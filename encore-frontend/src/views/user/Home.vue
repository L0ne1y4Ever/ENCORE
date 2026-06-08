<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowLeft, ArrowRight, Calendar, PriceTag, Search } from '@element-plus/icons-vue'
import { getShowList, getTopRecommendedShows } from '../../api/show'
import type { RecommendedShow } from '../../api/show'
import type { Show } from '../../mock/shows'
import { mockShows } from '../../mock/shows'
import { handlePosterError, lowestPriceLabel, posterImageSrc } from '../../utils/ticketing'

const router = useRouter()
const shows = ref<Show[]>([])
const recommendedShows = ref<RecommendedShow[]>([])
const { t } = useI18n()

type DateFilter = 'all' | 'onSale' | 'comingSoon'

const activeCategory = ref('All')
const dateFilter = ref<DateFilter>('all')
const searchKeyword = ref('')
const railRef = ref<HTMLElement | null>(null)
const searchPanelRef = ref<HTMLElement | null>(null)
const activeHeroIndex = ref(0)
const heroTimer = ref<ReturnType<typeof window.setInterval> | null>(null)
const openDropdown = ref<'category' | 'date' | ''>('')
const HERO_INTERVAL_MS = 6500

const normalizeShows = <T extends Show>(data: T[]) => {
  return data.map(s => {
    const mockMatch = mockShows.find(m => m.id === s.id)
    return { ...s, status: s.status || mockMatch?.status || 'ON_SALE' }
  })
}

const toFallbackRecommendations = (source: Show[]): RecommendedShow[] => {
  return source.slice(0, 8).map((show, index) => ({
    ...show,
    rank: index + 1,
    ticketsSold: 0,
    availableScheduleCount: show.status === 'ON_SALE' ? 1 : 0,
    availableTicketCount: 0,
    hotScore: 0
  }))
}

onMounted(async () => {
  document.addEventListener('pointerdown', handleGlobalPointerDown)
  window.addEventListener('keydown', handleGlobalKeydown)

  let resolvedShows: Show[] = []

  try {
    const data = await getShowList()
    resolvedShows = data && data.length > 0 ? normalizeShows(data) : mockShows
  } catch {
    resolvedShows = mockShows
  }

  shows.value = resolvedShows

  try {
    const data = await getTopRecommendedShows()
    recommendedShows.value = data && data.length > 0
      ? normalizeShows(data).map((show, index) => ({
        ...show,
        rank: show.rank || index + 1,
        ticketsSold: show.ticketsSold || 0,
        availableScheduleCount: show.availableScheduleCount || 0,
        availableTicketCount: show.availableTicketCount || 0,
        hotScore: show.hotScore || 0
      }))
      : toFallbackRecommendations(resolvedShows)
  } catch {
    recommendedShows.value = toFallbackRecommendations(resolvedShows)
  }

  startHeroAutoplay()
})

onUnmounted(() => {
  stopHeroAutoplay()
  document.removeEventListener('pointerdown', handleGlobalPointerDown)
  window.removeEventListener('keydown', handleGlobalKeydown)
})

const categories = ['All', 'Movie', 'Musical', 'Play', 'Concert', 'Ballet']

const categoryLabelMap: Record<string, string> = {
  All: 'home.all',
  Movie: 'home.movie',
  Musical: 'home.musical',
  Play: 'home.play',
  Concert: 'home.concert',
  Ballet: 'home.ballet'
}

const matchesDateFilter = (show: Show) => {
  if (dateFilter.value === 'onSale') return show.status !== 'COMING_SOON'
  if (dateFilter.value === 'comingSoon') return show.status === 'COMING_SOON'
  return true
}

const filteredShows = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return shows.value.filter(show => {
    if (activeCategory.value !== 'All' && show.category !== activeCategory.value) return false
    if (!matchesDateFilter(show)) return false
    if (keyword) {
      const haystack = [
        show.title,
        show.subtitle,
        show.category,
        show.tags.join(' ')
      ].join(' ').toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    return true
  })
})

