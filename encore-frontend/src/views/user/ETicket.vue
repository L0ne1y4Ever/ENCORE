<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getOrderDetail } from '../../api/order'
import { getShowDetail, getShowSchedules } from '../../api/show'
import type { Order } from '../../mock/orders'
import type { Show, Schedule } from '../../mock/shows'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t, locale } = useI18n()
const orderId = route.params.id as string

const order = ref<Order | null>(null)
const show = ref<Show | null>(null)
const schedule = ref<Schedule | null>(null)
const loading = ref(true)

onMounted(async () => {
  order.value = (await getOrderDetail(orderId)) || null
  if (order.value) {
    const schId = order.value.scheduleId
    // 因为 mock 接口中 getShowSchedules 需要 showId，我们简化假设根据已知关系推导
    // 这里简单硬编码映射一下，实际应用会有后端联表查询
    let sId = 's-001'
    if (schId.includes('2')) sId = 's-002'
    if (schId.includes('3')) sId = 's-003'

    show.value = (await getShowDetail(sId)) || null
    const schs = await getShowSchedules(sId)
    schedule.value = schs.find(s => s.id === schId) || null
  }

  // 添加一点延迟体现“票面展开”的仪式感
  setTimeout(() => {
    loading.value = false
  }, 400)
})

const formatDate = (dateStr: string) => {
  const d = new Date(dateStr)
  const dateLocale = locale.value === 'zh' ? 'zh-CN' : 'en-US'
  const date = d.toLocaleDateString(dateLocale, { month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()
  const time = d.toLocaleTimeString(dateLocale, { hour: '2-digit', minute: '2-digit' })
  return { date, time }
}
</script>

<template>
  <div class="ticket-page">
    <transition name="ticket-reveal">
      <div class="ticket-container" v-if="!loading && order && show && schedule">
        <div
          class="ticket-wrapper"
          v-for="ticket in order.tickets"
          :key="ticket.id"
        >
          <div class="ticket-card" :class="{'vip-ticket': ticket.seatId ? ticket.seatId.includes('VIP') : ticket.areaType === 'VIP'}">
            <!-- 票全息涂层/反光 -->
            <div class="foil-overlay"></div>

            <!-- 票头部 -->
            <div class="t-header">
              <div class="back-link btn-interactive" @click="$router.push('/')">← {{ t('common.back') }}</div>
              <div class="brand">ENCORE</div>
            </div>

            <!-- 剧目与时间 -->
            <div class="t-body">
              <div class="date">{{ formatDate(schedule.startTime).date }}</div>
              <h1 class="title">{{ show.title }}</h1>
              <div class="theater-info">
                <span>{{ schedule.theaterName }}</span>
                <span>{{ formatDate(schedule.startTime).time }}</span>
              </div>
            </div>

            <!-- 分界打孔线 -->
            <div class="perforation-line"></div>

            <!-- 座位区 -->
            <div class="t-seat">
              <div class="s-label">{{ t('ticket.seat') }}</div>

              <!-- Seated Tickets -->
              <div class="s-details" v-if="ticket.seatId">
                <div class="s-item">
                  <div class="sm">{{ t('ticket.section') }}</div>
                  <div class="lg">{{ ticket.seatId.includes('VIP') ? 'VIP' : 'STD' }}</div>
                </div>
                <div class="s-item">
                  <div class="sm">{{ t('ticket.row') }}</div>
                  <div class="lg">{{ ticket.seatId.split('-')[1] }}</div>
                </div>
                <div class="s-item">
                  <div class="sm">{{ t('ticket.number') }}</div>
                  <div class="lg">{{ ticket.seatId.split('-')[2] }}</div>
                </div>
              </div>

              <!-- Zoned Tickets -->
              <div class="s-details-zoned" v-else>
                <div class="zoned-main">
                  <div class="sm">{{ t('ticket.section') }}</div>
                  <div class="lg-name">{{ ticket.areaName || '自由区域' }}</div>
                </div>
                <div class="zoned-info">
                  <div class="sm">座位信息 / SEAT</div>
                  <div class="lg-label">不指定座位</div>
                  <div class="instructions">凭票入场，区域内自由站立</div>
                </div>
              </div>
            </div>

            <!-- 二维码区 -->
            <div class="t-barcode">
              <div class="barcode-container">
                <div class="bars">
                  || ||| || ||| || ||| || ||| || ||| ||
                  || ||| || ||| || ||| || ||| || ||| ||
                  || ||| || ||| || ||| || ||| || ||| ||
                </div>
              </div>
              <div class="code-str">{{ ticket.ticketCode }}</div>
            </div>

            <!-- 状态区 -->
            <div class="t-footer">
              <div class="status">{{ t('ticket.statusValid') }}</div>
              <div class="tag">
                <span class="dot"></span> {{ t('ticket.unused') }}
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
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 80px);
  padding: var(--spacing-6);
  background-color: var(--color-bg-base);
}

