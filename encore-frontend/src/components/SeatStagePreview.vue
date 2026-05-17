<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import type { Seat, SeatStatus } from '../mock/seats'

interface PreviewProps {
  seats: Seat[]
  selectedSeatIds: Set<string>
  stageLabel: string
  unavailableLabel: string
  rowLabel: string
  colLabel: string
}

const props = defineProps<PreviewProps>()
const emit = defineEmits<{
  'toggle-seat': [seat: Seat]
}>()

const containerRef = ref<HTMLElement | null>(null)
const webglAvailable = ref(true)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0
let cameraAngle = 0
let orbitRadius = 8
let cameraHeight = 6
let pointerStartX = 0
let pointerStartY = 0
let pointerMoved = false
let dragging = false
let activePointerId: number | null = null

const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const clickableMeshes: THREE.Mesh[] = []
const materialCache = new Map<string, THREE.MeshStandardMaterial>()

const seatSpacingX = 0.52
const seatSpacingZ = 0.58

const supportsWebGL = () => {
  try {
    const canvas = document.createElement('canvas')
    return Boolean(
      window.WebGLRenderingContext &&
        (canvas.getContext('webgl2') || canvas.getContext('webgl'))
    )
  } catch {
    return false
  }
}

const getSeatStats = () => {
  const maxRow = Math.max(1, ...props.seats.map((seat) => seat.row))
  const maxCol = Math.max(1, ...props.seats.map((seat) => seat.col))
  return {
    maxRow,
    maxCol,
    width: Math.max(6, maxCol * seatSpacingX + 1.4),
    depth: Math.max(5, maxRow * seatSpacingZ + 2.4)
  }
}

const getMaterialKey = (seat: Seat, selected: boolean) => {
  if (selected) return 'selected'
  if (seat.status === 'AVAILABLE') return `available-${seat.section}`
  return seat.status.toLowerCase()
}

const getMaterialConfig = (seat: Seat, selected: boolean) => {
  if (selected) {
    return { color: 0xc8955a, emissive: 0x4a2a10, opacity: 1 }
  }

  const statusColor: Record<SeatStatus, number> = {
    AVAILABLE: seat.section === 'VIP' ? 0x3b2e22 : seat.section === 'A' ? 0x2a2927 : 0x22292b,
    LOCKED: 0x3d3a38,
    SOLD: 0x111111,
    DISABLED: 0x080808
  }

  return {
    color: statusColor[seat.status],
    emissive: seat.status === 'AVAILABLE' ? 0x090604 : 0x000000,
    opacity: seat.status === 'SOLD' ? 0.42 : seat.status === 'DISABLED' ? 0.1 : 1
  }
}

const getSeatMaterial = (seat: Seat, selected: boolean) => {
  const key = getMaterialKey(seat, selected)
  const cached = materialCache.get(key)
  if (cached) return cached

  const config = getMaterialConfig(seat, selected)
  const material = new THREE.MeshStandardMaterial({
    color: config.color,
    emissive: config.emissive,
    roughness: 0.72,
    metalness: selected ? 0.18 : 0.04,
    transparent: config.opacity < 1,
    opacity: config.opacity
  })
  materialCache.set(key, material)
  return material
}

const disposeSceneGeometries = () => {
  if (!scene) return
  const cachedMaterials = new Set(materialCache.values())
  scene.traverse((object) => {
    const mesh = object as THREE.Mesh
    if (mesh.geometry instanceof THREE.BufferGeometry) {
      mesh.geometry.dispose()
    }
    const meshMaterials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    meshMaterials.forEach((material) => {
      if (material instanceof THREE.Material && !cachedMaterials.has(material as THREE.MeshStandardMaterial)) {
        material.dispose()
      }
    })
  })
}

const createLights = () => {
  if (!scene) return

  const ambient = new THREE.HemisphereLight(0xf0ede8, 0x080808, 1.55)
  scene.add(ambient)

  const keyLight = new THREE.DirectionalLight(0xffd5a0, 2.4)
  keyLight.position.set(-3, 6, 5)
  keyLight.castShadow = true
  scene.add(keyLight)

  const rimLight = new THREE.DirectionalLight(0xc8955a, 1.2)
  rimLight.position.set(4, 3, -4)
  scene.add(rimLight)
}