const heroShows = computed(() => {
  const source = recommendedShows.value.length > 0 ? recommendedShows.value : shows.value
  const seen = new Set<string>()
  return source
    .filter(show => {
      if (!show?.id || seen.has(show.id)) return false
      seen.add(show.id)
      return true
    })
    .slice(0, 5)
})

const featuredShow = computed(() => {
  const items = heroShows.value
  if (!items.length) return undefined
  return items[activeHeroIndex.value % items.length]
})

const featuredMeta = computed(() => {
  const show = featuredShow.value
  if (!show) return []
  return [
    show.category,
    `${show.duration} ${t('detail.minutes')}`,
    ...(show.tags || []).slice(0, 2)
  ]
})

const topPicks = computed(() => recommendedShows.value.slice(0, 8))
const onSaleCount = computed(() => shows.value.filter(show => show.status !== 'COMING_SOON').length)
const comingSoonCount = computed(() => shows.value.filter(show => show.status === 'COMING_SOON').length)
const categoryOptions = computed(() => categories.map(value => ({
  value,
  label: t(categoryLabelMap[value])
})))
const dateFilterOptions = computed<Array<{ value: DateFilter; label: string }>>(() => [
  { value: 'all', label: t('home.allDates') },
  { value: 'onSale', label: `${t('home.onSaleNow')} (${onSaleCount.value})` },
  { value: 'comingSoon', label: `${t('home.comingSoonBadge')} (${comingSoonCount.value})` }
])
const activeCategoryLabel = computed(() => categoryOptions.value.find(option => option.value === activeCategory.value)?.label || '')
const activeDateFilterLabel = computed(() => dateFilterOptions.value.find(option => option.value === dateFilter.value)?.label || '')

const showPrice = (show: Show | RecommendedShow) => {
  const fromRange = lowestPriceLabel((show as Show & { priceRange?: string }).priceRange)
  return fromRange || t('home.pricePending')
}

const scheduleCountLabel = (show: Show | RecommendedShow) => {
  const count = Number((show as RecommendedShow).availableScheduleCount || 0)
  return `${count} ${t('home.onSaleSchedules')}`
}

const statusLabel = (show: Show) => {
  return show.status === 'COMING_SOON' ? t('home.comingSoonBadge') : t('home.onSaleNow')
}

const formatCount = (value: number) => {
  return new Intl.NumberFormat().format(value)
}

const goDetail = (id: string) => {
  router.push(`/show/${id}`)
}

const stopHeroAutoplay = () => {
  if (!heroTimer.value) return
  window.clearInterval(heroTimer.value)
  heroTimer.value = null
}

const startHeroAutoplay = () => {
  stopHeroAutoplay()
  if (heroShows.value.length <= 1) return
  heroTimer.value = window.setInterval(() => {
    const count = heroShows.value.length
    if (count <= 1) {
      stopHeroAutoplay()
      return
    }
    activeHeroIndex.value = (activeHeroIndex.value + 1) % count
  }, HERO_INTERVAL_MS)
}

const setActiveHero = (index: number) => {
  activeHeroIndex.value = index
  startHeroAutoplay()
}

const shiftHero = (direction: 'prev' | 'next') => {
  const count = heroShows.value.length
  if (!count) return
  activeHeroIndex.value = direction === 'next'
    ? (activeHeroIndex.value + 1) % count
    : (activeHeroIndex.value - 1 + count) % count
  startHeroAutoplay()
}

const scrollRail = (direction: 'prev' | 'next') => {
  const el = railRef.value
  if (!el) return
  const amount = Math.round(el.clientWidth * 0.8)
  el.scrollBy({ left: direction === 'next' ? amount : -amount, behavior: 'smooth' })
}

const toggleDropdown = (name: 'category' | 'date') => {
  openDropdown.value = openDropdown.value === name ? '' : name
}

const selectCategory = (value: string) => {
  activeCategory.value = value
  openDropdown.value = ''
}

const selectDateFilter = (value: DateFilter) => {
  dateFilter.value = value
  openDropdown.value = ''
}

const handleGlobalPointerDown = (event: PointerEvent) => {
  if (!searchPanelRef.value?.contains(event.target as Node)) {
    openDropdown.value = ''
  }
}

const handleGlobalKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    openDropdown.value = ''
  }
}
</script>

