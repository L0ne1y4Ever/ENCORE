<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShowDetail, getShowSchedules } from '../../api/show'
import type { Show, Schedule } from '../../mock/shows'
import { useI18n } from 'vue-i18n'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const show = ref<Show | null>(null)
const schedules = ref<Schedule[]>([])
const loading = ref(true)
const showReserveModal = ref(false)
const reserveEmail = ref('')

onMounted(async () => {
  const id = route.params.id as string
  const [sData, schData] = await Promise.all([
    getShowDetail(id),
    getShowSchedules(id)
  ])
  show.value = sData || null
  schedules.value = schData || []
  loading.value = false
})

const goSeatSelection = (scheduleId: string) => {
  router.push(`/seat/${scheduleId}`)
}

const handleReserve = () => {
  showReserveModal.value = true
}

const submitReservation = () => {
  if (!reserveEmail.value) return
  alert(t('reservation.successMsg', { count: '8,205' }))
  showReserveModal.value = false
}

const formatDate = (dateStr: string) => {
  const d = new Date(dateStr)
  const dateLocale = locale.value === 'zh' ? 'zh-CN' : 'en-US'
  return d.toLocaleDateString(dateLocale, { month: 'short', day: 'numeric', year: 'numeric' })
}

const formatTime = (dateStr: string) => {
  const d = new Date(dateStr)
  const dateLocale = locale.value === 'zh' ? 'zh-CN' : 'en-US'
  return d.toLocaleTimeString(dateLocale, { hour: 'numeric', minute: '2-digit' })
}
</script>

<template>
  <div class="show-detail stagger-fade-up" v-if="!loading && show">
    <!-- 高斯模糊背景 + 前景海报 -->
    <div class="detail-hero">
      <div class="bg-blur-container">
        <img :src="show.coverUrl" class="bg-blur-img" />
        <div class="bg-overlay"></div>
      </div>

      <div class="hero-content">
        <div class="poster-container stagger-fade-up stagger-delay-1">
          <img :src="show.coverUrl" :alt="show.title" class="poster-img" />
        </div>
        <div class="hero-text stagger-fade-up stagger-delay-2">
          <div class="badge">{{ show.category }}</div>
          <h1 class="title">{{ show.title }}</h1>
          <p class="subtitle">{{ show.subtitle }}</p>
        </div>
      </div>
    </div>

    <!-- 杂志风正文排版 -->
    <div class="content-container stagger-fade-up stagger-delay-3">
      <main class="main-desc">
        <section class="synopsis">
          <h2 class="section-title">{{ t('detail.synopsis') }}</h2>
          <p class="drop-cap">{{ show.description }}</p>
        </section>

        <!-- 场次卡片列表 -->
        <section class="schedules-section">
          <h2 class="section-title">{{ t('detail.selectSchedule') }}</h2>
          <div class="schedule-grid">
            <div
              class="schedule-card btn-interactive"
              v-for="sch in schedules"
              :key="sch.id"
              :class="{'disabled': sch.status !== 'ON_SALE' && sch.status !== 'PREPARING' && sch.status !== 'COMING_SOON'}"
            >
              <div class="sch-date-box">
                <div class="sch-time">{{ formatTime(sch.startTime) }}</div>
                <div class="sch-date">{{ formatDate(sch.startTime) }}</div>
              </div>
              <div class="sch-info">
                <div class="sch-theater">{{ sch.theaterName }}</div>
                <div class="sch-price">{{ sch.priceRange }}</div>
              </div>
              <div class="sch-action">
                <button
                  v-if="sch.status === 'PREPARING' || sch.status === 'COMING_SOON'"
                  class="btn-outline reserve-btn"
                  @click.stop="handleReserve"
                >
                  {{ t('detail.reserve') }}
                </button>
                <button
                  v-else
                  class="btn-solid"
                  :disabled="sch.status !== 'ON_SALE'"
                  @click.stop="goSeatSelection(sch.id)"
                >
                  {{ sch.status === 'ON_SALE' ? t('detail.book') : t('detail.unavailable') }}
                </button>
              </div>
            </div>
            <div v-if="schedules.length === 0" class="empty-state">
              {{ t('detail.emptySchedules') }}
            </div>
          </div>
        </section>
      </main>

      <aside class="meta-sidebar">
        <div class="meta-block">
          <div class="meta-label">{{ t('detail.duration') }}</div>
          <div class="meta-value">{{ show.duration }} {{ t('detail.minutes') }}</div>
        </div>
        <div class="meta-block">
          <div class="meta-label">{{ t('detail.tags') }}</div>
          <div class="tags">
            <span v-for="tag in show.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
        </div>
      </aside>
    </div>

    <!-- Reservation Modal -->
    <transition name="fade">
      <div class="modal-overlay" v-if="showReserveModal" @click.self="showReserveModal = false">
        <div class="modal-content">
          <h2>{{ t('reservation.title') }}</h2>
          <p>{{ t('reservation.subtitle') }}</p>
          <input type="email" v-model="reserveEmail" :placeholder="t('reservation.email')" class="modal-input" />
          <div class="modal-actions">
            <button class="btn-cancel btn-interactive" @click="showReserveModal = false">{{ t('common.cancel') }}</button>
            <button class="btn-confirm btn-interactive" @click="submitReservation">{{ t('common.confirm') }}</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.show-detail {
  width: 100%;
}