const createTheaterShell = () => {
  if (!scene) return
  const stats = getSeatStats()

  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(stats.width + 1.2, stats.depth + 1.4),
    new THREE.MeshStandardMaterial({
      color: 0x0b0b0b,
      roughness: 0.9,
      metalness: 0
    })
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.z = 0.3
  floor.receiveShadow = true
  scene.add(floor)

  const stageZ = -(stats.depth / 2) + 0.25
  const stage = new THREE.Mesh(
    new THREE.BoxGeometry(Math.max(3.2, stats.width * 0.72), 0.18, 0.72),
    new THREE.MeshStandardMaterial({
      color: 0x241811,
      emissive: 0x2d1609,
      roughness: 0.58,
      metalness: 0.08
    })
  )
  stage.position.set(0, 0.09, stageZ)
  stage.castShadow = true
  stage.receiveShadow = true
  scene.add(stage)

  const proscenium = new THREE.Mesh(
    new THREE.BoxGeometry(Math.max(4.2, stats.width * 0.86), 0.08, 0.08),
    new THREE.MeshStandardMaterial({
      color: 0xc8955a,
      emissive: 0x321b08,
      roughness: 0.4,
      metalness: 0.25
    })
  )
  proscenium.position.set(0, 0.24, stageZ + 0.44)
  scene.add(proscenium)
}

const createSeatObject = (seat: Seat) => {
  const selected = props.selectedSeatIds.has(seat.id)
  const material = getSeatMaterial(seat, selected)
  const group = new THREE.Group()

  const base = new THREE.Mesh(new THREE.BoxGeometry(0.38, 0.12, 0.34), material)
  base.position.y = 0.08
  base.castShadow = true
  base.userData.seatId = seat.id
  base.userData.status = seat.status
  base.userData.label = `${props.rowLabel} ${seat.row} ${props.colLabel} ${seat.col}`
  group.add(base)

  const back = new THREE.Mesh(new THREE.BoxGeometry(0.38, 0.28, 0.08), material)
  back.position.set(0, 0.26, 0.17)
  back.castShadow = true
  back.userData.seatId = seat.id
  back.userData.status = seat.status
  back.userData.label = base.userData.label
  group.add(back)

  const stats = getSeatStats()
  const x = (seat.col - (stats.maxCol + 1) / 2) * seatSpacingX
  const z = (seat.row - (stats.maxRow + 1) / 2) * seatSpacingZ + 0.55
  group.position.set(x, selected ? 0.12 : 0, z)
  group.rotation.y = (x / Math.max(stats.width, 1)) * -0.16

  clickableMeshes.push(base, back)
  return group
}

const syncCamera = () => {
  if (!camera) return
  const stats = getSeatStats()
  orbitRadius = Math.max(7, stats.width * 0.82, stats.depth * 0.9)
  cameraHeight = Math.max(4.8, stats.depth * 0.55)
  camera.position.set(
    Math.sin(cameraAngle) * orbitRadius,
    cameraHeight,
    Math.cos(cameraAngle) * orbitRadius + stats.depth * 0.25
  )
  camera.lookAt(0, 0.2, 0.15)
}

const rebuildScene = () => {
  if (!scene) return
  disposeSceneGeometries()
  scene.clear()
  clickableMeshes.length = 0
  createLights()
  createTheaterShell()
  props.seats.forEach((seat) => {
    if (seat.status !== 'DISABLED') {
      scene?.add(createSeatObject(seat))
    }
  })
  syncCamera()
}