<template>
  <div class="home-page">
    <section
      v-if="featuredShow"
      class="hero-section"
      @mouseenter="stopHeroAutoplay"
      @mouseleave="startHeroAutoplay"
      @focusin="stopHeroAutoplay"
      @focusout="startHeroAutoplay"
    >
      <transition name="hero-fade" mode="out-in">
        <img
          :key="featuredShow.id"
          class="hero-image"
          :src="posterImageSrc(featuredShow.coverUrl, featuredShow.title)"
          :alt="featuredShow.title"
          @error="handlePosterError($event, featuredShow.title)"
        />
      </transition>
      <div class="hero-scrim" />
      <div class="hero-inner">
        <div class="hero-copy">
          <span class="eyebrow">{{ t('home.editorChoice') }}</span>
          <h1>{{ featuredShow.title }}</h1>
          <p>{{ featuredShow.subtitle || featuredShow.description }}</p>
          <div class="hero-meta">
            <span v-for="item in featuredMeta" :key="item">{{ item }}</span>
          </div>
          <div class="hero-actions">
            <button class="primary-cta" type="button" @click="goDetail(featuredShow.id)">
              {{ featuredShow.status === 'COMING_SOON' ? t('detail.reserve') : t('detail.book') }}
              <ArrowRight />
            </button>
            <button class="secondary-cta" type="button" @click="goDetail(featuredShow.id)">
              {{ t('home.viewDetails') }}
            </button>
          </div>
        </div>

        <div class="hero-ticket">
          <span>{{ t('home.ticketFrom') }}</span>
          <strong>{{ showPrice(featuredShow) }}</strong>
          <small>{{ scheduleCountLabel(featuredShow) }}</small>
        </div>
      </div>
      <div v-if="heroShows.length > 1" class="hero-carousel-ui">
        <button class="hero-nav" type="button" :aria-label="t('common.back')" @click="shiftHero('prev')">
          <ArrowLeft />
        </button>
        <div class="hero-thumbs" :aria-label="t('home.topRecommended')">
          <button
            v-for="(show, index) in heroShows"
            :key="show.id"
            class="hero-thumb"
            :class="{ active: index === activeHeroIndex % heroShows.length }"
            type="button"
            @click="setActiveHero(index)"
          >
            <img
              :src="posterImageSrc(show.coverUrl, show.title)"
              :alt="show.title"
              loading="lazy"
              @error="handlePosterError($event, show.title)"
            />
          </button>
        </div>
        <button class="hero-nav" type="button" :aria-label="t('home.next')" @click="shiftHero('next')">
          <ArrowRight />
        </button>
      </div>
      <div v-if="heroShows.length > 1" class="hero-progress" aria-hidden="true">
        <span :key="featuredShow.id" />
      </div>
    </section>

    <section ref="searchPanelRef" class="search-panel" :aria-label="t('home.searchShows')">
      <label class="search-box">
        <Search />
        <input v-model="searchKeyword" type="search" :placeholder="t('home.searchPlaceholder')" />
      </label>
      <div class="select-box custom-select" :class="{ open: openDropdown === 'category' }">
        <span>{{ t('home.categoryFilter') }}</span>
        <button
          class="select-trigger"
          type="button"
          :aria-expanded="openDropdown === 'category'"
          aria-haspopup="listbox"
          @click="toggleDropdown('category')"
        >
          <strong>{{ activeCategoryLabel }}</strong>
          <ArrowDown />
        </button>
        <div v-if="openDropdown === 'category'" class="select-menu" role="listbox">
          <button
            v-for="option in categoryOptions"
            :key="option.value"
            class="select-option"
            :class="{ selected: option.value === activeCategory }"
            type="button"
            role="option"
            :aria-selected="option.value === activeCategory"
            @click="selectCategory(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <div class="select-box custom-select" :class="{ open: openDropdown === 'date' }">
        <span>{{ t('home.dateFilter') }}</span>
        <button
          class="select-trigger"
          type="button"
          :aria-expanded="openDropdown === 'date'"
          aria-haspopup="listbox"
          @click="toggleDropdown('date')"
        >
          <strong>{{ activeDateFilterLabel }}</strong>
          <ArrowDown />
        </button>
        <div v-if="openDropdown === 'date'" class="select-menu" role="listbox">
          <button
            v-for="option in dateFilterOptions"
            :key="option.value"
            class="select-option"
            :class="{ selected: option.value === dateFilter }"
            type="button"
            role="option"
            :aria-selected="option.value === dateFilter"
            @click="selectDateFilter(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </section>

    <section class="recommendation-section">
      <div class="section-header">
        <div>
          <span class="section-kicker">{{ t('home.hotPick') }}</span>
          <h2>{{ t('home.topRecommended') }}</h2>
        </div>
        <div class="pagination-controls">
          <button class="icon-btn" type="button" :aria-label="t('common.back')" @click="scrollRail('prev')">
            <ArrowLeft />
          </button>
          <button class="icon-btn" type="button" :aria-label="t('home.next')" @click="scrollRail('next')">
            <ArrowRight />
          </button>
        </div>
      </div>

      <div v-if="topPicks.length > 0" ref="railRef" class="recommendation-rail">
        <button
          v-for="show in topPicks"
          :key="show.id"
          class="recommendation-card"
          type="button"
          @click="goDetail(show.id)"
        >
          <span class="recommendation-rank">#{{ show.rank }}</span>
          <img
            :src="posterImageSrc(show.coverUrl, show.title)"
            :alt="show.title"
            loading="lazy"
            @error="handlePosterError($event, show.title)"
          />
          <div class="recommendation-info">
            <span>{{ show.category }}</span>
            <h3>{{ show.title }}</h3>
            <p>{{ show.subtitle }}</p>
            <div class="ticket-meta">
              <span><PriceTag /> {{ t('home.ticketFrom') }} {{ showPrice(show) }}</span>
              <span><Calendar /> {{ scheduleCountLabel(show) }}</span>
            </div>
          </div>
        </button>
      </div>

      <div v-else class="empty-state">
        {{ t('home.recommendationsEmpty') }}
      </div>
    </section>

    <section class="shows-section">
      <div class="section-header">
        <div>
          <span class="section-kicker">{{ t('home.discover') }}</span>
          <h2>{{ t('home.nowShowing') }}</h2>
        </div>
        <span class="section-count">{{ filteredShows.length }}</span>
      </div>

      <div v-if="filteredShows.length > 0" class="show-grid">
        <button
          v-for="show in filteredShows"
          :key="show.id"
          class="show-card"
          type="button"
          @click="goDetail(show.id)"
        >
          <div class="cover-wrapper">
            <img
              :src="posterImageSrc(show.coverUrl, show.title)"
              :alt="show.title"
              loading="lazy"
              @error="handlePosterError($event, show.title)"
            />
            <span class="status-badge" :class="{ presale: show.status === 'COMING_SOON' }">
              {{ statusLabel(show) }}
            </span>
          </div>
          <div class="show-info">
            <div class="show-topline">
              <span>{{ show.category }}</span>
              <strong>{{ showPrice(show) }}</strong>
            </div>
            <h3>{{ show.title }}</h3>
            <p>{{ show.subtitle }}</p>
            <div class="show-footer">
              <span>{{ formatCount(show.duration) }} {{ t('detail.minutes') }}</span>
              <span>{{ (show.tags || []).slice(0, 2).join(' / ') }}</span>
            </div>
          </div>
        </button>
      </div>

      <div v-else class="empty-state">
        {{ t('home.noShows') }}
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  width: 100%;
  overflow-x: hidden;
}

