<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as THREE from 'three'
import type { Seat, SeatStatus } from '../mock/seats'

const { t } = useI18n()

interface PreviewProps {
  seats: Seat[]
  selectedSeatIds: Set<string>
  stageLabel: string
  unavailableLabel: string
  rowLabel: string
  colLabel: string
  category?: string
}

const props = defineProps<PreviewProps>()
const emit = defineEmits<{
  'toggle-seat': [seat: Seat]
}>()

const containerRef = ref<HTMLElement | null>(null)
const minimapCanvasRef = ref<HTMLCanvasElement | null>(null)
const webglAvailable = ref(true)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0
let cameraAngle = 0
// orbitRadius is tracked via baseOrbitRadius * zoomScale now
let cameraHeight = 6
let pointerStartX = 0
let pointerStartY = 0
let pointerMoved = false
let dragging = false
let activePointerId: number | null = null
let zoomScale = 1.0
let lastClickTime = 0
let focusedSeatId: string | null = null

const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const clickableMeshes: THREE.Mesh[] = []
const materialCache = new Map<string, THREE.MeshStandardMaterial>()

const seatSpacingX = 0.72
const seatSpacingZ = 0.82

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

  const isMovie = props.category?.toLowerCase() === 'movie'

  // 环境光
  const ambientLight = new THREE.AmbientLight(isMovie ? 0x1a1a21 : 0x20202a, 1.0)
  scene.add(ambientLight)

  const stats = getSeatStats()
  const stageZ = -(stats.depth / 2) - 1.2

  if (isMovie) {
    // 银幕反射光：冷蓝光
    const screenReflectLight = new THREE.DirectionalLight(0x6f9ed0, 1.32)
    screenReflectLight.position.set(0, 4, stageZ)
    screenReflectLight.target.position.set(0, 0, 0)
    scene.add(screenReflectLight)
    scene.add(screenReflectLight.target)
  } else {
    // 舞台反射光：温暖的金黄光
    const stageReflectLight = new THREE.DirectionalLight(0xffdfb0, 1.16)
    stageReflectLight.position.set(0, 5, stageZ)
    stageReflectLight.target.position.set(0, 0, 0)
    scene.add(stageReflectLight)
    scene.add(stageReflectLight.target)
  }

  // 顶部微弱填充光
  const topFill = new THREE.DirectionalLight(isMovie ? 0x7796b4 : 0xa08d82, 0.54)
  topFill.position.set(0, 12, -2)
  scene.add(topFill)

  // 观众席后方微弱光线
  const audienceBackLight = new THREE.DirectionalLight(isMovie ? 0x554436 : 0x514333, 0.42)
  audienceBackLight.position.set(0, 12, stats.depth / 2 + 2.0)
  scene.add(audienceBackLight)
}

// Amphitheater slope: each row rises by this amount
const slopePerRow = 0.10

