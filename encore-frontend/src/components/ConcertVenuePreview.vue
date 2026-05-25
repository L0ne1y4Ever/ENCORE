<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ScheduleAreaResponse } from '../api/seat'

const props = defineProps<{
  areas: ScheduleAreaResponse[]
  selectedAreaId: string | null
}>()

const emit = defineEmits<{
  (e: 'select-area', area: ScheduleAreaResponse): void
}>()

const { t } = useI18n()

// Map area codes to beautifully curved SVG paths/attributes
const areaShapes = {
  VIP_A: {
    d: 'M280,135 Q400,120 520,135 L540,190 Q400,175 260,190 Z',
    labelX: 400,
    labelY: 162
  },
  INFIELD_A: {
    d: 'M260,205 Q400,190 540,205 L565,280 Q400,265 235,280 Z',
    labelX: 400,
    labelY: 242
  },
  INFIELD_B: {
    d: 'M235,295 Q400,280 565,295 L595,380 Q400,365 205,380 Z',
    labelX: 400,
    labelY: 337
  },
  STAND_1: {
    d: 'M80,120 Q165,130 250,140 Q222,267 195,395 Q122,372 50,350 Q65,235 80,120 Z',
    labelX: 142,
    labelY: 245
  },
  STAND_2: {
    d: 'M720,120 Q635,130 550,140 Q578,267 605,395 Q678,372 750,350 Q735,235 720,120 Z',
    labelX: 658,
    labelY: 245
  },
  STAND_3: {
    d: 'M140,410 Q400,395 660,410 L630,510 Q400,490 170,510 Z',
    labelX: 400,
    labelY: 460
  }
}

const mappedAreas = computed(() => {
  return props.areas.map(area => {
    const shape = areaShapes[area.code as keyof typeof areaShapes]
    return {
      ...area,
      shape
    }
  })
})

const handleAreaClick = (area: ScheduleAreaResponse) => {
  if (area.availableCount > 0) {
    emit('select-area', area)
  }
}

const getAreaGradient = (code: string) => {
  switch (code) {
    case 'VIP_A': return 'url(#grad-vip-a)'
    case 'INFIELD_A': return 'url(#grad-infield-a)'
    case 'INFIELD_B': return 'url(#grad-infield-b)'
    case 'STAND_1': return 'url(#grad-stand-1)'
    case 'STAND_2': return 'url(#grad-stand-2)'
    case 'STAND_3': return 'url(#grad-stand-3)'
    default: return 'rgba(255, 255, 255, 0.05)'
  }
}

const getAreaBorderColor = (area: ScheduleAreaResponse) => {
  if (area.color) return area.color
  switch (area.code) {
    case 'VIP_A': return '#c8955a'
    case 'INFIELD_A': return '#4a90e2'
    case 'INFIELD_B': return '#50e3c2'
    case 'STAND_1': return '#f5a623'
    case 'STAND_2': return '#b8e986'
    case 'STAND_3': return '#bd10e0'
    default: return '#8a8480'
  }
}

const getAreaPattern = (area: ScheduleAreaResponse) => {
  return area.isSeated ? 'url(#seats-pattern)' : 'url(#standing-pattern)'
}

const getAreaOpacity = (area: ScheduleAreaResponse) => {
  if (area.totalCount <= 0) return 0.12
  const ratio = area.availableCount / area.totalCount
  if (ratio === 0) return 0.08
  return 0.2 + ratio * 0.4 // Highly refined glass opacity
}
</script>

