<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { downloadAdminDashboardCsv, getAdminDashboard } from '../../api/admin'
import type { AdminDashboard } from '../../api/admin'
import { subscribeToDashboardUpdates } from '../../api/adminRealtime'
import type { RealtimeConnectionState } from '../../api/realtime'
import { downloadBlob } from '../../utils/csv'
import { formatMoney, toBaseAmount } from '../../utils/money'

const { t, locale } = useI18n()

const dashboard = ref<AdminDashboard | null>(null)
const loading = ref(false)
const exporting = ref(false)
const realtimeState = ref<RealtimeConnectionState>('connecting')
const realtimeRefreshing = ref(false)
const realtimeNotice = ref<string | null>(null)
let disconnectDashboardRealtime: (() => void) | undefined
let realtimeNoticeTimer: ReturnType<typeof setTimeout> | undefined
let realtimeRefreshTimer: ReturnType<typeof setTimeout> | undefined

const toNumber = toBaseAmount

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
  return formatMoney(amount, locale.value)
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

const financeSummary = computed(() => dashboard.value?.financeSummary)
const financeStats = computed(() => [
  { label: t('admin.salesRevenue'), value: formatAmount(financeSummary.value?.salesRevenue) },
  { label: t('admin.refundAmount'), value: formatAmount(financeSummary.value?.refundAmount) },
  { label: t('admin.netRevenue'), value: formatAmount(financeSummary.value?.netRevenue) },
  { label: t('admin.pendingAmount'), value: formatAmount(financeSummary.value?.pendingAmount) }
])

const trendRows = computed(() => dashboard.value?.salesTrend ?? [])
const topShows = computed(() => dashboard.value?.topShows ?? [])
const checkInSummary = computed(() => dashboard.value?.checkInSummary ?? { checkedIn: 0, unused: 0, voided: 0 })

const trendChartWidth = 700
const trendChartHeight = 230
const trendPadding = {
  top: 18,
  right: 18,
  bottom: 34,
  left: 42
}
const trendInnerWidth = trendChartWidth - trendPadding.left - trendPadding.right
const trendInnerHeight = trendChartHeight - trendPadding.top - trendPadding.bottom

const trendMaxTickets = computed(() => Math.max(1, ...trendRows.value.map(item => toNumber(item.ticketCount))))
const topShowsMaxTickets = computed(() => Math.max(1, ...topShows.value.map(item => toNumber(item.ticketCount))))

const trendHasSignal = computed(() => {
  return trendRows.value.some(item => toNumber(item.revenue) > 0 || toNumber(item.ticketCount) > 0)
})

const trendPoints = computed(() => {
  const rows = trendRows.value
  const lastIndex = Math.max(1, rows.length - 1)
  return rows.map((item, index) => {
    const tickets = toNumber(item.ticketCount)
    const x = rows.length === 1
      ? trendPadding.left + trendInnerWidth / 2
      : trendPadding.left + (index / lastIndex) * trendInnerWidth
    const y = trendPadding.top + (1 - tickets / trendMaxTickets.value) * trendInnerHeight
    return {
      x,
      y,
      valueLabelY: Math.max(14, y - 12),
      tickets,
      revenue: toNumber(item.revenue),
      dateLabel: formatDateLabel(item.date)
    }
  })
})

const trendGridLines = computed(() => [0, 0.25, 0.5, 0.75, 1].map(ratio => trendPadding.top + ratio * trendInnerHeight))
const trendLinePath = computed(() => {
  return trendPoints.value
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
    .join(' ')
})
const trendAreaPath = computed(() => {
  const points = trendPoints.value
  if (!points.length) return ''
  const baseY = trendPadding.top + trendInnerHeight
  return `${trendLinePath.value} L ${points[points.length - 1].x.toFixed(2)} ${baseY} L ${points[0].x.toFixed(2)} ${baseY} Z`
})

const ratioPercent = (value: number | string | undefined, max: number, minimum = 4) => {
  const ratio = Math.round((toNumber(value) / Math.max(1, max)) * 100)
  return `${Math.max(minimum, Math.min(100, ratio))}%`
}

const topShowBarWidth = (value: number | string | undefined) => ratioPercent(value, topShowsMaxTickets.value, 6)