const createTheaterShell = () => {
  if (!scene) return
  const stats = getSeatStats()
  const isMovie = props.category?.toLowerCase() === 'movie'

  // 1. 基础地板
  const floorW = stats.width + 4.0
  const floorD = stats.depth + 4.0
  const floorMat = new THREE.MeshStandardMaterial({
    color: isMovie ? 0x0a0a0c : 0x0d0b0f,
    roughness: 0.7,
    metalness: 0.1
  })

  const floor = new THREE.Mesh(new THREE.PlaneGeometry(floorW, floorD), floorMat)
  floor.rotation.x = -Math.PI / 2
  floor.position.set(0, -0.01, 0)
  floor.receiveShadow = true
  scene.add(floor)

  // 2. 墙壁
  const wallH = 7.0
  const wallMat = new THREE.MeshStandardMaterial({
    color: isMovie ? 0x16161a : 0x1a1520,
    roughness: 0.95,
    metalness: 0.02
  })

  const backWallZ = stats.depth / 2 + 2.0
  const backWall = new THREE.Mesh(new THREE.PlaneGeometry(floorW, wallH), wallMat)
  backWall.position.set(0, wallH / 2, backWallZ)
  backWall.rotation.y = Math.PI
  scene.add(backWall)

  const leftWall = new THREE.Mesh(new THREE.PlaneGeometry(floorD, wallH), wallMat)
  leftWall.position.set(-floorW / 2, wallH / 2, 0)
  leftWall.rotation.y = Math.PI / 2
  scene.add(leftWall)

  const rightWall = leftWall.clone()
  rightWall.position.x = floorW / 2
  rightWall.rotation.y = -Math.PI / 2
  scene.add(rightWall)

  // 3. 侧墙灯带
  const stripW = 0.05
  const stripH = 4.0
  const stripColor = isMovie ? 0xc8955a : 0xd4a86a
  const stripMat = new THREE.MeshStandardMaterial({
    color: stripColor,
    emissive: stripColor,
    emissiveIntensity: 0.8,
    roughness: 0.5
  })

  const wallStripsCount = 3
  for (let i = 0; i < wallStripsCount; i++) {
    const wallStripZ = (i - (wallStripsCount - 1) / 2) * (floorD * 0.28)

    const lStrip = new THREE.Mesh(new THREE.BoxGeometry(stripW, stripH, 0.08), stripMat)
    lStrip.position.set(-floorW / 2 + 0.03, stripH / 2 + 0.8, wallStripZ)
    scene.add(lStrip)

    const rStrip = lStrip.clone()
    rStrip.position.x = floorW / 2 - 0.03
    scene.add(rStrip)
  }

  // 4. 台阶及边缘安全指示灯带
  const stepMat = new THREE.MeshStandardMaterial({
    color: isMovie ? 0x121215 : 0x14121a,
    roughness: 0.85,
    metalness: 0.05
  })
  const stepGlowMat = new THREE.MeshStandardMaterial({
    color: 0xc8955a,
    emissive: 0xc8955a,
    emissiveIntensity: 1.2
  })

  for (let r = 1; r <= stats.maxRow; r++) {
    const stepH = r * slopePerRow
    const stepZ = (r - (stats.maxRow + 1) / 2) * seatSpacingZ + 0.55
    const stepDepth = seatSpacingZ * 0.92

    const step = new THREE.Mesh(
      new THREE.BoxGeometry(floorW * 0.9, stepH, stepDepth),
      stepMat
    )
    step.position.set(0, stepH / 2, stepZ)
    step.receiveShadow = true
    scene.add(step)

    const stepLight = new THREE.Mesh(
      new THREE.BoxGeometry(floorW * 0.88, 0.015, 0.015),
      stepGlowMat
    )
    stepLight.position.set(0, stepH + 0.01, stepZ - stepDepth / 2 + 0.01)
    scene.add(stepLight)
  }

  // 5. 舞台/银幕区 - 根据 category 分支
  const stageZ = -(stats.depth / 2) - 1.2

  if (isMovie) {
    // ===== 电影：弯曲巨幕 + 放映光束 =====
    const screenWidth = Math.max(8.0, stats.width * 1.1)
    const screenHeight = 4.2
    const screenRadius = screenWidth * 1.5
    const thetaLength = Math.PI / 4.5

    // 黑色边框
    const frameGeom = new THREE.CylinderGeometry(
      screenRadius + 0.02, screenRadius + 0.02,
      screenHeight + 0.25, 64, 1, true,
      -thetaLength / 2 - Math.PI / 2, thetaLength
    )
    const frameMat = new THREE.MeshStandardMaterial({
      color: 0x050505, roughness: 0.95, metalness: 0.02, side: THREE.DoubleSide
    })
    const screenFrame = new THREE.Mesh(frameGeom, frameMat)
    screenFrame.position.set(0, 2.2, stageZ + screenRadius + 0.01)
    scene.add(screenFrame)

    // 巨幕面
    const screenGeom = new THREE.CylinderGeometry(
      screenRadius, screenRadius, screenHeight, 64, 1, true,
      -thetaLength / 2 - Math.PI / 2, thetaLength
    )
    const screenMat = new THREE.MeshStandardMaterial({
      color: 0x112233,
      emissive: 0x1b354f,
      emissiveIntensity: 1.5,
      roughness: 0.3,
      metalness: 0.1,
      side: THREE.DoubleSide
    })
    const screen = new THREE.Mesh(screenGeom, screenMat)
    screen.position.set(0, 2.2, stageZ + screenRadius)
    scene.add(screen)

    // 放映室小窗
    const boothGeom = new THREE.PlaneGeometry(0.8, 0.4)
    const boothMat = new THREE.MeshBasicMaterial({ color: 0xffffff })
    const boothWindow = new THREE.Mesh(boothGeom, boothMat)
    boothWindow.position.set(0, 4.5, backWallZ - 0.02)
    boothWindow.rotation.y = Math.PI
    scene.add(boothWindow)

    // 放映光束
    const beamLength = Math.abs(backWallZ - stageZ)
    const beamGeom = new THREE.CylinderGeometry(0.04, screenWidth * 0.4, beamLength, 32, 1, true)
    beamGeom.rotateX(Math.PI / 2)
    const beamMat = new THREE.MeshBasicMaterial({
      color: 0x77aaff, transparent: true, opacity: 0.08,
      side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending
    })
    const projectorBeam = new THREE.Mesh(beamGeom, beamMat)
    projectorBeam.position.set(0, (4.5 + 2.2) / 2, (backWallZ + stageZ) / 2)
    scene.add(projectorBeam)
  } else {
    // ===== 剧场：木质舞台 + 镜框拱门 + 幕布 + 追光灯 =====
    const stageWidth = Math.max(8.0, stats.width * 1.05)
    const stageDepth = 3.2
    const stageHeight = 0.45

    // 舞台地板（木质）
    const stageFloorMat = new THREE.MeshStandardMaterial({
      color: 0x4a3828,
      roughness: 0.65,
      metalness: 0.05
    })
    const stageFloor = new THREE.Mesh(
      new THREE.BoxGeometry(stageWidth, stageHeight, stageDepth),
      stageFloorMat
    )
    stageFloor.position.set(0, stageHeight / 2, stageZ + stageDepth / 2 - 0.2)
    stageFloor.receiveShadow = true
    scene.add(stageFloor)

    // 舞台前沿装饰条
    const edgeMat = new THREE.MeshStandardMaterial({
      color: 0xc8955a,
      emissive: 0xc8955a,
      emissiveIntensity: 0.6,
      roughness: 0.4
    })
    const stageEdge = new THREE.Mesh(
      new THREE.BoxGeometry(stageWidth, 0.04, 0.04),
      edgeMat
    )
    stageEdge.position.set(0, stageHeight + 0.02, stageZ + stageDepth - 0.22)
    scene.add(stageEdge)

    // 镜框拱门（Proscenium Arch）
    const archThickness = 0.35
    const archWidth = stageWidth + 0.6
    const archHeight = 5.5
    const archMat = new THREE.MeshStandardMaterial({
      color: 0x2a2030,
      roughness: 0.8,
      metalness: 0.15
    })

    // 左柱
    const pillarGeom = new THREE.BoxGeometry(archThickness, archHeight, archThickness * 1.5)
    const leftPillar = new THREE.Mesh(pillarGeom, archMat)
    leftPillar.position.set(-archWidth / 2, archHeight / 2, stageZ + stageDepth - 0.3)
    scene.add(leftPillar)

    // 右柱
    const rightPillar = leftPillar.clone()
    rightPillar.position.x = archWidth / 2
    scene.add(rightPillar)

    // 顶梁
    const topBeam = new THREE.Mesh(
      new THREE.BoxGeometry(archWidth + archThickness, archThickness, archThickness * 1.5),
      archMat
    )
    topBeam.position.set(0, archHeight, stageZ + stageDepth - 0.3)
    scene.add(topBeam)

    // 镜框金色装饰边
    const archEdgeMat = new THREE.MeshStandardMaterial({
      color: 0xd4a86a,
      emissive: 0x8a6030,
      emissiveIntensity: 0.4,
      roughness: 0.5,
      metalness: 0.3
    })
    const archEdgeLeft = new THREE.Mesh(
      new THREE.BoxGeometry(0.06, archHeight, 0.06),
      archEdgeMat
    )
    archEdgeLeft.position.set(-archWidth / 2 + archThickness / 2 + 0.04, archHeight / 2, stageZ + stageDepth - 0.1)
    scene.add(archEdgeLeft)
    const archEdgeRight = archEdgeLeft.clone()
    archEdgeRight.position.x = archWidth / 2 - archThickness / 2 - 0.04
    scene.add(archEdgeRight)

    // 幕布（深红色天鹅绒）
    const curtainMat = new THREE.MeshStandardMaterial({
      color: 0x6b1520,
      roughness: 0.92,
      metalness: 0.0,
      side: THREE.DoubleSide
    })
    // 左幕
    const curtainH = archHeight - 0.5
    const curtainW = 1.2
    const leftCurtain = new THREE.Mesh(
      new THREE.PlaneGeometry(curtainW, curtainH),
      curtainMat
    )
    leftCurtain.position.set(-archWidth / 2 + curtainW / 2 + archThickness / 2, curtainH / 2 + 0.3, stageZ + stageDepth - 0.25)
    scene.add(leftCurtain)

    const rightCurtain = leftCurtain.clone()
    rightCurtain.position.x = archWidth / 2 - curtainW / 2 - archThickness / 2
    scene.add(rightCurtain)

    // 舞台背景墙（深色）
    const backdropMat = new THREE.MeshStandardMaterial({
      color: 0x0c0a0e,
      roughness: 0.98,
      metalness: 0.0
    })
    const backdrop = new THREE.Mesh(
      new THREE.PlaneGeometry(stageWidth + 1, archHeight + 0.5),
      backdropMat
    )
    backdrop.position.set(0, archHeight / 2, stageZ - 0.5)
    scene.add(backdrop)

    // 舞台追光灯（两束暖色聚光从顶部打向舞台中心）
    const spotGlowMat = new THREE.MeshBasicMaterial({
      color: 0xffe8c0,
      transparent: true,
      opacity: 0.06,
      side: THREE.DoubleSide,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
    const spotLength = 5.5
    const spotGeom = new THREE.ConeGeometry(1.8, spotLength, 32, 1, true)

    // 左追光
    const leftSpot = new THREE.Mesh(spotGeom, spotGlowMat)
    leftSpot.position.set(-2.5, wallH - spotLength / 2 + 0.5, stageZ + stageDepth / 2)
    leftSpot.rotation.z = 0.15
    scene.add(leftSpot)

    // 右追光
    const rightSpot = new THREE.Mesh(spotGeom, spotGlowMat)
    rightSpot.position.set(2.5, wallH - spotLength / 2 + 0.5, stageZ + stageDepth / 2)
    rightSpot.rotation.z = -0.15
    scene.add(rightSpot)

    // 追光灯体
    const spotFixtureMat = new THREE.MeshStandardMaterial({
      color: 0x222222,
      emissive: 0xffe8c0,
      emissiveIntensity: 0.5,
      roughness: 0.6,
      metalness: 0.4
    })
    const leftFixture = new THREE.Mesh(
      new THREE.CylinderGeometry(0.12, 0.08, 0.25, 8),
      spotFixtureMat
    )
    leftFixture.position.set(-2.5, wallH + 0.4, stageZ + stageDepth / 2)
    scene.add(leftFixture)

    const rightFixture = leftFixture.clone()
    rightFixture.position.x = 2.5
    scene.add(rightFixture)
  }
}

const getSeatMaterialConfig = (status: SeatStatus, selected: boolean) => {
  if (selected) {
    return { color: 0xC8955A, roughness: 0.85, metalness: 0.05, emissive: 0x7A4A1A, emissiveIntensity: 0.5 }
  }

  switch (status) {
    case 'SOLD':
      // 已售座位：哑光炭灰，保持低调但不沉入背景
      return { color: 0x303035, roughness: 1.0, metalness: 0.05, emissive: 0x020202, emissiveIntensity: 0.04 }
    case 'LOCKED':
      // 锁定座位：深红褐色带有微弱警示性红色呼吸灯，容易跟已售区分
      return { color: 0x6a2a2a, roughness: 0.85, metalness: 0.1, emissive: 0x2a0808, emissiveIntensity: 0.65 }
    case 'DISABLED':
      return { color: 0x18181a, roughness: 1.0, metalness: 0, emissive: 0x010101, emissiveIntensity: 0.02 }
    case 'AVAILABLE':
    default:
      return { color: 0x8a7964, roughness: 0.75, metalness: 0.1, emissive: 0x1d1205, emissiveIntensity: 0.22 }
  }
}

const getSeatMaterial = (status: SeatStatus, selected: boolean, isBackrest: boolean) => {
  const config = getSeatMaterialConfig(status, selected)
  // 靠背比座垫更平滑一点点以产生明暗分界
  const roughness = isBackrest ? (status === 'SOLD' ? 1.0 : 0.6) : config.roughness
  const key = `${status}-${selected}-${isBackrest}`

  const cached = materialCache.get(key)
  if (cached) return cached

  const material = new THREE.MeshStandardMaterial({
    color: config.color,
    roughness: roughness,
    metalness: isBackrest ? 0.1 : config.metalness,
    emissive: config.emissive,
    emissiveIntensity: config.emissiveIntensity
  })

  if (status === 'DISABLED') {
    material.transparent = true
    material.opacity = 0.1
  }

  materialCache.set(key, material)
  return material
}

const createSeatObject = (seat: Seat) => {
  const selected = props.selectedSeatIds.has(seat.id)
  const group = new THREE.Group()

  const cushionMaterial = getSeatMaterial(seat.status, selected, false)
  const backrestMaterial = getSeatMaterial(seat.status, false, true) // 靠背不发光，仅座垫发光

  const cushion = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.12, 0.42), cushionMaterial)
  cushion.position.y = 0.06
  cushion.castShadow = true
  cushion.receiveShadow = true
  cushion.userData.seatId = seat.id
  cushion.userData.status = seat.status
  cushion.userData.label = `${props.rowLabel} ${seat.row} ${props.colLabel} ${seat.col}`
  group.add(cushion)

  const backrest = new THREE.Mesh(new THREE.BoxGeometry(0.40, 0.38, 0.08), backrestMaterial)
  backrest.rotation.x = 0.18
  backrest.position.set(0, 0.26, 0.20)
  backrest.castShadow = true
  backrest.receiveShadow = true
  backrest.userData.seatId = seat.id
  backrest.userData.status = seat.status
  backrest.userData.label = cushion.userData.label
  group.add(backrest)

  if (selected) {
    const seatLight = new THREE.PointLight(0xC8955A, 0.8, 1.2)
    seatLight.position.set(0, -0.3, 0)
    group.add(seatLight)
  }

  const stats = getSeatStats()
  const x = (seat.col - (stats.maxCol + 1) / 2) * seatSpacingX
  const z = (seat.row - (stats.maxRow + 1) / 2) * seatSpacingZ + 0.55
  // Amphitheater: seat sits on top of its row's step platform
  const y = seat.row * slopePerRow
  group.position.set(x, y, z)
  // Slight inward curve for realism
  const curveFactor = (x / Math.max(stats.width, 1))
  group.rotation.y = curveFactor * -0.22

  clickableMeshes.push(cushion, backrest)
  return group
}

