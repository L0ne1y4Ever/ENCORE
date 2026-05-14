<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent
])

const { t } = useI18n()

const stats = computed(() => [
  { label: t('admin.totalRevenue'), value: '$12,450' },
  { label: t('admin.ticketsSold'), value: '450' },
  { label: t('admin.activeShows'), value: '3' },
  { label: t('admin.avgAttendance'), value: '85%' }
])

const lineOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis' },
  grid: { top: 20, right: 20, bottom: 20, left: 40 },
  xAxis: {
    type: 'category',
    data: [
      t('admin.weekdays.mon'),
      t('admin.weekdays.tue'),
      t('admin.weekdays.wed'),
      t('admin.weekdays.thu'),
      t('admin.weekdays.fri'),
      t('admin.weekdays.sat'),
      t('admin.weekdays.sun')
    ],
    axisLine: { lineStyle: { color: '#8A8480' } }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: 'rgba(240, 237, 232, 0.08)' } },
    axisLabel: { color: '#8A8480' }
  },
  series: [
    {
      data: [120, 200, 150, 80, 70, 110, 130],
      type: 'line',
      smooth: true,
      lineStyle: { color: '#C8955A', width: 2 },
      itemStyle: { color: '#C8955A' }
    }
  ]
}))
</script>

<template>
  <div class="dashboard">
    <div class="stats-grid">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-label">{{ stat.label }}</div>
        <div class="stat-value">{{ stat.value }}</div>
      </div>
    </div>

    <div class="chart-section">
      <div class="chart-card">
        <h3>{{ t('admin.salesTrend') }}</h3>
        <div class="chart-container">
          <v-chart class="chart" :option="lineOption" autoresize />
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

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-4);
  
  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
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
    font-size: 36px;
    font-weight: 700;
    color: var(--color-text-primary);
  }
}

.chart-section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);

  .chart-card {
    background-color: var(--color-bg-elevated);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    padding: var(--spacing-5);

    h3 {
      font-family: var(--font-family-sans);
      font-size: 16px;
      font-weight: 500;
      color: var(--color-text-primary);
      margin-bottom: var(--spacing-4);
    }

    .chart-container {
      height: 300px;
      width: 100%;
      
      .chart {
        width: 100%;
        height: 100%;
      }
    }
  }
}
</style>
