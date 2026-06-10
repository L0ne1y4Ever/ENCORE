<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrderDetail } from '../../api/order'
import type { Order, TicketItem } from '../../mock/orders'
import { useI18n } from 'vue-i18n'
import { Tickets } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const orderId = route.params.id as string

const order = ref<Order | null>(null)
const loading = ref(true)

onMounted(async () => {
  order.value = (await getOrderDetail(orderId)) || null

  // 添加一点延迟体现“票面展开”的仪式感
  setTimeout(() => {
    loading.value = false
  }, 400)
})

const formatDate = (dateStr: string) => {
  const d = new Date(dateStr)
  const isZh = locale.value === 'zh'
  const date = isZh
    ? `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
    : d.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })
  const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  return { date, time }
}

const areaDisplay = (ticket: TicketItem) => {
  return ticket.areaName || ticket.areaType || (ticket.seatId ? t('ticket.seatGeneral') : t('ticket.freeArea'))
}

const seatDisplay = (ticket: TicketItem) => {
  if (!ticket.seatId) return t('ticket.unassigned')
  if (ticket.rowNo != null && ticket.colNo != null) {
    return t('seat.info', { row: ticket.rowNo, col: ticket.colNo })
  }
  return ticket.seatLabel || ticket.seatId
}

const ticketStatusKey = (ticket: TicketItem) => {
  if (order.value?.status === 'PENDING_REFUND' && ticket.status === 'UNUSED') return 'pendingRefund'
  const status = (ticket.status || 'UNUSED').toLowerCase()
  if (status === 'checked_in') return 'checkedIn'
  if (status === 'pending_refund') return 'pendingRefund'
  if (status === 'void') return 'void'
  if (status === 'reserved') return 'reserved'
  return 'unused'
}

const ticketStatusLabel = (ticket: TicketItem) => {
  return t(`ticket.status.${ticketStatusKey(ticket)}`)
}

const ticketSubStatusLabel = (ticket: TicketItem) => {
  const status = (ticket.status || 'UNUSED').toLowerCase()
  if (status === 'checked_in') return locale.value === 'zh' ? '已核销' : 'Checked In'
  if (status === 'pending_refund') return locale.value === 'zh' ? '退票审核中' : 'Refund Review'
  if (status === 'void') return locale.value === 'zh' ? '已作废' : 'Void'
  if (status === 'reserved') return locale.value === 'zh' ? '待支付' : 'Reserved'
  return t('ticket.unused')
}

const ticketStateClass = (ticket: TicketItem) => {
  const key = ticketStatusKey(ticket)
  return {
    'ticket-card--inactive': key === 'checkedIn' || key === 'void' || key === 'pendingRefund',
    'ticket-card--reserved': key === 'reserved'
  }
}

const hashUnit = (value: string) => {
  let h = 0
  for (let i = 0; i < value.length; i++) {
    h = (h << 5) - h + value.charCodeAt(i)
    h |= 0
  }
  const x = Math.sin(h) * 10000
  return x - Math.floor(x)
}

const barcodeBars = (ticketCode: string) => {
  const bars: { width: string; height: string; transform: string; color: string }[] = []
  const totalCount = 42 // 稀疏线条，与图中一致
  
  const colors = [
    '#ff4b5c', // 玫瑰红
    '#ff6b6b',
    '#d64576',
    '#b04df0', // 魅惑紫
    '#7c4dff',
    '#00d2ff', // 天空蓝
    '#00f2fe',
    '#4facfe',
    '#38ef7d', // 翡翠绿
    '#11998e'
  ]
  
  for (let index = 0; index < totalCount; index++) {
    const seed1 = hashUnit(`${ticketCode}:w:${index}`)
    const seed2 = hashUnit(`${ticketCode}:h:${index}`)
    const seed3 = hashUnit(`${ticketCode}:t:${index}`)
    const seedColor = hashUnit(`${ticketCode}:c:${index}`)
    
    // 1. 模拟真实条形码的宽窄分布：细线偏多，粗线较少
    let width = '1.2px'
    if (seed1 < 0.55) {
      width = '1.2px'
    } else if (seed1 < 0.85) {
      width = '2.2px'
    } else if (seed1 < 0.94) {
      width = '3.8px'
    } else {
      width = '6px'
    }
    
    // 2. 高度起伏不过于夸张，限制在 50% 到 80% 之间
    const heightVal = 50 + Math.round(seed2 * 30)
    const height = `${heightVal}%`
    
    // 3. 垂直错落限制在微调级别（-8px 到 +8px），每一根线微调高低
    const offsetVal = Math.round((seed3 * 16) - 8)
    const transform = `translateY(${offsetVal}px)`
    
    // 4. 色彩不再从左到右渐变，而是完全随机跳变
    const colorIndex = Math.floor(seedColor * colors.length)
    const color = colors[colorIndex]
    
    bars.push({
      width,
      height,
      transform,
      color
    })
  }
  return bars
}
</script>

<template>
  <div class="ticket-page">
    <transition name="ticket-reveal">
      <div class="ticket-container" v-if="!loading && order">


        <div v-if="(order.tickets || []).length > 1" class="ticket-nav">
          <a
            v-for="(ticket, index) in order.tickets || []"
            :key="ticket.id"
            :href="`#ticket-${ticket.id}`"
          >
            <Tickets />
            <span>{{ t('ticket.ticketNumber', { number: index + 1 }) }}</span>
          </a>
        </div>

        <div
          class="ticket-wrapper"
          v-for="ticket in order.tickets || []"
          :key="ticket.id"
          :id="`ticket-${ticket.id}`"
        >
          <div class="ticket-card" :class="[{ 'vip-ticket': ticket.areaType === 'VIP' }, ticketStateClass(ticket)]">
            <!-- 票头部 -->
            <div class="t-header">
              <div class="back-btn" @click="router.push({ path: '/profile', query: { tab: 'tickets' } })">
                ← {{ t('common.back') || '返回' }}
              </div>
              <div class="brand">ENCORE</div>
            </div>

            <!-- 剧目与时间 -->
            <div class="t-body">
              <div class="date">{{ formatDate(order.startTime || order.createdAt).date }}</div>
              <h1 class="title">{{ order.showTitle || order.scheduleId }}</h1>
              <div class="theater-info">
                <span>{{ order.theaterName || '-' }}</span>
                <span>{{ formatDate(order.startTime || order.createdAt).time }}</span>
              </div>
            </div>

            <!-- 分界打孔线 -->
            <div class="perforation-wrap">
              <div class="notch notch-left"></div>
              <div class="perforation-line"></div>
              <div class="notch notch-right"></div>
            </div>

            <!-- 座位区 -->
            <div class="t-seat">
              <div class="s-label">{{ t('ticket.seat') }}</div>

              <div class="s-details-zoned">
                <div class="zoned-main">
                  <div class="sm">{{ t('ticket.section') }}</div>
                  <div class="lg-name">{{ areaDisplay(ticket) }}</div>
                </div>
                <div class="zoned-info">
                  <div class="sm">{{ t('ticket.seatInfo') }}</div>
                  <div class="lg-label">{{ seatDisplay(ticket) }}</div>
                  <div class="holder-line" v-if="ticket.holderDisplayName">
                    {{ t('ticket.holder') }} · {{ ticket.holderDisplayName }}
                  </div>
                  <div class="instructions" v-if="!ticket.seatId">{{ t('ticket.standingInstructions') }}</div>
                </div>
              </div>
            </div>

            <!-- 二维码区 -->
            <div class="t-barcode">
              <div class="barcode-container">
                <div class="bars">
                  <span
                    v-for="(bar, index) in barcodeBars(ticket.ticketCode)"
                    :key="`${ticket.id}-bar-${index}`"
                    :style="{
                      width: bar.width,
                      height: bar.height,
                      transform: bar.transform,
                      backgroundColor: bar.color,
                      position: 'relative'
                    }"
                  />
                </div>
              </div>
              <div class="code-str">{{ ticket.ticketCode }}</div>
            </div>

            <!-- 状态区 -->
            <div class="t-footer">
              <div class="status">{{ ticketStatusLabel(ticket) }}</div>
              <div class="tag" :class="`tag--${ticketStatusKey(ticket)}`">
                <span class="dot"></span> {{ ticketSubStatusLabel(ticket) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.ticket-page {
  --ticket-red: #e50914;
  --ticket-red-soft: #ff6570;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 80px);
  padding: var(--spacing-6);
  position: relative;
  isolation: isolate;
  background: transparent;
  overflow: hidden;

  &::before {
    content: none;
  }
}

.ticket-container {
  width: 100%;
  max-width: 680px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-5);
}

.ticket-toolbar,
.ticket-nav {
  width: min(100%, 520px);
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  justify-content: center;
}

.ticket-toolbar button,
.ticket-nav a {
  min-height: 44px;
  border: 1px solid rgba(255, 255, 255, 0.11);
  border-radius: 10px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.09), rgba(255, 255, 255, 0.03)),
    rgba(15, 15, 18, 0.74);
  color: var(--color-text-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 16px 34px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(18px) saturate(1.08);
  -webkit-backdrop-filter: blur(18px) saturate(1.08);
  transition: border-color 160ms ease, color 160ms ease, background-color 160ms ease, transform 160ms ease;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover {
    border-color: rgba(255, 255, 255, 0.24);
    color: #ffffff;
    background: rgba(255, 255, 255, 0.1);
    transform: translateY(-1px);
  }
}