const exportDashboard = async () => {
  exporting.value = true
  try {
    const file = await downloadAdminDashboardCsv()
    downloadBlob(file.blob, file.filename)
    ElMessage.success(t('admin.exportDone'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.exportFailed'))
  } finally {
    exporting.value = false
  }
}
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
        <el-button plain :loading="exporting" :disabled="!dashboard" @click="exportDashboard">
          {{ t('admin.exportDashboard') }}
        </el-button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-label">{{ stat.label }}</div>
        <div class="stat-value">{{ stat.value }}</div>
      </div>
    </div>

    <div class="finance-summary">
      <div class="summary-copy">
        <strong>{{ t('admin.financeSnapshot') }}</strong>
        <span>{{ t('admin.financePolicyHint') }}</span>
      </div>
      <div class="finance-summary-items">
        <div v-for="item in financeStats" :key="item.label" class="finance-summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </div>

    <div class="chart-grid">
      <div class="chart-card wide">
        <h3>{{ t('admin.salesTrend') }}</h3>
        <div class="chart-container">
          <div v-if="trendRows.length" class="trend-chart" :class="{ muted: !trendHasSignal }">
            <svg
              class="trend-line-chart"
              :viewBox="`0 0 ${trendChartWidth} ${trendChartHeight}`"
              role="img"
              :aria-label="t('admin.salesTrend')"
            >
              <line
                v-for="lineY in trendGridLines"
                :key="lineY"
                class="trend-grid-line"
                :x1="trendPadding.left"
                :x2="trendChartWidth - trendPadding.right"
                :y1="lineY"
                :y2="lineY"
              />
              <text class="trend-axis-label" :x="trendPadding.left - 10" :y="trendPadding.top + 4" text-anchor="end">
                {{ trendMaxTickets }}
              </text>
              <text
                class="trend-axis-label"
                :x="trendPadding.left - 10"
                :y="trendPadding.top + trendInnerHeight + 4"
                text-anchor="end"
              >
                0
              </text>
              <path class="trend-area" :d="trendAreaPath" />
              <path class="trend-line" :d="trendLinePath" />
              <g v-for="point in trendPoints" :key="point.dateLabel">
                <line
                  v-if="point.tickets > 0"
                  class="trend-hit-line"
                  :x1="point.x"
                  :x2="point.x"
                  :y1="point.y"
                  :y2="trendPadding.top + trendInnerHeight"
                />
                <circle class="trend-point-ring" :cx="point.x" :cy="point.y" r="8" />
                <circle class="trend-point" :class="{ active: point.tickets > 0 }" :cx="point.x" :cy="point.y" r="4.5">
                  <title>
                    {{ point.dateLabel }} · {{ t('admin.tickets') }} {{ point.tickets }} · {{ t('admin.revenue') }} {{ formatAmount(point.revenue) }}
                  </title>
                </circle>
                <text
                  v-if="point.tickets > 0"
                  class="trend-point-value"
                  :x="point.x"
                  :y="point.valueLabelY"
                  text-anchor="middle"
                >
                  {{ point.tickets }}
                </text>
                <text class="trend-date-label" :x="point.x" :y="trendChartHeight - 8" text-anchor="middle">
                  {{ point.dateLabel }}
                </text>
              </g>
            </svg>
            <div class="chart-legend">
              <span><i class="legend-tickets"></i>{{ t('admin.tickets') }}</span>
              <span>{{ t('admin.revenue') }} {{ formatAmount(dashboard?.totalRevenue) }}</span>
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
  gap: 0;
  padding: 10px 0;
  border-top: 1px solid rgba(240, 237, 232, 0.08);
  border-bottom: 1px solid rgba(240, 237, 232, 0.08);
  background: linear-gradient(180deg, rgba(240, 237, 232, 0.025), rgba(240, 237, 232, 0.006));

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.chart-card,
.summary-card {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.stat-card {
  position: relative;
  min-height: 76px;
  padding: 12px 22px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-right: 1px solid rgba(240, 237, 232, 0.07);

  &:first-child {
    padding-left: 18px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 17px;
      bottom: 17px;
      width: 2px;
      border-radius: 2px;
      background: var(--color-accent);
    }
  }

  &:last-child {
    border-right: 0;
  }

  .stat-label {
    font-family: var(--font-family-sans);
    font-size: 12px;
    line-height: 1.2;
    color: var(--color-text-secondary);
    margin-bottom: 8px;
  }

  .stat-value {
    font-family: var(--font-family-display);
    font-size: clamp(24px, 2vw, 30px);
    font-weight: 650;
    color: var(--color-text-primary);
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
    letter-spacing: 0;
  }

  &:first-child .stat-value {
    color: #f0c078;
  }
}

.finance-summary {
  display: grid;
  grid-template-columns: minmax(180px, 0.7fr) minmax(0, 1.3fr);
  gap: var(--spacing-4);
  align-items: center;
  padding: var(--spacing-4) 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.summary-copy {
  display: grid;
  gap: 6px;

  strong {
    color: var(--color-text-primary);
    font-size: 15px;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.5;
  }
}

.finance-summary-items {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-4);
}

.finance-summary-item {
  display: grid;
  gap: 8px;

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    font-family: var(--font-family-display);
    font-size: 24px;
    line-height: 1;
    color: var(--color-text-primary);
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
  gap: var(--spacing-2);
}

.trend-chart.muted {
  opacity: 0.78;
}

.trend-line-chart {
  width: 100%;
  height: 230px;
  display: block;
  overflow: visible;
}

.trend-grid-line {
  stroke: rgba(240, 237, 232, 0.08);
  stroke-width: 1;
}

.trend-axis-label,
.trend-date-label {
  fill: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 12px;
}

.trend-area {
  fill: rgba(200, 149, 90, 0.12);
}

.trend-line {
  fill: none;
  stroke: #c8955a;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-hit-line {
  stroke: rgba(200, 149, 90, 0.26);
  stroke-width: 1;
  stroke-dasharray: 4 6;
}

.trend-point-ring {
  fill: rgba(8, 8, 8, 0.95);
  stroke: rgba(200, 149, 90, 0.28);
  stroke-width: 1;
}

.trend-point {
  fill: rgba(200, 149, 90, 0.86);
  stroke: #080808;
  stroke-width: 2;

  &.active {
    fill: #d9a25f;
  }
}

.trend-point-value {
  fill: var(--color-text-primary);
  font-family: var(--font-family-display);
  font-size: 16px;
  font-weight: 700;
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

.legend-tickets {
  background: #c8955a;
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
  .summary-grid,
  .finance-summary,
  .finance-summary-items {
    grid-template-columns: 1fr;
  }
}
</style>