.hero-section {
  min-height: min(720px, calc(100vh - 76px));
  position: relative;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  border-bottom: 1px solid var(--color-border);
}

.hero-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.9) contrast(1.04);
}

.hero-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 8, 8, 0.96) 0%, rgba(8, 8, 8, 0.68) 44%, rgba(8, 8, 8, 0.2) 100%),
    linear-gradient(180deg, rgba(8, 8, 8, 0.16) 0%, var(--color-bg-base) 100%);
}

.hero-inner {
  position: relative;
  z-index: 2;
  width: min(1320px, calc(100% - 40px));
  margin: 0 auto;
  padding: var(--spacing-7) 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  align-items: end;
  gap: var(--spacing-5);
}

.hero-copy {
  max-width: 740px;

  .eyebrow {
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 3px;
    background: transparent;
    color: rgba(255, 255, 255, 0.7);
    display: inline-flex;
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.12em;
    padding: 8px 12px;
    text-transform: uppercase;
  }

  h1 {
    margin-top: var(--spacing-4);
    font-size: clamp(44px, 7vw, 86px);
    line-height: 0.98;
    max-width: 780px;
  }

  p {
    margin-top: var(--spacing-3);
    max-width: 560px;
    color: rgba(240, 237, 232, 0.78);
    font-family: var(--font-family-sans);
    font-size: 18px;
    line-height: 1.6;
  }
}