const resizeRenderer = () => {
  if (!renderer || !camera || !containerRef.value) return
  const { width, height } = containerRef.value.getBoundingClientRect()
  if (width <= 0 || height <= 0) return
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

const renderLoop = () => {
  if (renderer && scene && camera) {
    renderer.render(scene, camera)
  }
  animationFrame = requestAnimationFrame(renderLoop)
}

const updatePointerFromEvent = (event: PointerEvent) => {
  if (!renderer) return
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
}

const pickSeat = (event: PointerEvent) => {
  if (!camera || clickableMeshes.length === 0) return
  updatePointerFromEvent(event)
  raycaster.setFromCamera(pointer, camera)
  const hit = raycaster.intersectObjects(clickableMeshes, false)[0]
  const seatId = hit?.object.userData.seatId as string | undefined
  const seat = seatId ? props.seats.find((item) => item.id === seatId) : undefined
  if (seat && (seat.status === 'AVAILABLE' || props.selectedSeatIds.has(seat.id))) {
    emit('toggle-seat', seat)
  }
}

const handlePointerDown = (event: PointerEvent) => {
  if (!renderer) return
  dragging = true
  pointerMoved = false
  activePointerId = event.pointerId
  pointerStartX = event.clientX
  pointerStartY = event.clientY
  renderer.domElement.setPointerCapture(event.pointerId)
}

const handlePointerMove = (event: PointerEvent) => {
  if (!dragging || activePointerId !== event.pointerId) return
  const deltaX = event.clientX - pointerStartX
  const deltaY = event.clientY - pointerStartY
  if (Math.abs(deltaX) > 2 || Math.abs(deltaY) > 2) {
    pointerMoved = true
  }
  cameraAngle += deltaX * 0.005
  pointerStartX = event.clientX
  pointerStartY = event.clientY
  syncCamera()
}

const handlePointerUp = (event: PointerEvent) => {
  if (!renderer || activePointerId !== event.pointerId) return
  renderer.domElement.releasePointerCapture(event.pointerId)
  dragging = false
  activePointerId = null
  if (!pointerMoved) {
    pickSeat(event)
  }
}

const addCanvasListeners = () => {
  if (!renderer) return
  const canvas = renderer.domElement
  canvas.addEventListener('pointerdown', handlePointerDown)
  canvas.addEventListener('pointermove', handlePointerMove)
  canvas.addEventListener('pointerup', handlePointerUp)
  canvas.addEventListener('pointercancel', handlePointerUp)
}

const removeCanvasListeners = () => {
  if (!renderer) return
  const canvas = renderer.domElement
  canvas.removeEventListener('pointerdown', handlePointerDown)
  canvas.removeEventListener('pointermove', handlePointerMove)
  canvas.removeEventListener('pointerup', handlePointerUp)
  canvas.removeEventListener('pointercancel', handlePointerUp)
}

const initScene = async () => {
  await nextTick()
  if (!containerRef.value) return
  if (!supportsWebGL()) {
    webglAvailable.value = false
    return
  }

  scene = new THREE.Scene()
  scene.fog = new THREE.Fog(0x080808, 7, 18)
  camera = new THREE.PerspectiveCamera(36, 1, 0.1, 100)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setClearColor(0x080808, 1)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.domElement.className = 'preview-canvas'
  containerRef.value.appendChild(renderer.domElement)

  resizeObserver = new ResizeObserver(() => {
    resizeRenderer()
  })
  resizeObserver.observe(containerRef.value)

  addCanvasListeners()
  resizeRenderer()
  rebuildScene()
  renderLoop()
}

watch(
  () => [props.seats, Array.from(props.selectedSeatIds).join('|')],
  () => {
    rebuildScene()
  },
  { deep: true }
)

onMounted(() => {
  void initScene()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  removeCanvasListeners()
  resizeObserver?.disconnect()
  disposeSceneGeometries()
  materialCache.forEach((material) => material.dispose())
  renderer?.dispose()
})
</script>

<template>
  <section class="seat-stage-preview" :aria-label="stageLabel">
    <div class="preview-title">{{ stageLabel }}</div>
    <div
      ref="containerRef"
      class="preview-surface"
      :class="{ 'is-fallback': !webglAvailable }"
      role="img"
      :aria-label="stageLabel"
    >
      <p v-if="!webglAvailable">{{ unavailableLabel }}</p>
    </div>
  </section>
</template>

<style scoped lang="scss">
.seat-stage-preview {
  width: 100%;
  margin-bottom: var(--spacing-5);
  color: var(--color-text-primary);
}

.preview-title {
  margin-bottom: var(--spacing-2);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--color-text-secondary);
  text-transform: uppercase;
}

.preview-surface {
  width: 100%;
  height: clamp(300px, 34vh, 340px);
  min-height: 300px;
  overflow: hidden;
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border);
  background:
    linear-gradient(180deg, rgba(200, 149, 90, 0.1), transparent 28%),
    var(--color-bg-base);

  :deep(canvas) {
    display: block;
    width: 100%;
    height: 100%;
    cursor: grab;
    touch-action: none;

    &:active {
      cursor: grabbing;
    }
  }

  &.is-fallback {
    display: flex;
    min-height: 220px;
    align-items: center;
    justify-content: center;
    padding: var(--spacing-4);
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 14px;
    text-align: center;
  }
}

@media (max-width: 900px) {
  .seat-stage-preview {
    margin-bottom: var(--spacing-4);
  }

  .preview-surface {
    height: 220px;
    min-height: 220px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .preview-surface :deep(canvas) {
    scroll-behavior: auto;
  }
}
</style>
