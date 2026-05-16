<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAdminDashboard } from '../../api/admin'
import type { AdminDashboard } from '../../api/admin'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent
])

const { t } = useI18n()

const dashboard = ref<AdminDashboard | null>(null)
const loading = ref(false)

const loadDashboard = async () => {
  loading.value = true
  try {
    dashboard.value = await getAdminDashboard()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)

const formatAmount = (amount: number | string | undefined) => {
  return `$${Number(amount || 0).toFixed(2)}`
}

const formatPercent = (value: number | string | undefined) => {
  return `${Number(value || 0).toFixed(1)}%`
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

const lineOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: {
    trigger: 'axis',
    formatter: (params: Array<{ axisValue: string; value: number; seriesName: string }>) => {
      const revenue = params.find(item => item.seriesName === t('admin.revenue'))
      const tickets = params.find(item => item.seriesName === t('admin.tickets'))
      return [
        `<strong>${revenue?.axisValue || ''}</strong>`,
        `${t('admin.revenue')}: ${formatAmount(revenue?.value || 0)}`,
        `${t('admin.tickets')}: ${tickets?.value || 0}`
      ].join('<br/>')
    }
  },
  legend: {
    top: 0,
    right: 0,
    textStyle: { color: '#8A8480' }
  },
  grid: { top: 48, right: 20, bottom: 24, left: 52 },
  xAxis: {
    type: 'category',
    data: trendRows.value.map(item => formatDateLabel(item.date)),
    axisLine: { lineStyle: { color: '#8A8480' } },
    axisLabel: { color: '#8A8480' }
  },
  yAxis: [
    {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(240, 237, 232, 0.08)' } },
      axisLabel: { color: '#8A8480' }
    },
    {
      type: 'value',
      splitLine: { show: false },
      axisLabel: { color: '#8A8480' }
    }
  ],
  series: [
    {
      name: t('admin.revenue'),
      data: trendRows.value.map(item => Number(item.revenue)),
      type: 'line',
      smooth: true,
      lineStyle: { color: '#C8955A', width: 2 },
      itemStyle: { color: '#C8955A' }
    },
    {
      name: t('admin.tickets'),
      data: trendRows.value.map(item => item.ticketCount),
      type: 'bar',
      yAxisIndex: 1,
      barWidth: 12,
      itemStyle: { color: 'rgba(93, 160, 140, 0.75)', borderRadius: [3, 3, 0, 0] }
    }
  ]
}))

const topShowsOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params: Array<{ dataIndex: number; value: number }>) => {
      const item = topShows.value[params[0]?.dataIndex || 0]
      if (!item) return ''
      return [
        `<strong>${item.showTitle}</strong>`,
        `${t('admin.tickets')}: ${item.ticketCount}`,
        `${t('admin.revenue')}: ${formatAmount(item.revenue)}`
      ].join('<br/>')
    }
  },
  grid: { top: 16, right: 20, bottom: 24, left: 48 },
  xAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: 'rgba(240, 237, 232, 0.08)' } },
    axisLabel: { color: '#8A8480' }
  },
  yAxis: {
    type: 'category',
    data: topShows.value.map(item => item.showTitle),
    axisLine: { lineStyle: { color: '#8A8480' } },
    axisLabel: {
      color: '#8A8480',
      width: 120,
      overflow: 'truncate'
    }
  },
  series: [
    {
      data: topShows.value.map(item => item.ticketCount),
      type: 'bar',
      barWidth: 14,
      itemStyle: { color: '#C8955A', borderRadius: [0, 4, 4, 0] }
    }
  ]
}))
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <div class="dashboard-header">
      <div>
        <h1>{{ t('admin.dashboard') }}</h1>
        <p>{{ t('admin.dashboardSubtitle') }}</p>
      </div>
      <el-button type="primary" plain :loading="loading" @click="loadDashboard">
        {{ t('admin.refresh') }}
      </el-button>
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
          <v-chart v-if="trendRows.length" class="chart" :option="lineOption" autoresize />
          <div v-else class="empty-chart">{{ t('admin.noDashboardData') }}</div>
        </div>
      </div>

      <div class="chart-card">
        <h3>{{ t('admin.topShows') }}</h3>
        <div class="chart-container">
          <v-chart v-if="topShows.length" class="chart" :option="topShowsOption" autoresize />
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
    height: 320px;
    width: 100%;

    .chart {
      width: 100%;
      height: 100%;
    }
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

  .stats-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