.hero-meta,
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  margin-top: var(--spacing-4);
}

.hero-meta span {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 700;
  padding: 8px 12px;
}

.primary-cta,
.secondary-cta {
  min-height: 48px;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-family: var(--font-family-sans);
  font-weight: 900;
  padding: 0 20px;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease;

  svg {
    width: 17px;
    height: 17px;
  }
}

.primary-cta {
  border: none;
  background: #e50914;
  color: #fff;

  &:hover {
    background: #f6121d;
  }
}

.secondary-cta {
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: var(--color-text-primary);

  &:hover {
    background: rgba(255, 255, 255, 0.16);
    color: #fff;
  }
}

.hero-ticket {
  border: 1px solid rgba(200, 149, 90, 0.34);
  border-radius: 14px;
  background: rgba(8, 8, 8, 0.72);
  padding: var(--spacing-4);
  display: grid;
  gap: 6px;
  box-shadow: 0 24px 62px rgba(0, 0, 0, 0.42);

  span,
  small {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    color: rgba(255, 255, 255, 0.94);
    font-family: var(--font-family-display);
    font-size: 44px;
    line-height: 1;
  }
}

.search-panel {
  width: min(1180px, calc(100% - 40px));
  margin: calc(var(--spacing-5) * -1) auto var(--spacing-6);
  position: relative;
  z-index: 30;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: rgba(17, 17, 17, 0.95);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.32);
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 220px 220px;
  gap: var(--spacing-3);
  padding: var(--spacing-3);
}

.search-box,
.select-box {
  min-width: 0;
  min-height: 54px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-base);
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: 0 14px;

  svg {
    width: 18px;
    height: 18px;
    color: var(--color-accent);
  }
}

.search-box input,
.select-trigger {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text-primary);
  font-family: var(--font-family-sans);
  font-size: 15px;
}

.select-box {
  position: relative;
  display: grid;
  grid-template-columns: 1fr;
  align-content: center;
  gap: 2px;

  span {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
}

.custom-select {
  padding: 7px 12px;
  overflow: visible;

  &.open {
    border-color: rgba(229, 9, 20, 0.48);
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.34);
  }
}

.select-trigger {
  min-height: 26px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0;
  text-align: left;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #fff;
    font-size: 16px;
    font-weight: 800;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  svg {
    width: 15px;
    height: 15px;
    color: rgba(255, 255, 255, 0.72);
    flex: 0 0 auto;
    transition: transform 160ms ease;
  }
}

.custom-select.open .select-trigger svg {
  transform: rotate(180deg);
}

.select-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 40;
  width: 100%;
  min-width: 220px;
  border: 1px solid rgba(255, 255, 255, 0.11);
  border-radius: 6px;
  background: rgba(8, 8, 10, 0.98);
  box-shadow: 0 22px 54px rgba(0, 0, 0, 0.52), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  overflow: hidden;
  padding: 6px;
}

.select-option {
  width: 100%;
  min-height: 38px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: rgba(255, 255, 255, 0.76);
  cursor: pointer;
  display: flex;
  align-items: center;
  font-family: var(--font-family-sans);
  font-size: 14px;
  font-weight: 750;
  padding: 0 10px 0 12px;
  position: relative;
  text-align: left;
  transition: background-color 150ms ease, color 150ms ease;

  &::before {
    content: '';
    width: 2px;
    height: 18px;
    border-radius: 2px;
    background: transparent;
    position: absolute;
    left: 4px;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.08);
    color: #fff;
  }

  &.selected {
    background: rgba(229, 9, 20, 0.13);
    color: #fff;

    &::before {
      background: #e50914;
    }
  }
}