<template>
  <div class="concert-venue-preview">
    <!-- Premium Title & Stage Guide Header -->
    <div class="stage-guide">
      <div class="stage-light left"></div>
      <div class="stage-light right"></div>
      <div class="stage-board">
        <span class="stage-text">{{ t('seat.stage') || 'STAGE / 舞台' }}</span>
      </div>
    </div>

    <!-- Main Dynamic SVG Stadium Map -->
    <div class="svg-container">
      <svg viewBox="0 0 800 550" class="venue-svg" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <!-- Premium Glowing Filters -->
          <filter id="stage-glow" x="-25%" y="-25%" width="150%" height="150%">
            <feGaussianBlur stdDeviation="8" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>

          <filter id="active-glow" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="6" result="blur" />
            <feComponentTransfer in="blur" result="glow">
              <feFuncA type="linear" slope="0.8" />
            </feComponentTransfer>
            <feMerge>
              <feMergeNode in="glow" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>

          <filter id="screen-glow" x="-10%" y="-10%" width="120%" height="120%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>

          <!-- Seating pattern for seated stands -->
          <pattern id="seats-pattern" x="0" y="0" width="10" height="10" patternUnits="userSpaceOnUse">
            <circle cx="5" cy="5" r="1.2" fill="rgba(255, 255, 255, 0.15)" />
          </pattern>

          <!-- Crowd/Standing texture pattern -->
          <pattern id="standing-pattern" x="0" y="0" width="6" height="6" patternUnits="userSpaceOnUse">
            <circle cx="3" cy="3" r="0.8" fill="rgba(255, 255, 255, 0.08)" />
          </pattern>

          <!-- Spotlight gradient overlays (Hex + stop-opacity to fix cross-browser rendering bugs) -->
          <linearGradient id="spotlight-left-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#c8955a" stop-opacity="0.25" />
            <stop offset="50%" stop-color="#c8955a" stop-opacity="0.08" />
            <stop offset="100%" stop-color="#c8955a" stop-opacity="0" />
          </linearGradient>
          <linearGradient id="spotlight-right-grad" x1="100%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#c8955a" stop-opacity="0.25" />
            <stop offset="50%" stop-color="#c8955a" stop-opacity="0.08" />
            <stop offset="100%" stop-color="#c8955a" stop-opacity="0" />
          </linearGradient>
          <linearGradient id="spotlight-blue" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#4a90e2" stop-opacity="0.25" />
            <stop offset="60%" stop-color="#4a90e2" stop-opacity="0.08" />
            <stop offset="100%" stop-color="#4a90e2" stop-opacity="0" />
          </linearGradient>
          <linearGradient id="spotlight-magenta" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#bd10e0" stop-opacity="0.22" />
            <stop offset="60%" stop-color="#bd10e0" stop-opacity="0.06" />
            <stop offset="100%" stop-color="#bd10e0" stop-opacity="0" />
          </linearGradient>

          <!-- Screen backdrop gradient -->
          <linearGradient id="screen-grad" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#0a080c" />
            <stop offset="50%" stop-color="#181322" />
            <stop offset="100%" stop-color="#030205" />
          </linearGradient>

          <!-- Gorgeous glassmorphic gradients for each price area (Hex + stop-opacity to fix bugs) -->
          <linearGradient id="grad-vip-a" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#c8955a" stop-opacity="0.45" />
            <stop offset="100%" stop-color="#c8955a" stop-opacity="0.08" />
          </linearGradient>
          <linearGradient id="grad-infield-a" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#4a90e2" stop-opacity="0.45" />
            <stop offset="100%" stop-color="#4a90e2" stop-opacity="0.08" />
          </linearGradient>
          <linearGradient id="grad-infield-b" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#50e3c2" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#50e3c2" stop-opacity="0.06" />
          </linearGradient>
          <linearGradient id="grad-stand-1" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#f5a623" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#f5a623" stop-opacity="0.06" />
          </linearGradient>
          <linearGradient id="grad-stand-2" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#b8e986" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#b8e986" stop-opacity="0.06" />
          </linearGradient>
          <linearGradient id="grad-stand-3" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#bd10e0" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#bd10e0" stop-opacity="0.06" />
          </linearGradient>

          <!-- Stage flooring gradient -->
          <linearGradient id="stage-floor-grad" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#3e3224" />
            <stop offset="60%" stop-color="#201a14" />
            <stop offset="100%" stop-color="#0a0907" />
          </linearGradient>

          <!-- Digital LED screen matrix grid pattern -->
          <pattern id="screen-grid" width="6" height="6" patternUnits="userSpaceOnUse">
            <line x1="0" y1="0" x2="6" y2="0" stroke="rgba(255,255,255,0.03)" stroke-width="0.5" />
            <line x1="0" y1="0" x2="0" y2="6" stroke="rgba(255,255,255,0.03)" stroke-width="0.5" />
          </pattern>
        </defs>

        <!-- Spatial Blueprint Grid Lines (Adding high-fidelity architectural aura) -->
        <g opacity="0.1" pointer-events="none">
          <!-- Horizontal grid lines -->
          <line x1="50" y1="100" x2="750" y2="100" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="50" y1="200" x2="750" y2="200" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="50" y1="300" x2="750" y2="300" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="50" y1="400" x2="750" y2="400" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="50" y1="500" x2="750" y2="500" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />

          <!-- Vertical grid lines -->
          <line x1="100" y1="50" x2="100" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="200" y1="50" x2="200" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="300" y1="50" x2="300" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="400" y1="50" x2="400" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="500" y1="50" x2="500" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="600" y1="50" x2="600" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
          <line x1="700" y1="50" x2="700" y2="520" stroke="#c8955a" stroke-width="0.5" stroke-dasharray="2,8" />
        </g>

        <!-- Stadium Blueprint Outer Rings -->
        <ellipse cx="400" cy="275" rx="385" ry="260" fill="none" stroke="rgba(200, 149, 90, 0.04)" stroke-width="1.5" />
        <ellipse cx="400" cy="275" rx="378" ry="253" fill="none" stroke="rgba(200, 149, 90, 0.08)" stroke-width="1" stroke-dasharray="5,5" />
        <ellipse cx="400" cy="275" rx="392" ry="267" fill="none" stroke="rgba(255,255,255,0.015)" stroke-width="1" />

        <!-- Sweeping Concert Spotlights (Dynamic, Multi-colored Beams) -->
        <g class="spotlight-layer">
          <!-- Warm Gold Sweeping Beams -->
          <polygon points="150,20 180,20 380,480 200,480" fill="url(#spotlight-left-grad)" class="spotlight-beam spotlight-left" />
          <polygon points="650,20 620,20 420,480 600,480" fill="url(#spotlight-right-grad)" class="spotlight-beam spotlight-right" />

          <!-- Neon Blue/Magenta Center Sweeping Beams -->
          <polygon points="310,38 330,38 200,500 100,500" fill="url(#spotlight-blue)" class="spotlight-beam spotlight-mid-left" />
          <polygon points="490,38 510,38 700,500 600,500" fill="url(#spotlight-magenta)" class="spotlight-beam spotlight-mid-right" />
        </g>

        <!-- Dynamic concert stage -->
        <g class="stage-group">
          <!-- Backdrop screen screen -->
          <rect x="220" y="20" width="360" height="60" rx="8" fill="url(#screen-grad)" stroke="#c8955a" stroke-width="1.2" />

          <!-- Screen Matrix Grid Overlay -->
          <rect x="222" y="22" width="356" height="56" rx="6" fill="url(#screen-grid)" pointer-events="none" />

          <!-- Abstract Soundwaves on Screen -->
          <path d="M225,50 Q260,30 300,50 T380,50 T460,50 T540,50 T575,50" fill="none" stroke="rgba(200,149,90,0.18)" stroke-width="1.2" />
          <path d="M225,50 Q280,68 330,45 T430,45 T530,55 T575,50" fill="none" stroke="rgba(74, 144, 226, 0.12)" stroke-width="1" />

          <!-- Dynamic equalizer bars bouncing on screen -->
          <g fill="#c8955a" opacity="0.6" class="screen-eq" filter="url(#screen-glow)">
            <!-- Left Equalizer Bars -->
            <rect x="235" y="45" width="4" height="30" rx="1.5" class="eq-bar eq-1" />
            <rect x="243" y="35" width="4" height="40" rx="1.5" class="eq-bar eq-2" />
            <rect x="251" y="55" width="4" height="20" rx="1.5" class="eq-bar eq-3" />
            <rect x="259" y="30" width="4" height="45" rx="1.5" class="eq-bar eq-4" />
            <rect x="267" y="48" width="4" height="27" rx="1.5" class="eq-bar eq-5" />
            <rect x="275" y="60" width="4" height="15" rx="1.5" class="eq-bar eq-6" />

            <!-- Right Equalizer Bars -->
            <rect x="521" y="60" width="4" height="15" rx="1.5" class="eq-bar eq-6" />
            <rect x="529" y="48" width="4" height="27" rx="1.5" class="eq-bar eq-5" />
            <rect x="537" y="30" width="4" height="45" rx="1.5" class="eq-bar eq-4" />
            <rect x="545" y="55" width="4" height="20" rx="1.5" class="eq-bar eq-3" />
            <rect x="553" y="35" width="4" height="40" rx="1.5" class="eq-bar eq-2" />
            <rect x="561" y="45" width="4" height="30" rx="1.5" class="eq-bar eq-1" />
          </g>

          <!-- Screen Center Premium Text -->
          <text x="400" y="54" class="stage-inner-text" text-anchor="middle" filter="url(#stage-glow)">ENCORE LIVE</text>

          <!-- Stage speakers / Line Array Towers (Highly Detailed Stacked Cabinet) -->
          <!-- Left Stack -->
          <g fill="#121216" stroke="rgba(255,255,255,0.12)" stroke-width="0.8">
            <rect x="180" y="30" width="18" height="14" rx="2" />
            <circle cx="189" cy="37" r="3" fill="none" stroke="#c8955a" stroke-width="0.8" />
            <rect x="180" y="46" width="18" height="14" rx="2" />
            <circle cx="189" cy="53" r="3" fill="none" stroke="#c8955a" stroke-width="0.8" />
            <rect x="180" y="62" width="18" height="14" rx="2" />
            <circle cx="189" cy="69" r="3" fill="none" stroke="#c8955a" stroke-width="0.8" />
          </g>
          <!-- Right Stack -->
          <g fill="#121216" stroke="rgba(255,255,255,0.12)" stroke-width="0.8">
            <rect x="602" y="30" width="18" height="14" rx="2" />
            <circle cx="611" cy="37" r="3" fill="none" stroke="#c8955a" stroke-width="0.8" />
            <rect x="602" y="46" width="18" height="14" rx="2" />
            <circle cx="611" cy="53" r="3" fill="none" stroke="#c8955a" stroke-width="0.8" />
            <rect x="602" y="62" width="18" height="14" rx="2" />
            <circle cx="611" cy="69" r="3" fill="none" stroke="#c8955a" stroke-width="0.8" />
          </g>

          <!-- Stage flooring (3D angled deck) -->
          <polygon points="200,80 600,80 620,112 180,112" fill="url(#stage-floor-grad)" stroke="#c8955a" stroke-width="1.5" />

          <!-- Stage Edge LED Lightstrip -->
          <polygon points="180,112 620,112 623,116 177,116" fill="url(#stage-floor-grad)" stroke="#c8955a" stroke-width="1" />
          <line x1="180" y1="112" x2="620" y2="112" stroke="#ffffff" stroke-width="1.5" opacity="0.6" filter="url(#stage-glow)" />

          <!-- Stage flooring texture grid lines -->
          <path d="M250,80 L235,112 M300,80 L295,112 M350,80 L350,112 M400,80 L400,112 M450,80 L450,112 M500,80 L505,112 M550,80 L565,112" stroke="rgba(200,149,90,0.15)" stroke-width="0.8" />

          <!-- Curved lighting truss frame -->
          <path d="M190,80 Q400,20 610,80" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="3" />
          <path d="M190,83 Q400,23 610,83" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
          <path d="M190,80 L190,83 M250,51 L250,54 M310,38 L310,41 M400,32 L400,35 M490,38 L490,41 M550,51 L550,54 M610,80 L610,83" stroke="rgba(255,255,255,0.2)" stroke-width="1" />

          <!-- Truss Spotlight heads -->
          <g fill="#c8955a" filter="url(#stage-glow)" opacity="0.9">
            <polygon points="248,51 252,51 253,56 247,56" />
            <polygon points="308,38 312,38 313,43 307,43" />
            <polygon points="398,32 402,32 403,37 397,37" />
            <polygon points="488,38 492,38 493,43 487,43" />
            <polygon points="548,51 552,51 553,56 547,56" />
          </g>

          <text x="400" y="103" class="stage-inner-brand-text" text-anchor="middle">ENCORE ARENA</text>
        </g>

        <!-- Interactive Stadium Areas -->
        <g v-for="area in mappedAreas" :key="area.id" class="area-group" :class="{
          'is-active': selectedAreaId === area.id,
          'is-soldout': area.availableCount === 0
        }">
          <template v-if="area.shape">
            <!-- 1st layer: Elegant Translucent Gradient Glass Fill -->
            <path
              :d="area.shape.d"
              class="area-path-bg"
              :fill="getAreaGradient(area.code)"
              :style="{ fillOpacity: getAreaOpacity(area) }"
            />

            <!-- 2nd layer: Realistic Seating/Standing Pattern Texture overlay -->
            <path
              :d="area.shape.d"
              class="area-path-texture"
              :fill="getAreaPattern(area)"
              pointer-events="none"
              style="mix-blend-mode: overlay; opacity: 0.85;"
            />

            <!-- 3rd layer: Interactive Neon glowing border stroke -->
            <path
              :d="area.shape.d"
              class="area-path-border"
              fill="rgba(0,0,0,0)"
              :stroke="getAreaBorderColor(area)"
              :stroke-width="selectedAreaId === area.id ? 2.5 : 1"
              :filter="selectedAreaId === area.id ? 'url(#active-glow)' : ''"
              @click="handleAreaClick(area)"
            />

            <!-- 4th layer: Exquisite Floating Glass Tag Badge for Details -->
            <g class="text-group" @click="handleAreaClick(area)">
              <!-- Glass Card BG -->
              <rect
                :x="area.shape.labelX - 55"
                :y="area.shape.labelY - 26"
                width="110"
                height="52"
                rx="8"
                class="label-card"
                fill="rgba(10, 10, 12, 0.85)"
                :stroke="selectedAreaId === area.id ? getAreaBorderColor(area) : 'rgba(255,255,255,0.08)'"
                :stroke-width="selectedAreaId === area.id ? 1.8 : 1"
              />

              <!-- Floating Area Name -->
              <text
                :x="area.shape.labelX"
                :y="area.shape.labelY - 10"
                class="area-name-text"
                text-anchor="middle"
              >
                {{ area.name }}
              </text>

              <!-- Floating Area Price -->
              <text
                :x="area.shape.labelX"
                :y="area.shape.labelY + 5"
                class="area-price-text"
                text-anchor="middle"
                :fill="getAreaBorderColor(area)"
              >
                ￥{{ area.price }}
              </text>

              <!-- Floating Area Inventory Stock Badge -->
              <text
                :x="area.shape.labelX"
                :y="area.shape.labelY + 18"
                class="area-stock-text"
                text-anchor="middle"
              >
                {{ area.availableCount > 0 ? `${t('seat.available') || '可用'} ${area.availableCount}` : 'SOLD OUT' }}
              </text>
            </g>
          </template>
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped lang="scss">
.concert-venue-preview {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  background-image:
    radial-gradient(circle at 50% 15%, rgba(200, 149, 90, 0.15) 0%, rgba(8, 8, 8, 0) 65%),
    linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
  background-size: 100% 100%, 24px 24px, 24px 24px;
  background-position: center top;
  background-color: var(--color-bg-base);
  padding: var(--spacing-5) var(--spacing-4);
  overflow-y: auto;
  min-height: 580px;
}

