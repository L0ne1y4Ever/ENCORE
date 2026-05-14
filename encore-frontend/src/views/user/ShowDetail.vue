<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShowDetail, getShowSchedules } from '../../api/show'
import type { Show, Schedule } from '../../mock/shows'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
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
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' })
}
</script>

<template>
  <div class="show-detail" v-if="!loading && show">
    <div class="detail-hero">
      <img :src="show.coverUrl" class="bg-img" />
      <div class="overlay"></div>
      <div class="hero-text">
        <h1 class="title">{{ show.title }}</h1>
        <p class="subtitle">{{ show.subtitle }}</p>
      </div>
    </div>

    <div class="content-container">
      <aside class="meta-sidebar">
        <div class="meta-item">
          <label>{{ t('detail.category') }}</label>
          <div>{{ show.category }}</div>
        </div>
        <div class="meta-item">
          <label>{{ t('detail.duration') }}</label>
          <div>{{ show.duration }} Mins</div>
        </div>
        <div class="meta-item">
          <label>{{ t('detail.tags') }}</label>
          <div class="tags">
            <span v-for="tag in show.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
        </div>
      </aside>

      <main class="main-desc">
        <section class="synopsis">
          <h2>{{ t('detail.synopsis') }}</h2>
          <p>{{ show.description }}</p>
        </section>

        <section class="schedules-section">
          <h2>{{ t('detail.selectSchedule') }}</h2>
          <div class="schedule-list">
            <div 
              class="schedule-row" 
              v-for="sch in schedules" 
              :key="sch.id"
            >
              <div class="sch-time">
                <div class="date">{{ formatDate(sch.startTime) }}</div>
                <div class="theater">{{ sch.theaterName }}</div>
              </div>
              <div class="sch-price">{{ sch.priceRange }}</div>
              <div class="sch-action">
                <button 
                  v-if="sch.status === 'PREPARING' || sch.status === 'COMING_SOON'"
                  class="btn-primary reserve-btn" 
                  @click="handleReserve"
                >
                  {{ t('detail.reserve') }}
                </button>
                <button 
                  v-else
                  class="btn-primary" 
                  :disabled="sch.status !== 'ON_SALE'"
                  @click="goSeatSelection(sch.id)"
                >
                  {{ sch.status === 'ON_SALE' ? t('detail.book') : t('detail.unavailable') }}
                </button>
              </div>
            </div>
            <div v-if="schedules.length === 0" class="empty-state">
              No schedules available for this show.
            </div>
          </div>
        </section>
      </main>
    </div>

    <!-- Reservation Modal -->
    <div class="modal-overlay" v-if="showReserveModal" @click.self="showReserveModal = false">
      <div class="modal-content">
        <h2>{{ t('reservation.title') }}</h2>
        <p>{{ t('reservation.subtitle') }}</p>
        <input type="email" v-model="reserveEmail" :placeholder="t('reservation.email')" class="modal-input" />
        <div class="modal-actions">
          <button class="btn-cancel" @click="showReserveModal = false">{{ t('common.cancel') }}</button>
          <button class="btn-confirm" @click="submitReservation">{{ t('common.confirm') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.show-detail {
  width: 100%;
}

.detail-hero {
  height: 50vh;
  position: relative;
  overflow: hidden;

  .bg-img {
    width: 100%;
    height: 120%;
    object-fit: cover;
    object-position: center 20%;
    transform: translateY(-10%);
  }

  .overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(to top, var(--color-bg-base) 0%, transparent 100%);
  }

  .hero-text {
    position: absolute;
    bottom: var(--spacing-6);
    left: var(--spacing-6);
    right: var(--spacing-6);

    .title {
      font-family: var(--font-family-display);
      font-size: 56px;
      font-weight: 900;
      line-height: 1.1;
      margin-bottom: var(--spacing-2);
    }

    .subtitle {
      font-family: var(--font-family-sans);
      font-size: 18px;
      color: var(--color-text-secondary);
    }
  }
}

.content-container {
  display: flex;
  gap: var(--spacing-8);
  padding: var(--spacing-6);
  max-width: 1200px;
  margin: 0 auto;

  @media (max-width: 768px) {
    flex-direction: column;
  }
}

.meta-sidebar {
  flex: 0 0 240px;
  
  .meta-item {
    margin-bottom: var(--spacing-5);
    
    label {
      display: block;
      font-family: var(--font-family-sans);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--color-text-secondary);
      margin-bottom: var(--spacing-1);
    }

    div {
      font-size: 16px;
    }

    .tags {
      display: flex;
      flex-wrap: wrap;
      gap: var(--spacing-2);

      .tag {
        font-size: 12px;
        padding: 2px 8px;
        border: 1px solid var(--color-border-strong);
        border-radius: var(--radius-full);
      }
    }
  }
}

.main-desc {
  flex: 1;

  section {
    margin-bottom: var(--spacing-8);

    h2 {
      font-family: var(--font-family-display);
      font-size: 32px;
      margin-bottom: var(--spacing-4);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: var(--spacing-2);
    }

    p {
      font-family: var(--font-family-cjk);
      font-size: 16px;
      line-height: 1.8;
      color: var(--color-text-primary);
    }
  }
}

.schedule-list {
  display: flex;
  flex-direction: column;

  .schedule-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--spacing-4) 0;
    border-bottom: 1px solid var(--color-border);

    @media (max-width: 600px) {
      flex-direction: column;
      align-items: flex-start;
      gap: var(--spacing-3);
    }

    .sch-time {
      flex: 2;
      .date {
        font-size: 18px;
        font-weight: 500;
        margin-bottom: var(--spacing-1);
      }
      .theater {
        font-size: 14px;
        color: var(--color-text-secondary);
      }
    }

    .sch-price {
      flex: 1;
      font-family: var(--font-family-sans);
      color: var(--color-accent);
      font-weight: 500;
    }

    .sch-action {
      flex: 1;
      text-align: right;
      
      @media (max-width: 600px) {
        width: 100%;
        text-align: left;
      }
    }
  }
}

.btn-primary {
  background-color: var(--color-text-primary);
  color: var(--color-bg-base);
  border: none;
  padding: 12px 24px;
  font-family: var(--font-family-sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all 150ms ease;

  &.reserve-btn {
    background-color: transparent;
    border: 1px solid var(--color-accent);
    color: var(--color-accent);
    
    &:hover {
      background-color: rgba(200, 149, 90, 0.1);
    }
  }

  &:hover:not(:disabled):not(.reserve-btn) {
    background-color: var(--color-accent);
    color: #fff;
  }

  &:disabled {
    background-color: var(--color-bg-elevated);
    color: var(--color-text-ghost);
    cursor: not-allowed;
  }
}

.empty-state {
  padding: var(--spacing-6) 0;
  color: var(--color-text-secondary);
  font-style: italic;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background-color: var(--color-bg-elevated);
  padding: var(--spacing-8);
  border: 1px solid var(--color-border);
  width: 100%;
  max-width: 400px;
  
  h2 {
    font-family: var(--font-family-display);
    font-size: 24px;
    margin-bottom: var(--spacing-2);
  }
  
  p {
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-6);
  }
  
  .modal-input {
    width: 100%;
    background: transparent;
    border: none;
    border-bottom: 1px solid var(--color-border-strong);
    padding: var(--spacing-2) 0;
    margin-bottom: var(--spacing-8);
    color: var(--color-text-primary);
    outline: none;
    
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
      font-weight: 600;
      cursor: pointer;
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