const syncCamera = () => {
  if (!camera) return
  const stats = getSeatStats()
  const baseOrbitRadius = Math.max(10, stats.width * 0.82, stats.depth * 0.9)
  const currentOrbitRadius = baseOrbitRadius * zoomScale
  const isMovie = props.category?.toLowerCase() === 'movie'

  // 舞台/银幕中心位置
  const stageZ = -(stats.depth / 2) - 1.2
  const stageCenterY = isMovie ? 2.2 : 1.5

  // 如果当前聚焦在某个座位，在场景中查找该座位 mesh 以获取最新坐标
  let targetMesh: THREE.Object3D | null = null
  if (focusedSeatId && scene) {
    scene.traverse((obj) => {
      if (obj.userData && obj.userData.seatId === focusedSeatId && obj instanceof THREE.Mesh) {
        targetMesh = obj
      }
    })
  }

  if (targetMesh) {
    const seatWorldPos = new THREE.Vector3()
    ;(targetMesh as THREE.Object3D).getWorldPosition(seatWorldPos)

    // 双击聚焦：从座位后方俯视看向舞台/银幕方向（模拟观众视角）
    const focusPos = new THREE.Vector3(
      seatWorldPos.x,
      seatWorldPos.y + 1.8,
      seatWorldPos.z + 3.0
    )
    camera.position.lerp(focusPos, 0.08)
    // 看向舞台/银幕中心，而非座位本身
    const lookTarget = new THREE.Vector3(0, stageCenterY, stageZ)
    camera.lookAt(lookTarget)
  } else {
    // 默认视角
    cameraHeight = Math.max(6.8, stats.depth * 0.7)
    const targetPos = new THREE.Vector3(
      Math.sin(cameraAngle) * currentOrbitRadius,
      cameraHeight,
      Math.cos(cameraAngle) * currentOrbitRadius + stats.depth * 0.25
    )
    camera.position.lerp(targetPos, 0.1)
    // 看向舞台/银幕中心
    camera.lookAt(0, stageCenterY * 0.5, stageZ * 0.3)
  }
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
    // 动态模拟已选座垫的呼吸灯效
    const pulseIntensity = 0.4 + Math.sin(Date.now() * 0.005) * 0.25
    materialCache.forEach((material, key) => {
      if (key.includes('-true-')) {
        material.emissiveIntensity = pulseIntensity
      }
    })

    syncCamera()
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
    const now = Date.now()
    if (now - lastClickTime < 300) {
      // 双击的第二次点击，忽略选座触发，以防止选座状态被二次翻转
      lastClickTime = now
      return
    }
    lastClickTime = now
    pickSeat(event)
  }
}