/* 沉浸式首屏 */
.detail-hero {
  position: relative;
  min-height: 60vh;
  display: flex;
  align-items: flex-end;
  padding: var(--spacing-8) var(--spacing-6) var(--spacing-6);
  overflow: hidden;

  .bg-blur-container {
    position: absolute;
    inset: -10%; /* 扩展边缘防止模糊漏底 */
    z-index: 0;

    .bg-blur-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      filter: blur(40px) brightness(0.6) saturate(1.2);
      transform: scale(1.1);
    }

    .bg-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(to top, var(--color-bg-base) 0%, rgba(8, 8, 8, 0.4) 100%);
    }
  }

  .hero-content {
    position: relative;
    z-index: 10;
    max-width: 1200px;
    width: 100%;
    margin: 0 auto;
    display: flex;
    align-items: flex-end;
    gap: var(--spacing-6);

    @media (max-width: 768px) {
      flex-direction: column;
      align-items: flex-start;
    }
  }

  .poster-container {
    flex-shrink: 0;
    width: 240px;
    border-radius: var(--radius-md);
    overflow: hidden;
    box-shadow: 0 24px 48px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.1);

    .poster-img {
      width: 100%;
      display: block;
      aspect-ratio: 3/4;
      object-fit: cover;
    }

    @media (max-width: 768px) {
      width: 160px;
    }
  }

  .hero-text {
    flex: 1;
    padding-bottom: var(--spacing-2);

    .badge {
      display: inline-block;
      padding: 4px 12px;
      background-color: var(--color-accent);
      color: #000;
      font-family: var(--font-family-sans);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      margin-bottom: var(--spacing-3);
    }

    .title {
      font-family: var(--font-family-display);
      font-size: clamp(40px, 5vw, 64px);
      font-weight: 900;
      line-height: 1.1;
      margin-bottom: var(--spacing-2);
      letter-spacing: 0.02em;
      color: #fff;
    }

    .subtitle {
      font-family: var(--font-family-sans);
      font-size: 20px;
      color: rgba(255, 255, 255, 0.7);
      font-weight: 300;
    }
  }
}

/* 杂志风内容区 */
.content-container {
  display: flex;
  gap: var(--spacing-8);
  padding: var(--spacing-8) var(--spacing-6);
  max-width: 1200px;
  margin: 0 auto;

  @media (max-width: 768px) {
    flex-direction: column-reverse;
  }
}

.main-desc {
  flex: 1;
  min-width: 0;

  section {
    margin-bottom: var(--spacing-8);
  }

  .section-title {
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.2em;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-5);
    border-top: 1px solid var(--color-border-strong);
    padding-top: var(--spacing-2);
  }

  .synopsis {
    .drop-cap {
      font-family: var(--font-family-cjk);
      font-size: 18px;
      line-height: 1.8;
      color: var(--color-text-primary);
      font-weight: 300;

      /* 首字下沉效果 */
      &::first-letter {
        float: left;
        font-family: var(--font-family-display);
        font-size: 4em;
        line-height: 0.8;
        padding-top: 4px;
        padding-right: 8px;
        color: var(--color-accent);
      }
    }
  }
}

