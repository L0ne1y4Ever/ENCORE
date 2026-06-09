<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAdminDashboard } from '../../api/admin'
import type { AdminDashboard } from '../../api/admin'
import { subscribeToDashboardUpdates } from '../../api/adminRealtime'
import type { RealtimeConnectionState } from '../../api/realtime'

const { t } = useI18n()

const dashboard = ref<AdminDashboard | null>(null)
const loading = ref(false)
const realtimeState = ref<RealtimeConnectionState>('connecting')
const realtimeRefreshing = ref(false)
const realtimeNotice = ref<string | null>(null)
let disconnectDashboardRealtime: (() => void) | undefined
let realtimeNoticeTimer: ReturnType<typeof setTimeout> | undefined
let realtimeRefreshTimer: ReturnType<typeof setTimeout> | undefined

const toNumber = (value: number | string | undefined | null) => Number(value || 0)

const showRealtimeNotice = (messageKey: string) => {
  realtimeNotice.value = messageKey
  if (realtimeNoticeTimer) {
    clearTimeout(realtimeNoticeTimer)
  }
  realtimeNoticeTimer = setTimeout(() => {
    realtimeNotice.value = null
  }, 2800)
}

const loadDashboard = async (silent = false) => {
  if (silent) {
    realtimeRefreshing.value = true
  } else {
    loading.value = true
  }
  try {
    dashboard.value = await getAdminDashboard()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    if (silent) {
      realtimeRefreshing.value = false
    } else {
      loading.value = false
    }
  }
}

const queueRealtimeRefresh = () => {
  showRealtimeNotice('admin.dashboardLiveUpdated')
  if (realtimeRefreshTimer) {
    clearTimeout(realtimeRefreshTimer)
  }
  realtimeRefreshTimer = setTimeout(() => {
    void loadDashboard(true)
  }, 300)
}

onMounted(() => {
  void loadDashboard()
  disconnectDashboardRealtime = subscribeToDashboardUpdates({
    onEvent: queueRealtimeRefresh,
    onStateChange: (state) => {
      realtimeState.value = state
    }
  })
})

onBeforeUnmount(() => {
  disconnectDashboardRealtime?.()
  if (realtimeNoticeTimer) {
    clearTimeout(realtimeNoticeTimer)
  }
  if (realtimeRefreshTimer) {
    clearTimeout(realtimeRefreshTimer)
  }
})

const formatAmount = (amount: number | string | undefined) => {
  return `$${toNumber(amount).toFixed(2)}`
}

const formatPercent = (value: number | string | undefined) => {
  return `${toNumber(value).toFixed(1)}%`
}