const handleDoubleClick = (event: MouseEvent) => {
  if (!camera || clickableMeshes.length === 0) return
  updatePointerFromEvent(event as PointerEvent)
  raycaster.setFromCamera(pointer, camera)
  const hit = raycaster.intersectObjects(clickableMeshes, false)[0]

  if (hit) {
    const seatId = hit.object.userData.seatId as string | undefined
    if (seatId) {
      if (focusedSeatId === seatId) {
        focusedSeatId = null
      } else {
        focusedSeatId = seatId
      }
    }
  } else {
    focusedSeatId = null
  }
}

const handleWheel = (event: WheelEvent) => {
  event.preventDefault()
  // 通过调整缩放比率进行缩放，防止被帧循环重置
  zoomScale += event.deltaY * 0.001
  zoomScale = Math.max(0.4, Math.min(2.5, zoomScale))
  syncCamera()
}

const addCanvasListeners = () => {
  if (!renderer) return
  const canvas = renderer.domElement
  canvas.addEventListener('pointerdown', handlePointerDown)
  canvas.addEventListener('pointermove', handlePointerMove)
  canvas.addEventListener('pointerup', handlePointerUp)
  canvas.addEventListener('pointercancel', handlePointerUp)
  canvas.addEventListener('dblclick', handleDoubleClick)
  canvas.addEventListener('wheel', handleWheel, { passive: false })
}

