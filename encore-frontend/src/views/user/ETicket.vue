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
          class="ticket-card" 
          v-for="ticket in order.tickets" 
          :key="ticket.id"
        >
          <!-- 票头部 -->
          <div class="t-header">
            <div class="back-link" @click="$router.push('/')">← {{ t('common.back') }}</div>
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

          <!-- 座位区 -->
          <div class="t-seat">
            <div class="s-label">{{ t('ticket.seat') }}</div>
            <div class="s-details">
              <div class="s-item">
                <div class="sm">{{ t('ticket.section') }}</div>
                <div class="lg">VIP</div>
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
          </div>

          <!-- 二维码区 -->
          <div class="t-barcode">
            <div class="bars">
              || ||| || ||| || ||| || ||| || ||| ||
              || ||| || ||| || ||| || ||| || ||| ||
              || ||| || ||| || ||| || ||| || ||| ||
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

.ticket-card {
  background-color: var(--color-bg-base);
  border: 2px solid var(--color-border-strong);
  display: flex;
  flex-direction: column;
  color: var(--color-text-primary);
  
  /* 极简边框风格，不使用圆角 */
  border-radius: 0;

  .t-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-4) var(--spacing-5);
    border-bottom: 1px solid var(--color-border-hairline);
    
    .back-link {
      font-size: 12px;
      font-family: var(--font-family-sans);
      color: var(--color-text-secondary);
      cursor: pointer;
      &:hover { color: var(--color-text-primary); }
    }
    
    .brand {
      font-family: var(--font-family-sans);
      font-weight: 700;
      letter-spacing: 0.1em;
    }
  }

  .t-body {
    padding: var(--spacing-6) var(--spacing-5);
    border-bottom: 1px solid var(--color-border-hairline);
    
    .date {
      font-family: var(--font-family-sans);
      font-size: 14px;
      color: var(--color-text-secondary);
      margin-bottom: var(--spacing-4);
    }
    
    .title {
      font-family: var(--font-family-display);
      font-size: 44px;
      font-style: italic;
      font-weight: 700;
      line-height: 1.1;
      margin-bottom: var(--spacing-6);
    }

    .theater-info {
      font-family: var(--font-family-cjk);
      font-size: 16px;
      display: flex;
      justify-content: space-between;
    }
  }

  .t-seat {
    padding: var(--spacing-5);
    border-bottom: 2px solid var(--color-border-bold);
    
    .s-label {
      font-family: var(--font-family-sans);
      font-size: 12px;
      letter-spacing: 0.1em;
      margin-bottom: var(--spacing-4);
      color: var(--color-text-secondary);
    }

    .s-details {
      display: flex;
      justify-content: space-between;
      
      .s-item {
        .sm {
          font-family: var(--font-family-sans);
          font-size: 10px;
          color: var(--color-text-secondary);
          margin-bottom: 4px;
        }
        .lg {
          font-family: var(--font-family-display);
          font-size: 32px;
          line-height: 1;
        }
      }
    }
  }

  .t-barcode {
    padding: var(--spacing-6) var(--spacing-5);
    border-bottom: 1px solid var(--color-border-hairline);
    text-align: center;
    
    .bars {
      font-family: monospace;
      font-size: 24px;
      line-height: 0.8;
      color: var(--color-text-primary);
      margin-bottom: var(--spacing-3);
      letter-spacing: -1px;
    }
    
    .code-str {
      font-family: monospace;
      font-size: 14px;
      letter-spacing: 0.2em;
    }
  }

  .t-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-4) var(--spacing-5);
    font-family: var(--font-family-sans);
    font-size: 12px;
    
    .status {
      font-weight: 500;
    }
    
    .tag {
      display: flex;
      align-items: center;
      gap: 6px;
      
      .dot {
        width: 6px;
        height: 6px;
        background-color: var(--color-accent-vermilion);
        border-radius: 50%;
      }
    }
  }
}

/* 根据简报定义的额外变量补充，以防漏写 */
:root {
  --color-border-hairline: rgba(240, 237, 232, 0.1);
  --color-border-bold: rgba(240, 237, 232, 0.4);
  --color-accent-vermilion: #D1302A;
}
</style>