const formatDateLabel = (date: string) => {
  return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

const stats = computed(() => [
  { label: t('admin.totalRevenue'), value: formatAmount(dashboard.value?.totalRevenue) },
  { label: t('admin.ticketsSold'), value: String(dashboard.value?.ticketsSold ?? 0) },
  { label: t('admin.activeShows'), value: String(dashboard.value?.activeShows ?? 0) },
  { label: t('admin.avgAttendance'), value: formatPercent(dashboard.value?.avgAttendance) }
])

const trendRows = computed(() => dashboard.value?.salesTrend ?? [])
const topShows = computed(() => dashboard.value?.topShows ?? [])
const checkInSummary = computed(() => dashboard.value?.checkInSummary ?? { checkedIn: 0, unused: 0, voided: 0 })

const trendMaxRevenue = computed(() => Math.max(1, ...trendRows.value.map(item => toNumber(item.revenue))))
const trendMaxTickets = computed(() => Math.max(1, ...trendRows.value.map(item => toNumber(item.ticketCount))))
const topShowsMaxTickets = computed(() => Math.max(1, ...topShows.value.map(item => toNumber(item.ticketCount))))

const trendHasSignal = computed(() => {
  return trendRows.value.some(item => toNumber(item.revenue) > 0 || toNumber(item.ticketCount) > 0)
})

const ratioPercent = (value: number | string | undefined, max: number, minimum = 4) => {
  const ratio = Math.round((toNumber(value) / Math.max(1, max)) * 100)
  return `${Math.max(minimum, Math.min(100, ratio))}%`
}

const revenueBarHeight = (value: number | string | undefined) => ratioPercent(value, trendMaxRevenue.value, 3)
const ticketBarHeight = (value: number | string | undefined) => ratioPercent(value, trendMaxTickets.value, 3)
const topShowBarWidth = (value: number | string | undefined) => ratioPercent(value, topShowsMaxTickets.value, 6)
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <div class="dashboard-header">
      <div>
        <h1>{{ t('admin.dashboard') }}</h1>
        <p>{{ t('admin.dashboardSubtitle') }}</p>
      </div>
      <div class="dashboard-actions">
        <div class="realtime-pill" aria-live="polite">
          <span class="status-dot" :class="`state-${realtimeState}`"></span>
          <span>{{ t(`admin.dashboardLive.${realtimeState}`) }}</span>
          <span v-if="realtimeRefreshing" class="realtime-extra">{{ t('admin.dashboardRefreshing') }}</span>
          <span v-else-if="realtimeNotice" class="realtime-extra">{{ t(realtimeNotice) }}</span>
        </div>
        <el-button type="primary" plain :loading="loading" @click="loadDashboard()">
          {{ t('admin.refresh') }}
        </el-button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-label">{{ stat.label }}</div>
        <div class="stat-value">{{ stat.value }}</div>
      </div>
    </div>

    <div class="chart-grid">
      <div class="chart-card wide">
        <h3>{{ t('admin.salesTrend') }}</h3>
        <div class="chart-container">
          <div v-if="trendRows.length" class="trend-chart" :class="{ muted: !trendHasSignal }">
            <div class="trend-plot">
              <div v-for="item in trendRows" :key="item.date" class="trend-column">
                <div class="trend-bars">
                  <span
                    class="trend-bar revenue"
                    :style="{ height: revenueBarHeight(item.revenue) }"
                    :title="`${t('admin.revenue')}: ${formatAmount(item.revenue)}`"
                  ></span>
                  <span
                    class="trend-bar tickets"
                    :style="{ height: ticketBarHeight(item.ticketCount) }"
                    :title="`${t('admin.tickets')}: ${item.ticketCount}`"
                  ></span>
                </div>
                <div class="trend-meta">
                  <strong>{{ item.ticketCount }}</strong>
                  <span>{{ formatDateLabel(item.date) }}</span>
                </div>
              </div>
            </div>
            <div class="chart-legend">
              <span><i class="legend-revenue"></i>{{ t('admin.revenue') }}</span>
              <span><i class="legend-tickets"></i>{{ t('admin.tickets') }}</span>
            </div>
          </div>
          <div v-else class="empty-chart">{{ t('admin.noDashboardData') }}</div>
        </div>
      </div>

      <div class="chart-card">
        <h3>{{ t('admin.topShows') }}</h3>
        <div class="chart-container">
          <div v-if="topShows.length" class="top-show-list">
            <div v-for="(item, index) in topShows" :key="item.showId" class="top-show-row">
              <span class="top-show-rank">{{ index + 1 }}</span>
              <div class="top-show-main">
                <div class="top-show-head">
                  <strong>{{ item.showTitle }}</strong>
                  <span>{{ item.ticketCount }} {{ t('admin.tickets') }}</span>
                </div>
                <div class="top-show-track">
                  <span :style="{ width: topShowBarWidth(item.ticketCount) }"></span>
                </div>
                <small>{{ formatAmount(item.revenue) }}</small>
              </div>
            </div>
          </div>
          <div v-else class="empty-chart">{{ t('admin.noDashboardData') }}</div>
        </div>
      </div>
    </div>

    <div class="summary-card">
      <div class="summary-header">
        <h3>{{ t('admin.checkInSummary') }}</h3>
        <span>{{ t('admin.validTickets') }}: {{ dashboard?.ticketsSold ?? 0 }}</span>
      </div>
      <div class="summary-grid">
        <div class="summary-item">
          <span class="label">{{ t('admin.checkedInTickets') }}</span>
          <span class="value success">{{ checkInSummary.checkedIn }}</span>
        </div>
        <div class="summary-item">
          <span class="label">{{ t('admin.unusedTickets') }}</span>
          <span class="value warning">{{ checkInSummary.unused }}</span>
        </div>
        <div class="summary-item">
          <span class="label">{{ t('admin.voidedTickets') }}</span>
          <span class="value muted">{{ checkInSummary.voided }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-6);
}

.dashboard-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
    line-height: 1.2;
  }

  p {
    margin-top: var(--spacing-2);
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 14px;
  }
}

