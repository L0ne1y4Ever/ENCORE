<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Calendar, Clock, Location, PriceTag } from '@element-plus/icons-vue'
import { getShowDetail, getShowSchedules } from '../../api/show'
import type { Schedule, Show } from '../../mock/shows'
import { formatScheduleDay, formatScheduleTime, handlePosterError, lowestPriceLabel, posterImageSrc } from '../../utils/ticketing'
import { formatMoney } from '../../utils/money'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const show = ref<Show | null>(null)
const schedules = ref<Schedule[]>([])
const loading = ref(true)
const showReserveModal = ref(false)
const reserveEmail = ref('')

const introText = computed(() => show.value?.intro || show.value?.description || '')
const castText = computed(() => show.value?.castMembers || t('detail.pendingContent'))
const creativeText = computed(() => show.value?.creativeTeam || t('detail.pendingContent'))
const fullSynopsisText = computed(() => show.value?.fullSynopsis || show.value?.description || '')

const numericPrice = (value: number | string | null | undefined) => {
  const amount = Number(String(value ?? '').replace(/,/g, ''))
  return Number.isFinite(amount) && amount > 0 ? amount : null
}

const schedulePriceLabel = (schedule: Schedule) => {
  const min = numericPrice(schedule.minPrice)
  if (min != null) return formatMoney(min, locale.value)
  return lowestPriceLabel(schedule.priceRange, locale.value) || schedule.priceRange
}

const lowestPrice = computed(() => {
  const minimums = schedules.value
    .map(item => numericPrice(item.minPrice))
    .filter((value): value is number => value != null)
  if (minimums.length) {
    return formatMoney(Math.min(...minimums), locale.value)
  }
  const fromShow = numericPrice(show.value?.minPrice)
  if (fromShow != null) {
    return formatMoney(fromShow, locale.value)
  }
  const price = schedules.value.map(schedulePriceLabel).find(Boolean)
  return price || t('home.pricePending')
})
const onSaleSchedules = computed(() => schedules.value.filter(item => item.status === 'ON_SALE'))

const scheduleGroups = computed(() => {
  const groups = new Map<string, Schedule[]>()
  for (const schedule of schedules.value) {
    const key = schedule.startTime.slice(0, 10)
    const list = groups.get(key) || []
    list.push(schedule)
    groups.set(key, list)
  }
  return Array.from(groups.entries())
    .map(([date, rows]) => ({
      date,
      label: formatScheduleDay(`${date}T00:00:00`, locale.value),
      rows: rows.sort((left, right) => left.startTime.localeCompare(right.startTime))
    }))
    .sort((left, right) => left.date.localeCompare(right.date))
})

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
  window.alert(t('reservation.successMsg', { count: '8,205' }))
  showReserveModal.value = false
}

