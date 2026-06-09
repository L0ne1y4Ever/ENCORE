<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  downloadAdminBoxOfficeCsv,
  getAdminBoxOffice,
  getAdminShowCategories,
  getAdminShowOptions
} from '../../api/admin'
import type {
  AdminBoxOffice,
  AdminBoxOfficeQuery,
  AdminShowCategoryOption,
  AdminShowFilterOption
} from '../../api/admin'
import { downloadBlob } from '../../utils/csv'
import { formatMoney, toBaseAmount } from '../../utils/money'
import { adminCategoryLabel } from '../../utils/adminLabels'
import { formatLocaleDateTime, formatLocaleDay } from '../../utils/date'

const { t, locale } = useI18n()

const loading = ref(false)
const filterLoading = ref(false)
const rankingLoading = ref(false)
const exporting = ref(false)
const boxOffice = ref<AdminBoxOffice | null>(null)
const rankingBoxOffice = ref<AdminBoxOffice | null>(null)
const categories = ref<AdminShowCategoryOption[]>([])
const showOptions = ref<AdminShowFilterOption[]>([])
const range = ref<AdminBoxOfficeQuery['range']>('LAST_30_DAYS')
const selectedCategory = ref('')
const selectedShowId = ref('')
const rankingCategory = ref('Movie')
const showKeyword = ref('')
const customDates = ref<[string, string] | []>([])

const chartWidth = 920
const chartHeight = 280
const chartPadding = { top: 24, right: 28, bottom: 52, left: 78 }
const chartInnerWidth = chartWidth - chartPadding.left - chartPadding.right
const chartInnerHeight = chartHeight - chartPadding.top - chartPadding.bottom

const toNumber = toBaseAmount
const formatAmount = (value: number | string | undefined | null) => formatMoney(value, locale.value)
const formatPercent = (value: number | string | undefined | null) => `${toNumber(value).toFixed(1)}%`
const formatDate = (value?: string | null) => formatLocaleDateTime(value, locale.value)
const formatDay = (value: string) => formatLocaleDay(value, locale.value)

const queryParams = computed<AdminBoxOfficeQuery>(() => {
  const params: AdminBoxOfficeQuery = { range: range.value || 'LAST_30_DAYS' }
  if (selectedCategory.value) params.category = selectedCategory.value
  if (selectedShowId.value) params.showId = selectedShowId.value
  if (range.value === 'CUSTOM' && customDates.value.length === 2) {
    params.startDate = customDates.value[0]
    params.endDate = customDates.value[1]
  }
  return params
})

const rankingParams = computed<AdminBoxOfficeQuery>(() => {
  const params: AdminBoxOfficeQuery = {
    range: range.value || 'LAST_30_DAYS',
    category: rankingCategory.value
  }
  if (range.value === 'CUSTOM' && customDates.value.length === 2) {
    params.startDate = customDates.value[0]
    params.endDate = customDates.value[1]
  }
  return params
})

const summary = computed(() => boxOffice.value?.summary)
const hasSummaryActivity = (value?: AdminBoxOffice['summary'] | null) => {
  if (!value) return false
  return [
    value.salesRevenue,
    value.refundAmount,
    value.netRevenue,
    value.pendingAmount,
    value.validTickets,
    value.paidTickets,
    value.refundedTickets
  ].some(item => toNumber(item) > 0)
}
const globalSummary = computed(() => {
  const global = boxOffice.value?.globalSummary
  const current = boxOffice.value?.summary
  if (!global || (!hasSummaryActivity(global) && hasSummaryActivity(current))) {
    return current
  }
  return global
})
const trendRows = computed(() => boxOffice.value?.trends ?? [])
const categoryRows = computed(() => boxOffice.value?.categories ?? [])
const showRows = computed(() => boxOffice.value?.shows ?? [])
const scheduleRows = computed(() => boxOffice.value?.schedules ?? [])
const rankingRows = computed(() => rankingBoxOffice.value?.shows.slice(0, 10) ?? [])
const rankingSummary = computed(() => rankingBoxOffice.value?.summary)
const availableCategories = computed<AdminShowCategoryOption[]>(() => {
  if (categories.value.length) return categories.value

  if (categoryRows.value.length) {
    return categoryRows.value
      .filter(row => row.category)
      .map(row => ({
        category: row.category,
        showCount: row.showCount
      }))
  }

  const counts = new Map<string, number>()
  showRows.value.forEach(row => {
    const category = row.category
    if (!category) return
    counts.set(category, (counts.get(category) ?? 0) + 1)
  })
  return Array.from(counts.entries()).map(([category, showCount]) => ({ category, showCount }))
})
const rankingCategoryOptions = computed(() => availableCategories.value.filter(item => item.category))