.stage-guide {
  width: 100%;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: var(--spacing-4);
  position: relative;
}

.stage-board {
  width: 100%;
  padding: 10px;
  background: linear-gradient(90deg, rgba(200, 149, 90, 0) 0%, rgba(200, 149, 90, 0.15) 50%, rgba(200, 149, 90, 0) 100%);
  border-bottom: 1.5px solid var(--color-accent);
  text-align: center;
  border-radius: var(--radius-sm);
  box-shadow: 0 4px 20px rgba(200, 149, 90, 0.05);
}

.stage-text {
  font-family: var(--font-family-display);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-accent);
  letter-spacing: 0.4em;
  text-transform: uppercase;
  text-shadow: 0 0 10px rgba(200, 149, 90, 0.4);
}

.stage-light {
  position: absolute;
  top: 100%;
  width: 1.5px;
  height: 250px;
  background: linear-gradient(180deg, rgba(200, 149, 90, 0.25) 0%, rgba(200, 149, 90, 0) 100%);
  transform-origin: top center;
  pointer-events: none;
  z-index: 1;

  &.left {
    left: 20%;
    transform: rotate(-18deg);
    animation: swingLeft 7s infinite ease-in-out alternate;
  }

  &.right {
    right: 20%;
    transform: rotate(18deg);
    animation: swingRight 7s infinite ease-in-out alternate;
  }
}