const scrollToSchedules = () => {
  document.getElementById('schedule-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const statusLabel = (status: Schedule['status']) => {
  if (status === 'ON_SALE') return t('home.onSaleNow')
  if (status === 'PREPARING' || status === 'COMING_SOON') return t('detail.reserve')
  if (status === 'SOLD_OUT') return t('admin.scheduleStatusMap.soldOut')
  return t('detail.unavailable')
}

const ticketModeLabel = (mode?: string) => {
  if (!mode) return t('ticketMode.seated')
  return t(`ticketMode.${mode.toLowerCase()}`)
}
</script>

<template>
  <div v-if="loading" class="detail-loading">
    {{ t('common.loading') }}
  </div>

  <div v-else-if="show" class="show-detail">
    <section class="detail-hero">
      <img
        class="hero-bg"
        :src="posterImageSrc(show.coverUrl, show.title)"
        :alt="show.title"
        @error="handlePosterError($event, show.title)"
      />
      <div class="hero-scrim" />
      <div class="hero-inner">
        <div class="poster-frame">
          <img
            :src="posterImageSrc(show.coverUrl, show.title)"
            :alt="show.title"
            @error="handlePosterError($event, show.title)"
          />
        </div>

        <div class="hero-copy">
          <span class="category-badge">{{ show.category }}</span>
          <h1>{{ show.title }}</h1>
          <p>{{ show.subtitle }}</p>
          <div class="quick-facts">
            <span><Clock /> {{ show.duration }} {{ t('detail.minutes') }}</span>
            <span><PriceTag /> {{ t('home.ticketFrom') }} {{ lowestPrice }}</span>
            <span><Calendar /> {{ onSaleSchedules.length }} {{ t('home.onSaleSchedules') }}</span>
          </div>
          <div class="hero-tags">
            <span v-for="tag in show.tags" :key="tag">{{ tag }}</span>
          </div>
          <div class="hero-actions">
            <button class="primary-cta" type="button" @click="scrollToSchedules">
              {{ onSaleSchedules.length > 0 ? t('detail.book') : t('detail.reserve') }}
              <ArrowRight />
            </button>
            <button class="secondary-cta" type="button" @click="router.push('/')">
              {{ t('common.back') }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <div class="detail-body">
      <main class="content-column">
        <section class="content-section synopsis">
          <span class="section-kicker">{{ t('detail.synopsis') }}</span>
          <p>{{ introText }}</p>
        </section>

        <section id="schedule-list" class="content-section schedule-section">
          <div class="section-heading">
            <div>
              <span class="section-kicker">{{ t('detail.selectSchedule') }}</span>
              <h2>{{ t('detail.availableDates') }}</h2>
            </div>
            <span class="schedule-count">{{ schedules.length }}</span>
          </div>

          <div v-if="scheduleGroups.length > 0" class="date-groups">
            <div v-for="group in scheduleGroups" :key="group.date" class="date-group">
              <div class="date-label">{{ group.label }}</div>
              <div class="schedule-list">
                <article
                  v-for="sch in group.rows"
                  :key="sch.id"
                  class="schedule-card"
                  :class="{ presale: sch.status === 'PREPARING' || sch.status === 'COMING_SOON', disabled: sch.status !== 'ON_SALE' && sch.status !== 'PREPARING' && sch.status !== 'COMING_SOON' }"
                >
                  <div class="time-box">
                    <strong>{{ formatScheduleTime(sch.startTime, locale) }}</strong>
                    <span>{{ formatScheduleTime(sch.endTime, locale) }}</span>
                  </div>
                  <div class="schedule-main">
                    <div class="venue-line">
                      <Location />
                      <span>{{ sch.theaterName }}</span>
                    </div>
                    <div class="schedule-tags">
                      <span>{{ ticketModeLabel(sch.ticketMode) }}</span>
                      <span>{{ statusLabel(sch.status) }}</span>
                    </div>
                  </div>
                  <div class="schedule-price">
                    <span>{{ t('home.ticketFrom') }}</span>
                    <strong>{{ schedulePriceLabel(sch) }}</strong>
                  </div>
                  <button
                    v-if="sch.status === 'ON_SALE'"
                    class="book-btn"
                    type="button"
                    @click="goSeatSelection(sch.id)"
                  >
                    {{ t('detail.book') }}
                  </button>
                  <button
                    v-else-if="sch.status === 'PREPARING' || sch.status === 'COMING_SOON'"
                    class="reserve-btn"
                    type="button"
                    @click="handleReserve"
                  >
                    {{ t('detail.reserve') }}
                  </button>
                  <button v-else class="book-btn" type="button" disabled>
                    {{ t('detail.unavailable') }}
                  </button>
                </article>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            {{ t('detail.emptySchedules') }}
          </div>
        </section>

        <section class="content-section">
          <span class="section-kicker">{{ t('detail.castMembers') }}</span>
          <p>{{ castText }}</p>
        </section>

        <section class="content-section">
          <span class="section-kicker">{{ t('detail.creativeTeam') }}</span>
          <p>{{ creativeText }}</p>
        </section>

        <section class="content-section">
          <span class="section-kicker">{{ t('detail.fullSynopsis') }}</span>
          <p>{{ fullSynopsisText }}</p>
        </section>
      </main>

      <aside class="purchase-sidebar">
        <div class="sidebar-card">
          <span>{{ t('detail.ticketGuide') }}</span>
          <strong>{{ lowestPrice }}</strong>
          <p>{{ t('detail.ticketGuideCopy') }}</p>
          <button class="primary-cta" type="button" @click="scrollToSchedules">
            {{ t('detail.selectSchedule') }}
          </button>
        </div>
      </aside>
    </div>

    <transition name="fade">
      <div v-if="showReserveModal" class="modal-overlay" @click.self="showReserveModal = false">
        <div class="modal-content">
          <h2>{{ t('reservation.title') }}</h2>
          <p>{{ t('reservation.subtitle') }}</p>
          <label>
            <span>{{ t('reservation.email') }}</span>
            <input v-model="reserveEmail" type="email" :placeholder="t('reservation.email')" />
          </label>
          <div class="modal-actions">
            <button class="btn-cancel" type="button" @click="showReserveModal = false">{{ t('common.cancel') }}</button>
            <button class="btn-confirm" type="button" @click="submitReservation">{{ t('common.confirm') }}</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.detail-loading {
  min-height: calc(100vh - 76px);
  display: grid;
  place-items: center;
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
}

.show-detail {
  width: 100%;
  overflow-x: hidden;
}

.detail-hero {
  min-height: min(720px, calc(100vh - 76px));
  position: relative;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.9) contrast(1.05);
}

.hero-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 8, 8, 0.96) 0%, rgba(8, 8, 8, 0.72) 48%, rgba(8, 8, 8, 0.24) 100%),
    linear-gradient(180deg, rgba(8, 8, 8, 0.1) 0%, var(--color-bg-base) 100%);
}