const categoryLabel = (category?: string | null) => {
  return adminCategoryLabel(t, category)
}

const rankingCategoryLabel = computed(() => categoryLabel(rankingCategory.value))

const showStatusLabel = (status: AdminShowFilterOption['status']) => {
  const key = String(status || '').toLowerCase()
  return t(`admin.showStatus.${key}`)
}

const niceAxisMax = (value: number) => {
  if (value <= 0) return 0
  const exponent = Math.floor(Math.log10(value))
  const base = 10 ** exponent
  const scaled = value / base
  const nice = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10
  return nice * base
}

const peakNetRevenue = computed(() => Math.max(0, ...trendRows.value.map(item => Math.max(0, toNumber(item.netRevenue)))))
const maxTrendAmount = computed(() => Math.max(0, ...trendRows.value.map(item => Math.max(
  Math.max(0, toNumber(item.netRevenue)),
  Math.max(0, toNumber(item.refundAmount))
))))
const hasTrendActivity = computed(() => maxTrendAmount.value > 0)
const chartAxisMax = computed(() => niceAxisMax(maxTrendAmount.value))
const chartScaleY = (value: number) => {
  if (chartAxisMax.value <= 0) return chartPadding.top + chartInnerHeight
  const ratio = Math.max(0, Math.min(1, value / chartAxisMax.value))
  return chartPadding.top + (1 - ratio) * chartInnerHeight
}
const chartPoints = computed(() => {
  const rows = trendRows.value
  const step = rows.length > 1 ? chartInnerWidth / (rows.length - 1) : 0
  return rows.map((item, index) => {
    const net = Math.max(0, toNumber(item.netRevenue))
    const refunds = Math.max(0, toNumber(item.refundAmount))
    const x = rows.length > 1
      ? chartPadding.left + step * index
      : chartPadding.left + chartInnerWidth / 2
    return {
      x,
      y: chartScaleY(net),
      refundY: chartScaleY(refunds),
      label: formatDay(item.date),
      value: net,
      sales: toNumber(item.salesRevenue),
      refunds,
      index,
      hasActivity: net !== 0 || toNumber(item.salesRevenue) !== 0 || refunds !== 0
    }
  })
})
const buildLinePath = (points: { x: number; y: number }[]) => {
  if (!points.length) return ''
  return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`).join(' ')
}
const netLinePath = computed(() => buildLinePath(chartPoints.value.map(point => ({ x: point.x, y: point.y }))))
const refundLinePath = computed(() => buildLinePath(chartPoints.value.map(point => ({ x: point.x, y: point.refundY }))))
const hasRefundActivity = computed(() => chartPoints.value.some(point => point.refunds > 0))
const chartYAxisTicks = computed(() => [1, 0.75, 0.5, 0.25, 0].map(ratio => ({
  value: chartAxisMax.value * ratio,
  y: chartPadding.top + (1 - ratio) * chartInnerHeight
})))
const xAxisTickIndexes = computed(() => {
  const length = chartPoints.value.length
  if (!length) return []
  if (length <= 8) return Array.from({ length }, (_, index) => index)

  const tickCount = length <= 16 ? 5 : 6
  return Array.from({ length: tickCount }, (_, index) => {
    return Math.round((index / Math.max(1, tickCount - 1)) * (length - 1))
  }).filter((index, position, indexes) => indexes.indexOf(index) === position)
})
const xAxisTicks = computed(() => xAxisTickIndexes.value.map(index => chartPoints.value[index]).filter(Boolean))
const activeChartPoints = computed(() => chartPoints.value.filter(point => point.hasActivity))

const financeOverviewKpis = computed(() => [
  { label: t('admin.globalRefundAmount'), value: formatAmount(globalSummary.value?.refundAmount), tone: 'refund' },
  { label: t('admin.globalNetRevenue'), value: formatAmount(globalSummary.value?.netRevenue), tone: 'net' },
  { label: t('admin.globalValidTickets'), value: String(globalSummary.value?.validTickets ?? 0), tone: 'tickets' },
  { label: t('admin.pendingAmount'), value: formatAmount(summary.value?.pendingAmount), tone: 'pending' },
  { label: t('admin.attendanceRate'), value: formatPercent(summary.value?.attendanceRate), tone: 'attendance' }
])

const rangeLabel = computed(() => {
  if (range.value === 'LAST_7_DAYS') return t('admin.last7Days')
  if (range.value === 'LAST_30_DAYS') return t('admin.last30Days')
  if (range.value === 'ALL') return t('admin.allTime')
  if (customDates.value.length === 2) return `${customDates.value[0]} - ${customDates.value[1]}`
  return t('admin.customRange')
})

const selectedShowLabel = computed(() => {
  if (!selectedShowId.value) return ''
  const option = showOptions.value.find(show => show.id === selectedShowId.value)
  if (option) return option.title
  const row = showRows.value.find(show => show.showId === selectedShowId.value)
  return row?.showTitle || selectedShowId.value
})

const filterContextParts = computed(() => [
  rangeLabel.value,
  selectedCategory.value ? categoryLabel(selectedCategory.value) : t('admin.allCategories'),
  selectedShowLabel.value
].filter(Boolean))

const filterContextText = computed(() => filterContextParts.value.join(' · '))

const filteredContextMetrics = computed(() => [
  { label: t('admin.netRevenue'), value: formatAmount(summary.value?.netRevenue) },
  { label: t('admin.pendingAmount'), value: formatAmount(summary.value?.pendingAmount) },
  { label: t('admin.validTickets'), value: String(summary.value?.validTickets ?? 0) }
])

const ensureRankingCategory = () => {
  const options = rankingCategoryOptions.value
  if (!options.length) return
  if (options.some(item => item.category === rankingCategory.value)) return
  rankingCategory.value = options.find(item => item.category === 'Movie')?.category ?? options[0].category
}

const loadRanking = async () => {
  if (!rankingCategory.value) {
    rankingBoxOffice.value = null
    return
  }
  if (range.value === 'CUSTOM' && customDates.value.length !== 2) {
    ElMessage.warning(t('admin.financeDateRequired'))
    return
  }
  rankingLoading.value = true
  try {
    rankingBoxOffice.value = await getAdminBoxOffice(rankingParams.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    rankingLoading.value = false
  }
}

const loadFinance = async () => {
  if (range.value === 'CUSTOM' && customDates.value.length !== 2) {
    ElMessage.warning(t('admin.financeDateRequired'))
    return
  }
  loading.value = true
  try {
    const [financeData, rankingData] = await Promise.all([
      getAdminBoxOffice(queryParams.value),
      getAdminBoxOffice(rankingParams.value)
    ])
    boxOffice.value = financeData
    rankingBoxOffice.value = rankingData
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categories.value = await getAdminShowCategories()
    ensureRankingCategory()
  } catch {
    categories.value = []
  }
}

const loadShowOptions = async (keyword = showKeyword.value) => {
  showKeyword.value = keyword
  if (!selectedCategory.value) {
    showOptions.value = []
    return
  }
  filterLoading.value = true
  try {
    showOptions.value = await getAdminShowOptions({
      category: selectedCategory.value,
      keyword,
      limit: 30
    })
  } catch {
    const normalizedKeyword = keyword.trim().toLowerCase()
    showOptions.value = showRows.value
      .filter(show => show.category === selectedCategory.value)
      .filter(show => !normalizedKeyword || show.showTitle.toLowerCase().includes(normalizedKeyword))
      .slice(0, 30)
      .map(show => ({
        id: show.showId,
        title: show.showTitle,
        subtitle: null,
        category: show.category,
        status: 'PUBLISHED',
        scheduleCount: show.scheduleCount
      }))
  } finally {
    filterLoading.value = false
  }
}

const handleRangeChange = () => {
  if (range.value !== 'CUSTOM') {
    customDates.value = []
    void loadFinance()
  }
}

const handleDateChange = () => {
  if (customDates.value.length === 2) {
    range.value = 'CUSTOM'
    void loadFinance()
  }
}

const handleCategoryChange = async () => {
  selectedShowId.value = ''
  showKeyword.value = ''
  await loadShowOptions('')
  await loadFinance()
}

const handleShowChange = () => {
  void loadFinance()
}

const handleRankingCategoryChange = (category: string) => {
  if (rankingCategory.value === category) return
  rankingCategory.value = category
  void loadRanking()
}

const handleShowVisibleChange = (open: boolean) => {
  if (open) {
    void loadShowOptions(showKeyword.value)
  }
}

const exportFinance = async () => {
  if (range.value === 'CUSTOM' && customDates.value.length !== 2) {
    ElMessage.warning(t('admin.financeDateRequired'))
    return
  }
  exporting.value = true
  try {
    const file = await downloadAdminBoxOfficeCsv(queryParams.value)
    downloadBlob(file.blob, file.filename)
    ElMessage.success(t('admin.exportDone'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.exportFailed'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await loadCategories()
  await loadFinance()
})
</script>

<template>
  <div class="finance-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.finance') }}</h1>
        <p>{{ t('admin.financeSubtitle') }}</p>
      </div>
      <div class="header-actions" aria-label="finance filters">
        <div class="filter-row primary-filters">
          <el-select v-model="range" class="range-filter" @change="handleRangeChange">
            <el-option value="LAST_7_DAYS" :label="t('admin.last7Days')" />
            <el-option value="LAST_30_DAYS" :label="t('admin.last30Days')" />
            <el-option value="ALL" :label="t('admin.allTime')" />
            <el-option value="CUSTOM" :label="t('admin.customRange')" />
          </el-select>
          <el-select
            v-model="selectedCategory"
            clearable
            class="category-filter"
            :placeholder="t('admin.allCategories')"
            @change="handleCategoryChange"
          >
            <el-option
              v-for="item in availableCategories"
              :key="item.category"
              :value="item.category"
              :label="`${categoryLabel(item.category)} (${item.showCount})`"
            />
          </el-select>
          <el-date-picker
            v-model="customDates"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('admin.startDate')"
            :end-placeholder="t('admin.endDate')"
            class="date-filter"
            @change="handleDateChange"
          />
        </div>

        <div class="filter-row secondary-filters">
          <el-select
            v-model="selectedShowId"
            clearable
            filterable
            remote
            class="show-filter"
            popper-class="finance-show-select-popper"
            :disabled="!selectedCategory"
            :loading="filterLoading"
            :placeholder="selectedCategory ? t('admin.showSearchPlaceholder') : t('admin.selectCategoryFirst')"
            :remote-method="loadShowOptions"
            :no-match-text="t('admin.empty')"
            :no-data-text="selectedCategory ? t('admin.empty') : t('admin.selectCategoryFirst')"
            @visible-change="handleShowVisibleChange"
            @change="handleShowChange"
          >
            <el-option
              v-for="show in showOptions"
              :key="show.id"
              :value="show.id"
              :label="show.title"
            >
              <div class="finance-show-option">
                <strong>{{ show.title }}</strong>
                <span>{{ categoryLabel(show.category) }} · {{ show.scheduleCount }} {{ t('admin.scheduleCount') }} · {{ showStatusLabel(show.status) }}</span>
              </div>
            </el-option>
            <template #footer v-if="selectedCategory">
              <div class="finance-show-select-footer">
                {{ t('admin.showOptionsFooter') }}
              </div>
            </template>
          </el-select>
          <div class="filter-actions">
            <el-button plain :loading="exporting" @click="exportFinance">{{ t('admin.exportFinance') }}</el-button>
            <el-button type="primary" plain :loading="loading" @click="loadFinance">{{ t('admin.refresh') }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <section class="finance-summary">
      <div class="summary-title">
        <span>{{ t('admin.globalBoxOffice') }}</span>
        <strong>{{ formatAmount(globalSummary?.salesRevenue) }}</strong>
        <p>{{ t('admin.globalBoxOfficeSubtitle') }}</p>
      </div>
      <div class="summary-body">
        <div class="summary-metrics">
          <div v-for="item in financeOverviewKpis" :key="item.label" class="summary-metric" :class="item.tone">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="filter-context">
          <div>
            <span>{{ t('admin.filteredBoxOffice') }}</span>
            <strong>{{ filterContextText }}</strong>
          </div>
          <div v-for="item in filteredContextMetrics" :key="item.label" class="filter-context-metric">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="finance-panel compact-panel">
      <div class="panel-head">
        <div>
          <h2>{{ t('admin.categoryBoxOffice') }}</h2>
          <p>{{ t('admin.categoryBoxOfficeSubtitle') }}</p>
        </div>
      </div>
      <el-table :data="categoryRows" style="width: 100%" :empty-text="t('admin.empty')">
        <el-table-column :label="t('admin.category')" min-width="150">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.netRevenue')" width="130">
          <template #default="{ row }">{{ formatAmount(row.netRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.salesRevenue')" width="130">
          <template #default="{ row }">{{ formatAmount(row.salesRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.refundAmount')" width="120">
          <template #default="{ row }">{{ formatAmount(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.revenueShowCount')" width="120">
          <template #default="{ row }">{{ row.showCount }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.revenueScheduleCount')" width="120">
          <template #default="{ row }">{{ row.scheduleCount }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.attendanceRate')" width="110">
          <template #default="{ row }">{{ formatPercent(row.attendanceRate) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="finance-panel ranking-panel" v-loading="rankingLoading">
      <div class="panel-head">
        <div>
          <h2>{{ t('admin.categoryBoxOfficeRanking', { category: rankingCategoryLabel }) }}</h2>
          <p>{{ t('admin.categoryBoxOfficeRankingSubtitle') }}</p>
        </div>
        <div class="ranking-tools">
          <div class="ranking-tabs" :aria-label="t('admin.rankingCategorySwitch')">
            <button
              v-for="item in rankingCategoryOptions"
              :key="item.category"
              type="button"
              :class="{ active: rankingCategory === item.category }"
              @click="handleRankingCategoryChange(item.category)"
            >
              {{ categoryLabel(item.category) }}
            </button>
          </div>
          <div class="ranking-summary">
            <span>{{ rangeLabel }}</span>
            <strong>{{ formatAmount(rankingSummary?.netRevenue) }}</strong>
          </div>
        </div>
      </div>
      <el-table :data="rankingRows" style="width: 100%" :empty-text="t('admin.empty')">
        <el-table-column :label="t('admin.rank')" width="72" align="center">
          <template #default="{ $index }">
            <span class="rank-badge" :class="{ podium: $index < 3 }">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="showTitle" :label="rankingCategoryLabel" min-width="220" />
        <el-table-column :label="t('admin.netRevenue')" width="140">
          <template #default="{ row }">{{ formatAmount(row.netRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.salesRevenue')" width="140">
          <template #default="{ row }">{{ formatAmount(row.salesRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.refundAmount')" width="120">
          <template #default="{ row }">{{ formatAmount(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.validTickets')" width="110">
          <template #default="{ row }">{{ row.validTickets }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleCount')" width="110">
          <template #default="{ row }">{{ row.scheduleCount }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.attendanceRate')" width="110">
          <template #default="{ row }">{{ formatPercent(row.attendanceRate) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="finance-panel">
      <div class="panel-head">
        <div>
          <h2>{{ t('admin.netRevenueTrend') }}</h2>
          <p>{{ t('admin.financePolicyHint') }}</p>
        </div>
        <span>{{ trendRows.length }} {{ t('admin.days') }}</span>
      </div>
      <div v-if="trendRows.length" class="finance-chart">
        <svg class="chart-svg" :viewBox="`0 0 ${chartWidth} ${chartHeight}`" role="img" :aria-label="t('admin.netRevenueTrend')">
          <g v-for="tick in chartYAxisTicks" :key="`y-${tick.value}`">
            <line class="chart-grid-line" :x1="chartPadding.left" :x2="chartWidth - chartPadding.right" :y1="tick.y" :y2="tick.y" />
            <text class="axis-label" :x="chartPadding.left - 12" :y="tick.y + 4" text-anchor="end">{{ formatAmount(tick.value) }}</text>
          </g>
          <line class="chart-axis-line" :x1="chartPadding.left" :x2="chartWidth - chartPadding.right" :y1="chartPadding.top + chartInnerHeight" :y2="chartPadding.top + chartInnerHeight" />
          <g v-for="tick in xAxisTicks" :key="`tick-${tick.label}-${tick.index}`">
            <line class="chart-tick-line" :x1="tick.x" :x2="tick.x" :y1="chartPadding.top + chartInnerHeight" :y2="chartPadding.top + chartInnerHeight + 6" />
            <text class="date-label" :x="tick.x" :y="chartHeight - 16" text-anchor="middle">{{ tick.label }}</text>
          </g>
          <path v-if="hasRefundActivity" class="refund-line" :d="refundLinePath" />
          <path class="net-line" :class="{ quiet: !hasTrendActivity }" :d="netLinePath" />
          <g v-for="point in chartPoints" :key="`${point.label}-${point.index}`" class="chart-point-group">
            <line
              v-if="point.hasActivity"
              class="chart-hover-line"
              :x1="point.x"
              :x2="point.x"
              :y1="chartPadding.top"
              :y2="chartPadding.top + chartInnerHeight"
            />
            <circle
              class="chart-point"
              :class="{ quiet: !point.hasActivity }"
              :cx="point.x"
              :cy="point.y"
              :r="point.hasActivity ? 4 : 2"
            >
              <title>{{ point.label }} · {{ t('admin.netRevenue') }} {{ formatAmount(point.value) }} · {{ t('admin.salesRevenue') }} {{ formatAmount(point.sales) }} · {{ t('admin.refundAmount') }} {{ formatAmount(point.refunds) }}</title>
            </circle>
            <circle
              v-if="point.refunds > 0"
              class="refund-point"
              :cx="point.x"
              :cy="point.refundY"
              r="3"
            />
          </g>
        </svg>
        <div class="chart-summary">
          <span>{{ t('admin.activeRevenueDays') }} <strong>{{ activeChartPoints.length }}</strong></span>
          <span>{{ t('admin.peakNetRevenue') }} <strong>{{ formatAmount(peakNetRevenue) }}</strong></span>
          <span class="refund-legend">{{ t('admin.refundDaysMarked') }}</span>
        </div>
      </div>
      <div v-else class="empty-chart">{{ t('admin.noDashboardData') }}</div>
    </section>

    <section class="finance-panel">
      <div class="panel-head">
        <div>
          <h2>{{ t('admin.showBoxOffice') }}</h2>
          <p>{{ t('admin.showBoxOfficeSubtitle') }}</p>
        </div>
      </div>
      <el-table :data="showRows" style="width: 100%" :empty-text="t('admin.empty')">
        <el-table-column prop="showTitle" :label="t('admin.shows')" min-width="220" />
        <el-table-column :label="t('admin.category')" width="120">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.netRevenue')" width="130">
          <template #default="{ row }">{{ formatAmount(row.netRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.salesRevenue')" width="130">
          <template #default="{ row }">{{ formatAmount(row.salesRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.refundAmount')" width="120">
          <template #default="{ row }">{{ formatAmount(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.validTickets')" width="110">
          <template #default="{ row }">{{ row.validTickets }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.attendanceRate')" width="110">
          <template #default="{ row }">{{ formatPercent(row.attendanceRate) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="finance-panel">
      <div class="panel-head">
        <div>
          <h2>{{ t('admin.scheduleBoxOffice') }}</h2>
          <p>{{ t('admin.scheduleBoxOfficeSubtitle') }}</p>
        </div>
      </div>
      <el-table :data="scheduleRows" style="width: 100%" :empty-text="t('admin.empty')">
        <el-table-column prop="showTitle" :label="t('admin.shows')" min-width="200" />
        <el-table-column prop="theaterName" :label="t('admin.theater')" width="150" />
        <el-table-column :label="t('admin.startTime')" width="180">
          <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.netRevenue')" width="130">
          <template #default="{ row }">{{ formatAmount(row.netRevenue) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.pendingAmount')" width="130">
          <template #default="{ row }">{{ formatAmount(row.pendingAmount) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.validTickets')" width="110">
          <template #default="{ row }">{{ row.validTickets }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.checkedInTickets')" width="110">
          <template #default="{ row }">{{ row.checkedInTickets }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped lang="scss">
.finance-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-5);
}

.page-header {
  display: grid;
  grid-template-columns: 1fr;
  align-items: start;
  gap: var(--spacing-3);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
    line-height: 1.2;
  }

  p {
    margin-top: var(--spacing-2);
    color: var(--color-text-secondary);
    font-size: 14px;
  }

  > div:first-child {
    max-width: 720px;
  }
}

.header-actions {
  display: grid;
  gap: 12px;
  width: 100%;
  min-width: 0;
  max-width: 1180px;
  padding: 12px 0 0;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 12px;

  :deep(.el-button) {
    min-width: 88px;
    margin-left: 0;
  }
}

.filter-row {
  display: grid;
  align-items: center;
  gap: 10px;
  min-width: 0;
  width: 100%;
}

.primary-filters {
  grid-template-columns: 160px 190px minmax(360px, 520px);
}

.secondary-filters {
  grid-template-columns: minmax(320px, 520px) max-content;
  justify-content: start;
}

.filter-actions {
  display: grid;
  grid-template-columns: repeat(2, max-content);
  align-items: center;
  justify-content: start;
  gap: 8px;

  :deep(.el-button) {
    min-width: 108px;
    height: 40px;
    border-radius: var(--radius-sm);
  }
}

.range-filter,
.category-filter {
  width: 100%;
}

.date-filter {
  width: 100%;
  max-width: 100%;
}

.show-filter {
  justify-self: stretch;
  width: auto;
  min-width: 0;
}

.date-filter :deep(.el-range-input) {
  min-width: 104px;
}

.date-filter :deep(.el-range-separator) {
  flex: 0 0 24px;
  padding: 0;
}

.show-filter {
  :deep(.el-input__wrapper) {
    min-height: 40px;
  }
}

.finance-summary {
  display: grid;
  grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
  gap: var(--spacing-5);
  align-items: center;
  padding: 22px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.summary-title {
  display: grid;
  gap: 8px;
  min-width: 0;

  span {
    color: var(--color-text-secondary);
    font-size: 13px;
  }

  strong {
    color: var(--color-text-primary);
    font-family: var(--font-family-display);
    font-size: 34px;
    line-height: 1;
    word-break: break-word;
  }

  p {
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 1.5;
  }
}

.summary-body {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(96px, 1fr));
  gap: 18px;
}

.summary-metric {
  display: grid;
  gap: 7px;
  min-width: 0;

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    color: var(--color-text-primary);
    font-size: 22px;
    line-height: 1;
    font-weight: 700;
    white-space: nowrap;
  }

  &.refund strong {
    color: #d58a80;
  }

  &.net strong {
    color: #d9a25f;
  }
}

.filter-context {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 18px;
  min-width: 0;
  padding-top: 13px;
  border-top: 1px solid rgba(240, 237, 232, 0.08);

  > div:first-child {
    display: flex;
    align-items: baseline;
    gap: 8px;
    min-width: min(100%, 240px);
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    color: var(--color-text-primary);
    font-size: 13px;
    font-weight: 700;
  }
}

.filter-context-metric {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;

  span {
    color: rgba(240, 237, 232, 0.44);
  }

  strong {
    color: var(--color-text-secondary);
    white-space: nowrap;
  }
}

.finance-panel {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-5);
  min-width: 0;

  :deep(.el-table) {
    background-color: transparent;
    --el-table-border-color: var(--color-border);
    --el-table-header-bg-color: rgba(255, 255, 255, 0.02);
    --el-table-header-text-color: var(--color-text-secondary);
    --el-table-text-color: var(--color-text-primary);
    --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
  }
}

.compact-panel {
  padding-block: var(--spacing-4);
}

.ranking-panel {
  :deep(.el-table__row:first-child .rank-badge.podium) {
    border-color: rgba(217, 162, 95, 0.62);
    background: rgba(217, 162, 95, 0.14);
    color: #e0b06f;
  }
}

.ranking-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  min-width: min(100%, 520px);
}

.ranking-tabs {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;

  button {
    height: 30px;
    border: 0;
    border-radius: 4px;
    padding: 0 9px;
    background: transparent;
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
    line-height: 1;
    cursor: pointer;
    transition: background-color 150ms ease, color 150ms ease;

    &:hover {
      background: rgba(240, 237, 232, 0.06);
      color: var(--color-text-primary);
    }

    &.active {
      background: rgba(217, 162, 95, 0.12);
      color: #e0b06f;
    }

    &:focus-visible {
      outline: 1px solid rgba(217, 162, 95, 0.55);
      outline-offset: 2px;
    }
  }
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);

  h2 {
    font-size: 17px;
    font-weight: 700;
  }

  p,
  span {
    margin-top: 6px;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.ranking-summary {
  display: grid;
  justify-items: end;
  gap: 5px;
  flex: 0 0 auto;
  min-width: 120px;

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    color: var(--color-text-primary);
    font-size: 18px;
    line-height: 1;
  }
}

.rank-badge {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid rgba(240, 237, 232, 0.12);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.035);
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 700;

  &.podium {
    border-color: rgba(240, 237, 232, 0.2);
    color: var(--color-text-primary);
  }
}

.finance-chart {
  width: 100%;
  min-height: 300px;
  overflow: hidden;
  border: 1px solid rgba(240, 237, 232, 0.06);
  border-radius: var(--radius-sm);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0.01)),
    rgba(0, 0, 0, 0.18);
}

.chart-svg {
  width: 100%;
  height: 300px;
  display: block;
  overflow: hidden;
  user-select: none;
}

.chart-grid-line {
  stroke: rgba(240, 237, 232, 0.075);
}

.chart-axis-line,
.chart-tick-line {
  stroke: rgba(240, 237, 232, 0.16);
  stroke-linecap: round;
}

.axis-label,
.date-label {
  fill: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 500;
  pointer-events: none;
}

.net-line {
  fill: none;
  stroke: rgba(217, 162, 95, 0.92);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;

  &.quiet {
    stroke: rgba(240, 237, 232, 0.2);
    stroke-width: 2;
  }
}

.refund-line {
  fill: none;
  stroke: rgba(213, 138, 128, 0.82);
  stroke-dasharray: 6 7;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.chart-hover-line {
  opacity: 0;
  stroke: rgba(240, 237, 232, 0.12);
  stroke-dasharray: 4 5;
  transition: opacity 140ms ease;
}

.chart-point {
  fill: rgba(217, 162, 95, 0.96);
  stroke: #11100f;
  stroke-width: 2;
  transition: fill 140ms ease, r 140ms ease;

  &.quiet {
    fill: rgba(240, 237, 232, 0.22);
    stroke-width: 0;
  }
}

.chart-point-group:hover {
  .chart-hover-line {
    opacity: 1;
  }

  .chart-point {
    fill: rgba(233, 184, 118, 1);
  }
}

.refund-point {
  fill: rgba(213, 138, 128, 0.95);
  stroke: #11100f;
  stroke-width: 1.5;
}

.chart-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  padding: 0 18px 14px;
  color: var(--color-text-secondary);
  font-size: 12px;

  strong {
    color: var(--color-text-primary);
    font-size: 13px;
    font-weight: 700;
  }
}

.refund-legend {
  position: relative;
  padding-left: 18px;

  &::before {
    position: absolute;
    top: 50%;
    left: 0;
    width: 10px;
    height: 0;
    border-top: 2px dashed rgba(213, 138, 128, 0.9);
    content: '';
    transform: translateY(-50%);
  }
}

.empty-chart {
  min-height: 220px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
}

:global(.finance-show-select-popper) {
  width: min(520px, calc(100vw - 32px)) !important;

  .el-select-dropdown__wrap {
    max-height: 320px;
  }

  .el-select-dropdown__item {
    height: auto;
    min-height: 58px;
    padding: 9px 14px;
    line-height: 1.25;
  }
}

:global(.finance-show-option) {
  display: grid;
  gap: 5px;
  min-width: 0;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--color-text-primary);
    font-size: 14px;
    font-weight: 700;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

:global(.finance-show-select-footer) {
  padding: 8px 14px 10px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .primary-filters,
  .secondary-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .date-filter {
    grid-column: 1 / -1;
  }

  .filter-actions {
    width: 100%;
    justify-content: flex-start;

    :deep(.el-button) {
      width: 100%;
    }
  }
}

@media (max-width: 980px) {
  .finance-summary {
    grid-template-columns: 1fr;
    gap: var(--spacing-4);
  }

  .summary-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .filter-row,
  .filter-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .range-filter,
  .category-filter,
  .date-filter,
  .show-filter,
  .filter-actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
  }

  .summary-title strong {
    font-size: 32px;
  }

  .summary-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .filter-context {
    display: grid;
    gap: 8px;
  }

  .filter-context > div:first-child {
    display: grid;
    gap: 4px;
  }

  .ranking-tools {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }

  .ranking-tabs {
    justify-content: flex-start;
  }

  .ranking-summary {
    justify-items: start;
  }
}
</style>