const removeCanvasListeners = () => {
  if (!renderer) return
  const canvas = renderer.domElement
  canvas.removeEventListener('pointerdown', handlePointerDown)
  canvas.removeEventListener('pointermove', handlePointerMove)
  canvas.removeEventListener('pointerup', handlePointerUp)
  canvas.removeEventListener('pointercancel', handlePointerUp)
  canvas.removeEventListener('dblclick', handleDoubleClick)
  canvas.removeEventListener('wheel', handleWheel)
}

const initScene = async () => {
  await nextTick()
  if (!containerRef.value) return
  if (!supportsWebGL()) {
    webglAvailable.value = false
    return
  }

  const bgColor = 0x0e0c0a
  scene = new THREE.Scene()
  scene.fog = new THREE.Fog(bgColor, 30, 60)
  camera = new THREE.PerspectiveCamera(36, 1, 0.1, 100)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setClearColor(bgColor, 1)
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

const drawMinimap = () => {
  if (!minimapCanvasRef.value) return
  const ctx = minimapCanvasRef.value.getContext('2d')
  if (!ctx) return

  const width = minimapCanvasRef.value.width
  const height = minimapCanvasRef.value.height
  ctx.clearRect(0, 0, width, height)

  const stats = getSeatStats()
  const cellW = (width - 20) / stats.maxCol
  const cellH = (height - 30) / stats.maxRow
  const size = Math.min(cellW, cellH, 6)

  const offsetX = (width - (stats.maxCol * size)) / 2
  const offsetY = (height - (stats.maxRow * size)) / 2 + 10

  props.seats.forEach(seat => {
    if (seat.status === 'DISABLED') return
    const x = offsetX + (seat.col - 1) * size
    const y = offsetY + (seat.row - 1) * size

    if (props.selectedSeatIds.has(seat.id)) {
      ctx.fillStyle = '#C8955A'
    } else if (seat.status === 'AVAILABLE') {
      ctx.fillStyle = '#5a5045'
    } else {
      ctx.fillStyle = '#2a2520'
    }

    ctx.fillRect(x + 1, y + 1, size - 2, size - 2)
  })
}

watch(
  () => [props.seats, Array.from(props.selectedSeatIds).join('|')],
  () => {
    rebuildScene()
    drawMinimap()
  },
  { deep: true }
)

// Theme switching has been removed as the app is locked to dark mode.

onMounted(() => {
  void initScene()
  setTimeout(drawMinimap, 100)
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
    <div
      ref="containerRef"
      class="preview-surface"
      :class="{ 'is-fallback': !webglAvailable }"
      role="img"
      :aria-label="stageLabel"
    >
      <div class="canvas-hint">{{ t('seat.canvasHint') }}</div>
      <p v-if="!webglAvailable">{{ unavailableLabel }}</p>

      <div class="minimap-container" v-show="webglAvailable">
        <div class="minimap-title">{{ t('seat.minimapTitle') }}</div>
        <canvas ref="minimapCanvasRef" width="180" height="130"></canvas>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.seat-stage-preview {
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  color: var(--color-text-primary);
  min-height: 0;
}

.preview-surface {
  width: 100%;
  height: 100%;
  flex: 1;
  overflow: hidden;
  position: relative;

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

.canvas-hint {
  position: absolute;
  left: 16px;
  bottom: 16px;
  font-family: var(--font-family-sans);
  font-size: 11px;
  color: var(--color-text-ghost);
  pointer-events: none;
  z-index: 10;
}

.minimap-container {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 180px;
  height: 130px;
  background: rgba(17, 17, 17, 0.85);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  pointer-events: none;
  z-index: 10;
  overflow: hidden;

  .minimap-title {
    position: absolute;
    top: 6px;
    left: 8px;
    font-size: 10px;
    color: var(--color-text-ghost);
    font-family: var(--font-family-sans);
  }

  canvas {
    display: block;
    width: 100%;
    height: 100%;
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