.dashboard-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.realtime-pill {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 12px;
  background-color: rgba(240, 237, 232, 0.03);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background-color: var(--color-text-ghost);

  &.state-connected {
    background-color: var(--color-accent);
  }

  &.state-connecting {
    background-color: var(--color-border-strong);
  }

  &.state-disconnected {
    background-color: var(--color-text-ghost);
  }
}

.realtime-extra {
  color: var(--color-text-ghost);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-4);

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card,
.chart-card,
.summary-card {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.stat-card {
  padding: var(--spacing-5);
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  .stat-label {
    font-family: var(--font-family-sans);
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-4);
  }

  .stat-value {
    font-family: var(--font-family-display);
    font-size: 34px;
    font-weight: 700;
    color: var(--color-text-primary);
    line-height: 1.1;
  }
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.85fr);
  gap: var(--spacing-4);
  align-items: start;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  padding: var(--spacing-5);

  h3 {
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text-primary);
    margin-bottom: var(--spacing-4);
  }

  .chart-container {
    min-height: 236px;
    width: 100%;
  }
}

.chart-card.wide .chart-container {
  min-height: 264px;
}

.trend-chart {
  height: 264px;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: var(--spacing-3);
}

.trend-chart.muted {
  opacity: 0.78;
}

.trend-plot {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(7, minmax(42px, 1fr));
  align-items: end;
  gap: 12px;
  padding: 14px 2px 0;
  border-bottom: 1px solid var(--color-border);
}

.trend-column {
  min-width: 0;
  display: grid;
  grid-template-rows: minmax(140px, 1fr) auto;
  gap: 10px;
}

.trend-bars {
  min-height: 140px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
}

.trend-bar {
  width: 14px;
  min-height: 4px;
  border-radius: 3px 3px 0 0;
  display: block;
  transition: height 180ms ease;
}

.trend-bar.revenue {
  background: #c8955a;
}

.trend-bar.tickets {
  background: rgba(93, 160, 140, 0.8);
}

.trend-meta {
  min-width: 0;
  display: grid;
  gap: 3px;
  text-align: center;

  strong {
    color: var(--color-text-primary);
    font-size: 14px;
    line-height: 1;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.chart-legend {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--spacing-3);
  color: var(--color-text-secondary);
  font-size: 12px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  i {
    width: 10px;
    height: 10px;
    border-radius: 2px;
    display: inline-block;
  }
}

.legend-revenue {
  background: #c8955a;
}

.legend-tickets {
  background: rgba(93, 160, 140, 0.8);
}

.top-show-list {
  display: grid;
  gap: var(--spacing-3);
}

.top-show-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: var(--spacing-3);
  align-items: start;
  padding-bottom: var(--spacing-3);
  border-bottom: 1px solid var(--color-border);

  &:last-child {
    border-bottom: 0;
    padding-bottom: 0;
  }
}

.top-show-rank {
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  display: inline-grid;
  place-items: center;
  color: var(--color-accent);
  background: rgba(200, 149, 90, 0.08);
  font-size: 13px;
  font-weight: 700;
}

.top-show-main {
  min-width: 0;
  display: grid;
  gap: 8px;

  small {
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

.top-show-head {
  min-width: 0;
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-3);

  strong {
    min-width: 0;
    overflow: hidden;
    color: var(--color-text-primary);
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    flex: 0 0 auto;
    color: var(--color-text-secondary);
    font-size: 12px;
    white-space: nowrap;
  }
}

.top-show-track {
  height: 7px;
  overflow: hidden;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.06);

  span {
    height: 100%;
    min-width: 6px;
    display: block;
    border-radius: inherit;
    background: #c8955a;
  }
}

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
}

.summary-card {
  padding: var(--spacing-5);
}

.summary-header {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);

  h3 {
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 600;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-4);
}

.summary-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-4);
  display: flex;
  align-items: center;
  justify-content: space-between;

  .label {
    color: var(--color-text-secondary);
    font-size: 13px;
  }

  .value {
    font-family: var(--font-family-display);
    font-size: 28px;
    font-weight: 700;
  }

  .success {
    color: var(--color-success);
  }

  .warning {
    color: var(--color-warning);
  }

  .muted {
    color: var(--color-text-secondary);
  }
}

@media (max-width: 720px) {
  .dashboard-header {
    flex-direction: column;
  }

  .dashboard-actions {
    width: 100%;
    justify-content: space-between;
  }

  .stats-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