/* 侧边栏元数据 */
.meta-sidebar {
  flex: 0 0 300px;

  .meta-block {
    margin-bottom: var(--spacing-6);

    .meta-label {
      font-family: var(--font-family-sans);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--color-text-secondary);
      margin-bottom: var(--spacing-2);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: var(--spacing-1);
    }

    .meta-value {
      font-size: 16px;
      font-weight: 500;
    }

    .tags {
      display: flex;
      flex-wrap: wrap;
      gap: var(--spacing-2);

      .tag {
        font-size: 12px;
        font-family: var(--font-family-sans);
        padding: 4px 12px;
        background-color: var(--color-bg-elevated);
        border: 1px solid var(--color-border);
        color: var(--color-text-secondary);
        border-radius: var(--radius-sm);
      }
    }
  }
}

/* 场次日历卡片 */
.schedule-grid {
  display: grid;
  gap: var(--spacing-4);

  .schedule-card {
    display: flex;
    align-items: center;
    background-color: var(--color-bg-elevated);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    padding: var(--spacing-4);
    gap: var(--spacing-4);

    &:hover {
      border-color: var(--color-border-strong);
      background-color: rgba(255, 255, 255, 0.02);
    }

    &.disabled {
      opacity: 0.5;
      pointer-events: none;
    }

    @media (max-width: 600px) {
      flex-direction: column;
      align-items: flex-start;
    }

    .sch-date-box {
      flex: 0 0 120px;
      border-right: 1px solid var(--color-border);
      padding-right: var(--spacing-4);

      .sch-time {
        font-family: var(--font-family-display);
        font-size: 24px;
        font-weight: 700;
        color: var(--color-text-primary);
      }

      .sch-date {
        font-family: var(--font-family-sans);
        font-size: 14px;
        color: var(--color-text-secondary);
      }

      @media (max-width: 600px) {
        border-right: none;
        border-bottom: 1px solid var(--color-border);
        padding-right: 0;
        padding-bottom: var(--spacing-2);
        width: 100%;
      }
    }

    .sch-info {
      flex: 1;

      .sch-theater {
        font-size: 16px;
        font-weight: 500;
        margin-bottom: 4px;
      }

      .sch-price {
        font-family: var(--font-family-sans);
        font-size: 14px;
        color: var(--color-accent);
      }
    }

    .sch-action {
      flex-shrink: 0;
    }
  }
}

/* 按钮样式 */
.btn-solid {
  background-color: var(--color-text-primary);
  color: var(--color-bg-base);
  border: none;
  padding: 10px 24px;
  font-family: var(--font-family-sans);
  font-size: 14px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all 150ms ease;

  &:hover:not(:disabled) {
    background-color: var(--color-accent);
    color: #fff;
  }

  &:disabled {
    background-color: var(--color-bg-overlay);
    color: var(--color-text-ghost);
    cursor: not-allowed;
  }
}

.btn-outline {
  background-color: transparent;
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  padding: 10px 24px;
  font-family: var(--font-family-sans);
  font-size: 14px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all 150ms ease;

  &:hover {
    background-color: rgba(200, 149, 90, 0.1);
  }
}

/* Modal 动效与样式 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 200ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background-color: var(--color-bg-elevated);
  padding: var(--spacing-6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  width: 90%;
  max-width: 440px;
  box-shadow: 0 24px 48px rgba(0,0,0,0.5);

  h2 {
    font-family: var(--font-family-display);
    font-size: 28px;
    margin-bottom: var(--spacing-2);
  }

  p {
    font-size: 15px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-6);
  }

  .modal-input {
    width: 100%;
    background: transparent;
    border: none;
    border-bottom: 2px solid var(--color-border-strong);
    padding: var(--spacing-2) 0;
    margin-bottom: var(--spacing-8);
    color: var(--color-text-primary);
    font-size: 16px;
    outline: none;
    transition: border-color 200ms ease;

    &:focus {
      border-bottom-color: var(--color-accent);
    }
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--spacing-4);

    button {
      background: transparent;
      border: none;
      font-family: var(--font-family-sans);
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      padding: 8px 16px;
    }

    .btn-cancel {
      color: var(--color-text-secondary);
    }

    .btn-confirm {
      color: var(--color-accent);
    }
  }
}
</style>