/* 票面展开动画 */
.ticket-reveal-enter-active {
  transition: opacity 380ms ease, transform 380ms cubic-bezier(0.16, 1, 0.3, 1);
}
.ticket-reveal-enter-from {
  opacity: 0;
  transform: translateY(40px) scale(0.98);
}

.ticket-wrapper {
  width: min(100%, 570px);
  filter: drop-shadow(0 28px 52px rgba(0,0,0,0.58));
  perspective: 1000px;
  scroll-margin-top: 96px;
}

.ticket-card {
  position: relative;
  background-color: #1a1a1f;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.03);
  background-image:
    radial-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 0),
    radial-gradient(rgba(255, 255, 255, 0.01) 1px, transparent 0),
    linear-gradient(180deg, #18181d 0%, #0e0e11 100%);
  background-size: 8px 8px, 4px 4px, 100% 100%;
  background-position: 0 0, 4px 4px, 0 0;
  border: none;
  --ticket-accent: #ff4d58; /* 玫瑰红/珊瑚粉色调，代替原先的黄色金色 */

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    opacity: 0.8;
    background:
      radial-gradient(circle at 18% 8%, rgba(255, 255, 255, 0.03), transparent 18%),
      linear-gradient(180deg, rgba(255, 255, 255, 0.01), transparent 20%),
      linear-gradient(90deg, rgba(255, 255, 255, 0.012), transparent 32%);
    pointer-events: none;
    z-index: 1;
  }

  &.vip-ticket {
    border: 1px solid rgba(255, 255, 255, 0.15);
    background-image:
      radial-gradient(circle at 0% 0%, rgba(255, 255, 255, 0.05), transparent 40%),
      linear-gradient(180deg, #1c1c24 0%, #0d0d10 100%);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 0 30px rgba(255, 255, 255, 0.05),
      0 30px 70px rgba(0, 0, 0, 0.6);

    .t-header .brand {
      background: linear-gradient(135deg, #ffffff 0%, #a1a1aa 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      text-shadow: 0 0 20px rgba(255, 255, 255, 0.15);
    }
  }

  &.ticket-card--inactive {
    filter: grayscale(0.8) brightness(0.6);
    opacity: 0.55;

    .barcode-container,
    .code-str {
      opacity: 0.3;
    }
  }

  &.ticket-card--reserved {
    .t-footer {
      background: rgba(255, 184, 77, 0.08);
      border-top-color: rgba(255, 184, 77, 0.22);
      color: #ffd37d;
    }
  }

  > * {
    position: relative;
    z-index: 3;
  }

  .t-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 24px 28px 12px;

    .back-btn {
      font-size: 14px;
      font-family: var(--font-family-sans);
      color: var(--ticket-accent);
      font-weight: 700;
      cursor: pointer;
      transition: opacity 0.2s ease;

      &:hover {
        opacity: 0.8;
      }
    }

    .brand {
      font-family: var(--font-family-display);
      font-weight: 900;
      letter-spacing: 0.05em;
      font-size: 20px;
      color: #ffffff;
    }
  }

  .t-body {
    padding: 24px 28px 20px;

    .date {
      font-family: var(--font-family-sans);
      font-size: 15px;
      color: var(--ticket-accent);
      margin-bottom: 14px;
      font-weight: 800;
      letter-spacing: 0.05em;
    }

    .title {
      font-family: var(--font-family-display);
      font-size: clamp(34px, 8.5vw, 40px);
      font-weight: 900;
      line-height: 1.1;
      margin-bottom: 24px;
      text-transform: uppercase;
      color: #ffffff;
      letter-spacing: 0.02em;
    }

    .theater-info {
      font-family: var(--font-family-sans);
      font-size: 15px;
      font-weight: 700;
      display: flex;
      justify-content: space-between;
      gap: 16px;
      color: rgba(255, 255, 255, 0.7);
    }
  }

  .perforation-wrap {
    position: relative;
    display: flex;
    align-items: center;
    margin: 8px 0;
  }

  .perforation-line {
    flex: 1;
    height: 1px;
    margin: 0 24px;
    background-image: linear-gradient(to right, rgba(255, 255, 255, 0.08) 50%, transparent 50%);
    background-size: 10px 1px;
    background-repeat: repeat-x;
  }

  .notch {
    position: absolute;
    width: 20px;
    height: 20px;
    background-color: #060608; /* 与底板暗色融合 */
    border-radius: 50%;
    z-index: 5;
    top: 50%;
    transform: translateY(-50%);
  }

  .notch-left {
    left: -10px;
    box-shadow: inset -4px 0 6px rgba(0, 0, 0, 0.8);
  }

  .notch-right {
    right: -10px;
    box-shadow: inset 4px 0 6px rgba(0, 0, 0, 0.8);
  }

  .t-seat {
    padding: 20px 28px 20px;

    .s-label {
      font-family: var(--font-family-sans);
      font-size: 13px;
      letter-spacing: 0.1em;
      margin-bottom: 16px;
      color: var(--ticket-accent);
      font-weight: 800;
      text-transform: uppercase;
    }

    .s-details-zoned {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;

      .sm {
        font-family: var(--font-family-sans);
        font-size: 12px;
        color: rgba(255, 255, 255, 0.4);
        margin-bottom: 6px;
        text-transform: uppercase;
        font-weight: 700;
      }

      .lg-name {
        font-family: var(--font-family-display);
        font-size: clamp(24px, 5.5vw, 28px);
        font-weight: 900;
        color: #ffffff;
        line-height: 1.1;
      }

      .lg-label {
        font-family: var(--font-family-sans);
        font-size: 18px;
        font-weight: 700;
        color: var(--ticket-accent); /* 契合主色调，不再是黄金色 */
      }

      .instructions {
        font-family: var(--font-family-sans);
        font-size: 12px;
        color: var(--color-text-secondary);
        margin-top: 4px;
        line-height: 1.35;
      }

      .holder-line {
        font-family: var(--font-family-sans);
        font-size: 12px;
        color: rgba(255, 255, 255, 0.55);
        margin-top: 6px;
        letter-spacing: 0.02em;
      }
    }
  }

  .t-barcode {
    padding: 20px 28px 24px;
    text-align: center;

    .barcode-container {
      position: relative;
      width: 100%;
      height: 120px; /* 拉长高度至120px，使彩线舒展 */
      margin-bottom: 12px;
      padding: 16px 20px;
      background: #121215; /* 纯暗背景面板，贴合图中卡片 */
      border-radius: 12px;
      overflow: hidden;
      border: 1px solid rgba(255, 255, 255, 0.05);
      display: flex;
      align-items: center;
      justify-content: center;

      .bars {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center; /* 垂直居中对齐 */
        justify-content: space-between;

        span {
          flex: 0 0 auto;
          border-radius: 1px;
        }
      }
    }

    .code-str {
      font-family: 'Courier New', Courier, monospace;
      font-size: 16px; /* 增大票码字号 */
      letter-spacing: 0.25em;
      font-weight: 700;
      color: rgba(255, 255, 255, 0.75); /* 提升对比度，更加显眼 */
      margin-top: 12px; /* 增加与条形码的距离 */
      text-transform: uppercase;
      overflow-wrap: anywhere;
    }
  }

  .t-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 18px 28px;
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0;
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.018)),
      rgba(3, 3, 5, 0.72);
    color: rgba(255, 255, 255, 0.78);
    border-top: 1px dashed rgba(229, 9, 20, 0.2);

    .tag {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #ffffff;

      .dot {
        width: 8px;
        height: 8px;
        background-color: var(--color-success);
        border-radius: 50%;
        box-shadow: 0 0 8px var(--color-success);
      }

      &.tag--checkedIn,
      &.tag--void {
        color: rgba(255, 255, 255, 0.68);

        .dot {
          background-color: rgba(255, 255, 255, 0.44);
          box-shadow: none;
        }
      }

      &.tag--reserved {
        color: #ffd37d;

        .dot {
          background-color: #ffd37d;
          box-shadow: 0 0 8px rgba(255, 184, 77, 0.45);
        }
      }
    }
  }
}

@media (max-width: 520px) {
  .ticket-page {
    align-items: flex-start;
    padding: var(--spacing-4) var(--spacing-3);
  }

  .ticket-card {
    .t-body {
      padding: var(--spacing-5) var(--spacing-4);

      .title {
        font-size: 32px;
      }

      .theater-info {
        flex-direction: column;
        gap: 6px;
      }
    }

    .t-seat,
    .t-barcode,
    .t-header,
    .t-footer {
      padding-left: var(--spacing-4);
      padding-right: var(--spacing-4);
    }

    .t-seat .s-details-zoned {
      grid-template-columns: 1fr;
    }

    .t-barcode .barcode-container .bars {
      transform: scaleX(0.78);
      transform-origin: center top;
    }
  }
}

@media (max-width: 420px) {
  .ticket-card .t-barcode .barcode-container .bars {
    transform: scaleX(0.62);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ticket-reveal-enter-active,
  .ticket-toolbar button,
  .ticket-nav a {
    animation: none;
    transition: none;
  }
}
</style>