.svg-container {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  filter: drop-shadow(0 16px 48px rgba(0,0,0,0.7));
}

.venue-svg {
  width: 100%;
  height: auto;
  display: block;
}

.stage-inner-text {
  font-family: var(--font-family-display);
  font-size: 13px;
  font-weight: 800;
  fill: var(--color-accent);
  letter-spacing: 0.25em;
  pointer-events: none;
}

.stage-inner-brand-text {
  font-family: var(--font-family-display);
  font-size: 10px;
  font-weight: 700;
  fill: #736557;
  letter-spacing: 0.2em;
  pointer-events: none;
}

/* Spotlights oscillation keyframes */
@keyframes oscLeft {
  0% { transform: rotate(-8deg); }
  100% { transform: rotate(4deg); }
}
@keyframes oscRight {
  0% { transform: rotate(8deg); }
  100% { transform: rotate(-4deg); }
}
@keyframes oscMidLeft {
  0% { transform: rotate(-5deg); }
  100% { transform: rotate(10deg); }
}
@keyframes oscMidRight {
  0% { transform: rotate(5deg); }
  100% { transform: rotate(-10deg); }
}

.spotlight-beam {
  mix-blend-mode: screen;
  pointer-events: none;
  opacity: 0.85;

  &.spotlight-left {
    transform-origin: 165px 20px;
    animation: oscLeft 11s infinite ease-in-out alternate;
  }

  &.spotlight-right {
    transform-origin: 635px 20px;
    animation: oscRight 11s infinite ease-in-out alternate;
  }

  &.spotlight-mid-left {
    transform-origin: 320px 38px;
    animation: oscMidLeft 9s infinite ease-in-out alternate;
  }

  &.spotlight-mid-right {
    transform-origin: 500px 38px;
    animation: oscMidRight 9s infinite ease-in-out alternate;
  }
}