.hero-inner {
  position: relative;
  z-index: 2;
  width: min(1200px, calc(100% - 40px));
  margin: 0 auto;
  padding: var(--spacing-7) 0 var(--spacing-6);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  align-items: end;
  gap: var(--spacing-6);
}

.poster-frame {
  border: 1px solid rgba(240, 237, 232, 0.2);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 28px 64px rgba(0, 0, 0, 0.58);

  img {
    width: 100%;
    aspect-ratio: 3 / 4;
    display: block;
    object-fit: cover;
  }
}

.hero-copy {
  min-width: 0;

  h1 {
    margin-top: var(--spacing-3);
    font-size: clamp(42px, 6vw, 82px);
    line-height: 0.98;
  }

  > p {
    max-width: 620px;
    margin-top: var(--spacing-3);
    color: rgba(240, 237, 232, 0.78);
    font-family: var(--font-family-sans);
    font-size: 19px;
    line-height: 1.55;
  }
}

.category-badge,
.hero-tags span,
.quick-facts span,
.schedule-tags span {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 800;
  padding: 8px 12px;
}

.category-badge {
  border-color: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.12em;
  text-transform: uppercase;

  &::before {
    content: '';
    display: inline-block;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #e50914;
    margin-right: 8px;
  }
}

.quick-facts,
.hero-tags,
.hero-actions {
  margin-top: var(--spacing-4);
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.quick-facts span {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  svg {
    width: 15px;
    height: 15px;
    color: var(--color-accent);
  }
}

.primary-cta,
.secondary-cta,
.book-btn,
.reserve-btn {
  min-height: 46px;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-family: var(--font-family-sans);
  font-weight: 900;
  padding: 0 18px;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease;

  svg {
    width: 17px;
    height: 17px;
  }
}

.primary-cta,
.book-btn {
  border: none;
  background: #e50914;
  color: #fff;

  &:hover:not(:disabled) {
    background: #f6121d;
  }
}

.secondary-cta,
.reserve-btn {
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);

  &:hover {
    background: rgba(255, 255, 255, 0.16);
    color: #fff;
  }
}

.book-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-ghost);
  cursor: not-allowed;
}

.detail-body {
  width: min(1200px, calc(100% - 40px));
  margin: 0 auto;
  padding: var(--spacing-5) 0 var(--spacing-7);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--spacing-6);
}

.content-column {
  min-width: 0;
  display: grid;
  gap: 24px;
}

.content-section {
  border-top: 1px solid var(--color-border);
  padding-top: 18px;
  margin-bottom: 0;

  p {
    max-width: 840px;
    margin: 0;
    color: var(--color-text-primary);
    font-family: var(--font-family-cjk);
    font-size: 17px;
    line-height: 1.68;
    white-space: pre-line;
  }
}

.synopsis p {
  font-size: 18px;
  line-height: 1.62;
}