.ticket-container {
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-8);
}

/* 票面展开动画 */
.ticket-reveal-enter-active {
  transition: all 800ms cubic-bezier(0.16, 1, 0.3, 1);
}
.ticket-reveal-enter-from {
  opacity: 0;
  transform: translateY(40px) scale(0.98);
}

.ticket-wrapper {
  filter: drop-shadow(0 20px 40px rgba(0,0,0,0.6));
  perspective: 1000px;
}

.ticket-card {
  position: relative;
  background-color: #121216;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);

  /* 拟物化：使用 radial-gradient 创建打孔边缘效果 */
  background-image: radial-gradient(circle at 0 0, transparent 8px, #121216 9px),
                    radial-gradient(circle at 100% 0, transparent 8px, #121216 9px),
                    radial-gradient(circle at 0 100%, transparent 8px, #121216 9px),
                    radial-gradient(circle at 100% 100%, transparent 8px, #121216 9px);
  background-position: top left, top right, bottom left, bottom right;
  background-size: 51% 51%;
  background-repeat: no-repeat;
  border: none;

  /* 添加微噪点纹理增加真实感 */
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    opacity: 0.15;
    background-image: url('data:image/svg+xml,%3Csvg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg"%3E%3Cfilter id="noiseFilter"%3E%3CfeTurbulence type="fractalNoise" baseFrequency="0.65" numOctaves="3" stitchTiles="stitch"/%3E%3C/filter%3E%3Crect width="100%25" height="100%25" filter="url(%23noiseFilter)"/%3E%3C/svg%3E');
    pointer-events: none;
    z-index: 1;
  }

  &.vip-ticket {
    background-color: #1a1510;
    background-image: radial-gradient(circle at 0 0, transparent 8px, #1a1510 9px),
                      radial-gradient(circle at 100% 0, transparent 8px, #1a1510 9px),
                      radial-gradient(circle at 0 100%, transparent 8px, #1a1510 9px),
                      radial-gradient(circle at 100% 100%, transparent 8px, #1a1510 9px);
    box-shadow: 0 0 25px rgba(200, 149, 90, 0.25);

    .foil-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(125deg,
        transparent 20%,
        rgba(200, 149, 90, 0.2) 30%,
        rgba(255, 255, 255, 0.25) 40%,
        transparent 50%
      );
      background-size: 200% 200%;
      animation: foil-shine 6s ease-in-out infinite;
      z-index: 2;
      pointer-events: none;
    }

    .t-header .brand {
      color: #c8955a;
      text-shadow: 0 0 10px rgba(200, 149, 90, 0.3);
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
    padding: var(--spacing-4) var(--spacing-5);

    .back-link {
      font-size: 12px;
      font-family: var(--font-family-sans);
      color: #c8955a;
      opacity: 0.8;
      cursor: pointer;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      transition: all var(--transition-fast);
      &:hover {
        opacity: 1;
        color: #ffffff;
        text-shadow: 0 0 8px rgba(200, 149, 90, 0.4);
      }
    }

    .brand {
      font-family: var(--font-family-display);
      font-weight: 900;
      letter-spacing: 0.1em;
      font-size: 20px;
      color: #ffffff;
    }
  }

  .t-body {
    padding: var(--spacing-6) var(--spacing-5);

    .date {
      font-family: var(--font-family-sans);
      font-size: 14px;
      color: #c8955a;
      margin-bottom: var(--spacing-4);
      font-weight: 700;
      letter-spacing: 0.05em;
    }

    .title {
      font-family: var(--font-family-display);
      font-size: 32px;
      font-weight: 900;
      line-height: 1.2;
      margin-bottom: var(--spacing-6);
      text-transform: uppercase;
      color: #ffffff;
      letter-spacing: -0.02em;
    }

    .theater-info {
      font-family: var(--font-family-sans);
      font-size: 15px;
      font-weight: 500;
      display: flex;
      justify-content: space-between;
      color: rgba(255, 255, 255, 0.7);
    }
  }

  .perforation-line {
    height: 1px;
    margin: 0 var(--spacing-5);
    background-image: linear-gradient(to right, rgba(200, 149, 90, 0.3) 50%, transparent 50%);
    background-size: 12px 1px;
    background-repeat: repeat-x;
    opacity: 0.6;
  }

  .t-seat {
    padding: var(--spacing-5);

    .s-label {
      font-family: var(--font-family-sans);
      font-size: 12px;
      letter-spacing: 0.1em;
      margin-bottom: var(--spacing-4);
      color: #c8955a;
      font-weight: 700;
      text-transform: uppercase;
    }

    .s-details {
      display: flex;
      justify-content: space-between;

      .s-item {
        .sm {
          font-family: var(--font-family-sans);
          font-size: 10px;
          color: rgba(255, 255, 255, 0.4);
          margin-bottom: 4px;
          text-transform: uppercase;
          font-weight: 700;
        }
        .lg {
          font-family: var(--font-family-display);
          font-size: 32px;
          font-weight: 900;
          line-height: 1;
          color: #ffffff;
        }
      }
    }

    .s-details-zoned {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;

      .sm {
        font-family: var(--font-family-sans);
        font-size: 10px;
        color: rgba(255, 255, 255, 0.4);
        margin-bottom: 4px;
        text-transform: uppercase;
        font-weight: 700;
      }

      .lg-name {
        font-family: var(--font-family-display);
        font-size: 24px;
        font-weight: 900;
        color: #ffffff;
        line-height: 1.2;
      }

      .lg-label {
        font-family: var(--font-family-sans);
        font-size: 16px;
        font-weight: 700;
        color: var(--color-accent);
      }

      .instructions {
        font-family: var(--font-family-sans);
        font-size: 11px;
        color: var(--color-text-secondary);
        margin-top: 4px;
        line-height: 1.3;
      }
    }
  }

  .t-barcode {
    padding: var(--spacing-6) var(--spacing-5);
    text-align: center;

    .barcode-container {
      position: relative;
      display: inline-block;
      margin-bottom: var(--spacing-3);
      padding: var(--spacing-3) var(--spacing-5);
      background: rgba(200, 149, 90, 0.06);
      border-radius: var(--radius-md);
      overflow: hidden;
      border: 1px solid rgba(200, 149, 90, 0.1);

      .bars {
        font-family: monospace;
        font-size: 24px;
        line-height: 0.8;
        letter-spacing: -1px;
        font-weight: 700;
        background: linear-gradient(90deg, #c8955a, #f3e5ab, #d4af37, #c8955a);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        color: transparent;
      }
    }

    .code-str {
      font-family: monospace;
      font-size: 14px;
      letter-spacing: 0.3em;
      font-weight: 700;
      color: rgba(255, 255, 255, 0.6);
    }
  }

  .t-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-4) var(--spacing-5);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    background: rgba(200, 149, 90, 0.08);
    color: #c8955a;
    border-top: 1px dashed rgba(200, 149, 90, 0.15);

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
    }
  }
}

@keyframes foil-shine {
  0% { background-position: 200% center; }
  100% { background-position: -200% center; }
}
</style>