.recommendation-section,
.shows-section {
  width: min(1320px, calc(100% - 40px));
  margin: 0 auto;
  padding-bottom: var(--spacing-7);
}

.section-header {
  margin-bottom: var(--spacing-4);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-4);

  h2 {
    font-size: clamp(28px, 3vw, 40px);
    line-height: 1.1;
  }
}

.section-kicker {
  color: rgba(200, 149, 90, 0.9);
  display: block;
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
  margin-bottom: var(--spacing-1);
  text-transform: uppercase;
}

.section-count {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 800;
  padding: 8px 12px;
}

.pagination-controls {
  display: flex;
  gap: var(--spacing-2);
}

.icon-btn {
  width: 44px;
  height: 44px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: border-color 160ms ease, color 160ms ease;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover {
    border-color: rgba(255, 255, 255, 0.5);
    color: #fff;
  }
}

.recommendation-rail {
  display: grid;
  grid-auto-columns: minmax(240px, 280px);
  grid-auto-flow: column;
  gap: var(--spacing-3);
  overflow-x: auto;
  padding-bottom: var(--spacing-2);
  scroll-snap-type: x proximity;
}

.recommendation-card,
.show-card {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
  cursor: pointer;
  overflow: hidden;
  padding: 0;
  text-align: left;
  transition: border-color 180ms ease, transform 180ms ease;

  &:hover {
    border-color: rgba(255, 255, 255, 0.22);
    transform: translateY(-2px);
  }
}

.recommendation-card {
  position: relative;
  display: grid;
  scroll-snap-align: start;

  > img {
    width: 100%;
    aspect-ratio: 16 / 10;
    object-fit: cover;
  }
}

.recommendation-rank {
  position: absolute;
  top: var(--spacing-3);
  right: var(--spacing-3);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  background: rgba(8, 8, 8, 0.48);
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 900;
  padding: 7px 10px;
}

.recommendation-info,
.show-info {
  padding: var(--spacing-3);
}

.recommendation-info {
  display: grid;
  gap: 8px;

  > span {
    color: rgba(200, 149, 90, 0.9);
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 900;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  h3 {
    display: -webkit-box;
    overflow: hidden;
    font-size: 22px;
    line-height: 1.12;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  p {
    color: var(--color-text-secondary);
    display: -webkit-box;
    font-family: var(--font-family-sans);
    font-size: 13px;
    line-height: 1.45;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
}

.ticket-meta {
  display: grid;
  gap: 6px;

  span {
    color: var(--color-text-secondary);
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-family: var(--font-family-sans);
    font-size: 12px;

    svg {
      width: 14px;
      height: 14px;
      color: rgba(200, 149, 90, 0.78);
    }
  }
}

.show-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--spacing-3);
}

.cover-wrapper {
  position: relative;

  img {
    width: 100%;
    aspect-ratio: 16 / 9;
    display: block;
    object-fit: cover;
    object-position: center;
  }
}

.status-badge {
  position: absolute;
  left: 10px;
  top: 10px;
  border-radius: 3px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  padding: 7px 10px;
  text-transform: uppercase;

  &.presale {
    background: transparent;
    border-color: #fff;
    color: #fff;
  }
}

.show-info {
  display: grid;
  gap: 7px;
  padding: 12px;

  h3 {
    font-size: 19px;
    line-height: 1.18;
  }

  p {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    line-height: 1.45;
  }
}

.show-topline,
.show-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-2);
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 11px;
  font-weight: 800;

  strong {
    color: rgba(255, 255, 255, 0.92);
    font-size: 14px;
  }
}

.show-footer {
  align-items: flex-start;
  border-top: 1px solid var(--color-border);
  margin-top: 4px;
  padding-top: 8px;
}

.empty-state {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  padding: var(--spacing-6);
  text-align: center;
}