.section-kicker {
  color: var(--color-accent);
  display: block;
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.08em;
  margin-bottom: 10px;
  text-transform: uppercase;
}

.section-heading {
  margin-bottom: 14px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-4);

  h2 {
    font-size: 30px;
    line-height: 1.08;
  }
}

.schedule-count {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 800;
  padding: 8px 12px;
}

.date-groups,
.schedule-list {
  display: grid;
  gap: 12px;
}

.date-label {
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.08em;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.schedule-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr) 112px auto;
  align-items: center;
  gap: 12px;
  padding: 14px;

  &.presale {
    border-color: rgba(255, 255, 255, 0.14);
  }

  &.disabled {
    opacity: 0.62;
  }
}

.time-box {
  border-right: 1px solid var(--color-border);
  display: grid;
  gap: 3px;
  font-family: var(--font-family-sans);
  padding-right: 12px;

  strong {
    color: var(--color-text-primary);
    font-size: 22px;
    font-variant-numeric: tabular-nums;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }
}

.schedule-main {
  min-width: 0;
  display: grid;
  gap: var(--spacing-2);
}

.venue-line {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-primary);
  font-family: var(--font-family-sans);
  font-weight: 800;

  svg {
    width: 16px;
    height: 16px;
    color: var(--color-accent);
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.schedule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    padding: 5px 9px;
  }
}

.schedule-price {
  display: grid;
  gap: 4px;
  font-family: var(--font-family-sans);

  span {
    color: var(--color-text-secondary);
    font-size: 11px;
    font-weight: 800;
    text-transform: uppercase;
  }

  strong {
    color: rgba(255, 255, 255, 0.94);
    font-size: 20px;
  }
}

.purchase-sidebar {
  min-width: 0;
}

.sidebar-card {
  position: sticky;
  top: 100px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 14px;
  background: var(--color-bg-elevated);
  padding: var(--spacing-4);
  display: grid;
  gap: var(--spacing-3);

  span,
  p {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    line-height: 1.5;
  }

  strong {
    color: rgba(255, 255, 255, 0.94);
    font-family: var(--font-family-display);
    font-size: 42px;
    line-height: 1;
  }
}

.empty-state {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  padding: var(--spacing-5);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.78);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: grid;
  place-items: center;
  padding: var(--spacing-4);
}

.modal-content {
  width: min(440px, 100%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  padding: var(--spacing-5);

  h2 {
    font-size: 28px;
    margin-bottom: var(--spacing-2);
  }

  p {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    margin-bottom: var(--spacing-4);
  }

  label {
    display: grid;
    gap: 8px;
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 700;
  }

  input {
    min-height: 48px;
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius-sm);
    background: var(--color-bg-base);
    color: var(--color-text-primary);
    font-size: 16px;
    outline: none;
    padding: 0 12px;

    &:focus {
      border-color: var(--color-accent);
    }
  }
}

.modal-actions {
  margin-top: var(--spacing-5);
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-2);

  button {
    min-height: 42px;
    border-radius: 4px;
    cursor: pointer;
    font-family: var(--font-family-sans);
    font-weight: 800;
    padding: 0 16px;
  }
}

.btn-cancel {
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: var(--color-text-secondary);

  &:hover {
    background: rgba(255, 255, 255, 0.16);
    color: #fff;
  }
}

.btn-confirm {
  border: none;
  background: #e50914;
  color: #fff;

  &:hover {
    background: #f6121d;
  }
}

@media (max-width: 900px) {
  .hero-inner,
  .detail-body {
    grid-template-columns: 1fr;
  }

  .poster-frame {
    width: 180px;
  }

  .purchase-sidebar {
    order: -1;
  }

  .sidebar-card {
    position: static;
  }
}

@media (max-width: 680px) {
  .detail-hero {
    min-height: 680px;
  }

  .hero-inner,
  .detail-body {
    width: min(100% - 24px, 1200px);
  }

  .schedule-card {
    grid-template-columns: 1fr;
  }

  .time-box {
    border-right: none;
    border-bottom: 1px solid var(--color-border);
    padding: 0 0 var(--spacing-2);
  }

  .book-btn,
  .reserve-btn {
    width: 100%;
  }
}
</style>