/* Equalizer bounce keyframe */
@keyframes eqBounce {
  0% { transform: scaleY(0.3); }
  50% { transform: scaleY(1); }
  100% { transform: scaleY(0.3); }
}

.screen-eq {
  .eq-bar {
    transform-origin: 50% 75px;
    animation: eqBounce 1.2s infinite ease-in-out;

    &.eq-1 { animation-delay: 0.1s; animation-duration: 0.8s; }
    &.eq-2 { animation-delay: 0.3s; animation-duration: 1.1s; }
    &.eq-3 { animation-delay: 0.5s; animation-duration: 0.7s; }
    &.eq-4 { animation-delay: 0.2s; animation-duration: 1.3s; }
    &.eq-5 { animation-delay: 0.4s; animation-duration: 0.9s; }
    &.eq-6 { animation-delay: 0.6s; animation-duration: 1.0s; }
  }
}

/* Interactivity and beautiful glass highlights */
.area-path-border {
  cursor: pointer;
  stroke-dasharray: 2000;
  stroke-dashoffset: 0;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  opacity: 0.55;

  &:hover {
    stroke-width: 2.2px !important;
    opacity: 1 !important;
    filter: drop-shadow(0 0 8px currentColor);
  }
}

.area-path-bg {
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.area-group {
  /* Hover effects covering the entire SVG area group */
  &:hover {
    .area-path-bg {
      fill-opacity: 0.65 !important;
    }
    .area-path-border {
      stroke-width: 2.5px !important;
      opacity: 1 !important;
      filter: drop-shadow(0 0 10px currentColor);
    }
    .label-card {
      fill: rgba(16, 16, 20, 0.95) !important;
      stroke-width: 1.8px !important;
      transform: scale(1.05) translateY(-2px);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
    }
    .area-name-text {
      fill: #ffffff !important;
      font-weight: 700;
    }
    .area-price-text {
      fill: #ffffff !important;
    }
  }

  &.is-active {
    .area-path-bg {
      fill-opacity: 0.75 !important;
    }
    .area-path-border {
      stroke-width: 2.8px !important;
      opacity: 1 !important;
    }
    .label-card {
      fill: rgba(14, 14, 18, 0.98) !important;
      stroke-width: 2px !important;
      transform: scale(1.05) translateY(-2px);
      box-shadow: 0 8px 28px rgba(0, 0, 0, 0.7);
    }
    .area-name-text {
      fill: #ffffff !important;
      font-weight: 800;
    }
    .area-price-text {
      fill: #ffffff !important;
    }
  }

  &.is-soldout {
    cursor: not-allowed;

    .area-path-bg {
      fill: #16161a !important;
      fill-opacity: 0.12 !important;
    }

    .area-path-border {
      stroke: rgba(255,255,255,0.05) !important;
      stroke-width: 1px !important;
      cursor: not-allowed;
    }

    .text-group {
      cursor: not-allowed;
    }

    .label-card {
      fill: rgba(8, 8, 10, 0.5) !important;
      stroke: rgba(255,255,255,0.02) !important;
      box-shadow: none !important;
      transform: none !important;
    }

    .area-name-text {
      fill: var(--color-text-ghost) !important;
    }

    .area-price-text {
      fill: var(--color-text-ghost) !important;
    }

    .area-stock-text {
      fill: var(--color-error) !important;
      font-weight: 600;
      opacity: 0.85;
    }
  }
}

.text-group {
  cursor: pointer;
}

.label-card {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  transform-origin: center center;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.area-name-text {
  font-family: var(--font-family-cjk);
  font-size: 11px;
  font-weight: 600;
  fill: var(--color-text-secondary);
  pointer-events: none;
  transition: fill 0.3s ease;
  letter-spacing: 0.05em;
}

.area-price-text {
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 700;
  pointer-events: none;
  transition: fill 0.3s ease;
}

.area-stock-text {
  font-family: var(--font-family-sans);
  font-size: 9px;
  fill: #8a8480;
  pointer-events: none;
  transition: fill 0.3s ease;
  letter-spacing: 0.02em;
}

@keyframes swingLeft {
  0% { transform: rotate(-22deg); }
  100% { transform: rotate(-10deg); }
}

@keyframes swingRight {
  0% { transform: rotate(10deg); }
  100% { transform: rotate(22deg); }
}
</style>