@media (max-width: 860px) {
  .hero-inner {
    grid-template-columns: 1fr;
  }

  .hero-ticket {
    width: min(100%, 320px);
  }

  .search-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-section {
    min-height: 620px;
  }

  .hero-inner,
  .search-panel,
  .recommendation-section,
  .shows-section {
    width: min(100% - 24px, 1320px);
  }

  .hero-inner {
    padding: var(--spacing-5) 0;
  }

  .section-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-controls {
    width: 100%;
    justify-content: flex-end;
  }

  .recommendation-rail {
    grid-auto-columns: minmax(230px, 78vw);
  }

  .show-grid {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }
}

.home-page {
  --ticket-red: #e50914;
  background: #080808;
}

.hero-section {
  isolation: isolate;
  background: #050505;
}

.hero-image {
  animation: hero-image-in 6500ms ease-out both;
  will-change: opacity, transform;
}

.hero-fade-enter-active,
.hero-fade-leave-active {
  transition: opacity 420ms ease, transform 620ms ease;
}

.hero-fade-enter-from {
  opacity: 0;
  transform: scale(1.035);
}

.hero-fade-leave-to {
  opacity: 0;
  transform: scale(1.01);
}

.hero-scrim {
  background:
    linear-gradient(90deg, rgba(0, 0, 0, 0.96) 0%, rgba(0, 0, 0, 0.68) 42%, rgba(0, 0, 0, 0.16) 100%),
    linear-gradient(180deg, rgba(0, 0, 0, 0.08) 0%, #080808 100%);
}

.hero-copy {
  animation: hero-copy-rise 560ms ease-out both;

  .eyebrow {
    border-color: rgba(255, 255, 255, 0.2);
    background: rgba(0, 0, 0, 0.46);
    color: rgba(255, 255, 255, 0.92);

    &::before {
      content: '';
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #e50914;
      margin-right: 8px;
    }
  }
}

.primary-cta {
  background: #e50914;
  color: #fff;

  &:hover {
    background: #f6121d;
  }
}

.secondary-cta {
  border: none;
  background: rgba(255, 255, 255, 0.1);

  &:hover {
    background: rgba(255, 255, 255, 0.16);
    color: #fff;
  }
}

.hero-ticket {
  border-color: rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.72);
}

.hero-carousel-ui {
  position: absolute;
  left: 50%;
  bottom: 26px;
  z-index: 3;
  width: min(1320px, calc(100% - 40px));
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.hero-nav {
  width: 42px;
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.52);
  color: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover {
    border-color: #fff;
    background: rgba(255, 255, 255, 0.12);
    transform: scale(1.05);
  }
}

.hero-thumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.44);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.hero-thumb {
  width: 74px;
  height: 46px;
  border: 2px solid transparent;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  overflow: hidden;
  padding: 0;
  opacity: 0.58;
  transition: opacity 180ms ease, border-color 180ms ease, transform 180ms ease;

  img {
    width: 100%;
    height: 100%;
    display: block;
    object-fit: cover;
  }

  &.active,
  &:hover {
    border-color: rgba(255, 255, 255, 0.92);
    opacity: 1;
    transform: translateY(-2px);
  }
}

.hero-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3;
  height: 3px;
  background: rgba(255, 255, 255, 0.12);
  overflow: hidden;

  span {
    display: block;
    width: 100%;
    height: 100%;
    transform-origin: left center;
    background: #e50914;
    animation: hero-progress 6500ms linear both;
  }
}

@keyframes hero-image-in {
  from {
    transform: scale(1.04);
  }

  to {
    transform: scale(1);
  }
}

@keyframes hero-copy-rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes hero-progress {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

@media (max-width: 860px) {
  .hero-carousel-ui {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .hero-carousel-ui {
    bottom: 14px;
    width: min(100% - 24px, 1320px);
    gap: 8px;
  }

  .hero-thumbs {
    max-width: calc(100vw - 116px);
    overflow-x: auto;
  }

  .hero-thumb {
    flex: 0 0 58px;
    width: 58px;
    height: 38px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-image,
  .hero-copy,
  .hero-progress span {
    animation: none;
  }

  .hero-fade-enter-active,
  .hero-fade-leave-active {
    transition: none;
  }
}
</style>
