<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getShowList, getTopRecommendedShows } from '../../api/show'
import type { RecommendedShow } from '../../api/show'
import type { Show } from '../../mock/shows'
import { mockShows } from '../../mock/shows'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const shows = ref<Show[]>([])
const recommendedShows = ref<RecommendedShow[]>([])
const { t } = useI18n()

const activeCategory = ref('All')

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
    hotScore: 0
  }))
}

onMounted(async () => {
  let resolvedShows: Show[] = []

  try {
    const data = await getShowList()
    if (data && data.length > 0) {
      resolvedShows = normalizeShows(data)
    } else {
      resolvedShows = mockShows
    }
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
        hotScore: show.hotScore || 0
      }))
      : toFallbackRecommendations(resolvedShows)
  } catch {
    recommendedShows.value = toFallbackRecommendations(resolvedShows)
  }
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

const filteredShows = computed(() => {
  if (activeCategory.value === 'All') return shows.value
  return shows.value.filter(s => s.category === activeCategory.value)
})

const nowShowing = computed(() => {
  return filteredShows.value.filter(s => s.status !== 'COMING_SOON')
})

const comingSoon = computed(() => {
  return filteredShows.value.filter(s => s.status === 'COMING_SOON')
})

const formatCount = (value: number) => {
  return new Intl.NumberFormat().format(value)
}

const goDetail = (id: string) => {
  router.push(`/show/${id}`)
}
</script>

<template>
  <div class="home-page">
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="brand">{{ t('home.brand') }}<span class="dot">.</span></h1>
        <p class="tagline">{{ t('home.tagline') }}</p>
      </div>
      <div class="hero-bg"></div>
    </section>

    <section class="recommendation-section">
      <div class="section-header recommendation-header">
        <div>
          <span class="section-kicker">{{ t('home.hotPick') }}</span>
          <h2>{{ t('home.topRecommended') }}</h2>
        </div>
        <span class="section-count">{{ recommendedShows.length }}</span>
      </div>

      <div class="recommendation-rail" v-if="recommendedShows.length > 0">
        <button
          class="recommendation-card"
          v-for="show in recommendedShows"
          :key="show.id"
          type="button"
          @click="goDetail(show.id)"
        >
          <span class="recommendation-rank">#{{ show.rank }}</span>
          <div class="recommendation-cover">
            <img :src="show.coverUrl" :alt="show.title" loading="lazy" />
            <span class="hot-badge">{{ t('home.hotPick') }}</span>
          </div>
          <div class="recommendation-info">
            <p class="recommendation-category">{{ show.category }}</p>
            <h3>{{ show.title }}</h3>
            <p class="recommendation-subtitle">{{ show.subtitle }}</p>
            <div class="recommendation-meta">
              <span>{{ formatCount(show.ticketsSold) }} {{ t('home.ticketsSold') }}</span>
              <span>{{ formatCount(show.availableScheduleCount) }} {{ t('home.onSaleSchedules') }}</span>
            </div>
          </div>
        </button>
      </div>

      <div class="recommendation-empty" v-else>
        {{ t('home.recommendationsEmpty') }}
      </div>
    </section>

    <!-- Categories Tab -->
    <div class="category-tabs">
      <button
        v-for="cat in categories"
        :key="cat"
        :class="['cat-btn', { active: activeCategory === cat }]"
        @click="activeCategory = cat"
      >
        {{ t(categoryLabelMap[cat]) }}
      </button>
    </div>

    <!-- Now Showing -->
    <section class="shows-section" v-if="nowShowing.length > 0">
      <div class="section-header">
        <h2>{{ t('home.nowShowing') }}</h2>
        <span class="section-count">{{ nowShowing.length }}</span>
      </div>
      <div class="show-grid">
        <div
          class="show-card"
          v-for="show in nowShowing"
          :key="show.id"
          @click="goDetail(show.id)"
        >
          <div class="cover-wrapper">
            <img :src="show.coverUrl" :alt="show.title" loading="lazy" />
            <div class="category-badge">{{ show.category }}</div>
            <div class="overlay">
              <span class="view-btn">{{ t('home.viewDetails') }}</span>
            </div>
          </div>
          <div class="info">
            <h3 class="title">{{ show.title }}</h3>
            <p class="subtitle">{{ show.subtitle }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Coming Soon / Reservation -->
    <section class="shows-section coming-soon-section" v-if="comingSoon.length > 0">
      <div class="section-header">
        <h2>{{ t('home.comingSoon') }}</h2>
        <span class="section-count">{{ comingSoon.length }}</span>
      </div>
      <div class="show-grid">
        <div
          class="show-card"
          v-for="show in comingSoon"
          :key="show.id"
          @click="goDetail(show.id)"
        >
          <div class="cover-wrapper">
            <img :src="show.coverUrl" :alt="show.title" loading="lazy" />
            <div class="category-badge coming-soon-badge">{{ t('home.comingSoonBadge') }}</div>
            <div class="overlay">
              <span class="view-btn reserve-btn">{{ t('detail.reserve') }}</span>
            </div>
          </div>
          <div class="info">
            <h3 class="title">{{ show.title }}</h3>
            <p class="subtitle">{{ show.subtitle }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Empty state -->
    <div class="empty-state" v-if="nowShowing.length === 0 && comingSoon.length === 0">
      <p>{{ t('home.noShows') }}</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  width: 100%;
}

.hero-section {
  height: 60vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  .hero-bg {
    position: absolute;
    inset: 0;
    background-image: url('https://images.unsplash.com/photo-1507676184212-d0330a157088?q=80&w=2000&auto=format&fit=crop');
    background-size: cover;
    background-position: center 30%;
    filter: grayscale(100%) contrast(1.2);
    opacity: 0.3;
    z-index: -1;
  }

  .hero-content {
    text-align: center;

    .brand {
      font-family: var(--font-family-display);
      font-size: 8vw;
      font-weight: 900;
      line-height: 1;
      margin-bottom: var(--spacing-4);
      letter-spacing: 0.05em;

      .dot {
        color: var(--color-accent);
      }
    }

    .tagline {
      font-family: var(--font-family-sans);
      font-size: 18px;
      letter-spacing: 0.2em;
      text-transform: uppercase;
      color: var(--color-text-secondary);
    }
  }
}

.recommendation-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--spacing-10) var(--spacing-6) var(--spacing-6);

  .recommendation-header {
    margin-bottom: var(--spacing-5);
    display: flex;
    align-items: end;
    justify-content: space-between;
    gap: var(--spacing-4);

    h2 {
      font-family: var(--font-family-display);
      font-size: 32px;
      font-weight: 700;
      letter-spacing: 0.05em;
      line-height: 1.15;
    }

    .section-kicker {
      display: block;
      margin-bottom: var(--spacing-2);
      color: var(--color-accent);
      font-family: var(--font-family-sans);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }

    .section-count {
      color: var(--color-text-secondary);
      font-family: var(--font-family-sans);
      font-size: 14px;
    }
  }

  .recommendation-rail {
    display: grid;
    grid-auto-columns: minmax(220px, 260px);
    grid-auto-flow: column;
    gap: var(--spacing-4);
    overflow-x: auto;
    padding-bottom: var(--spacing-2);
    scroll-snap-type: x proximity;
  }

  .recommendation-card {
    position: relative;
    display: flex;
    min-height: 320px;
    flex-direction: column;
    gap: var(--spacing-3);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-bg-elevated);
    color: var(--color-text-primary);
    cursor: pointer;
    padding: var(--spacing-3);
    scroll-snap-align: start;
    text-align: left;
    transition: border-color 200ms ease, transform 200ms ease;

    &:hover {
      border-color: var(--color-accent);
      transform: translateY(-2px);
    }
  }

  .recommendation-rank {
    position: absolute;
    top: var(--spacing-3);
    right: var(--spacing-3);
    z-index: 2;
    min-width: 42px;
    border: 1px solid rgba(212, 175, 55, 0.45);
    background: rgba(0, 0, 0, 0.72);
    color: var(--color-accent);
    font-family: var(--font-family-display);
    font-size: 18px;
    font-weight: 700;
    line-height: 1;
    padding: 8px 10px;
    text-align: center;
  }

  .recommendation-cover {
    position: relative;
    aspect-ratio: 16 / 10;
    overflow: hidden;
    border-radius: 6px;
    background: #0f0f0f;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      filter: contrast(1.08) saturate(0.92);
      transition: transform 400ms ease;
    }

    .hot-badge {
      position: absolute;
      left: var(--spacing-2);
      bottom: var(--spacing-2);
      background: var(--color-accent);
      color: #000;
      font-family: var(--font-family-sans);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.08em;
      padding: 5px 8px;
      text-transform: uppercase;
    }
  }

  .recommendation-card:hover .recommendation-cover img {
    transform: scale(1.04);
  }

  .recommendation-info {
    display: flex;
    min-height: 140px;
    flex: 1;
    flex-direction: column;

    h3 {
      display: -webkit-box;
      overflow: hidden;
      margin: 0 0 var(--spacing-2);
      font-family: var(--font-family-display);
      font-size: 20px;
      line-height: 1.2;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
    }
  }

  .recommendation-category {
    margin-bottom: var(--spacing-2);
    color: var(--color-accent);
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  .recommendation-subtitle {
    display: -webkit-box;
    overflow: hidden;
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    line-height: 1.4;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .recommendation-meta {
    display: grid;
    gap: 6px;
    margin-top: auto;
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
    line-height: 1.35;
  }

  .recommendation-empty {
    border: 1px solid var(--color-border);
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    padding: var(--spacing-6);
    text-align: center;
  }
}

.category-tabs {
  display: flex;
  justify-content: center;
  gap: var(--spacing-4);
  padding: var(--spacing-6) 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: var(--spacing-8);
  flex-wrap: wrap;

  .cat-btn {
    background: transparent;
    border: none;
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--color-text-secondary);
    padding: var(--spacing-2) var(--spacing-4);
    cursor: pointer;
    transition: all 200ms ease;

    &:hover {
      color: var(--color-text-primary);
    }

    &.active {
      color: var(--color-text-primary);
      border-bottom: 2px solid var(--color-accent);
    }
  }
}

.shows-section {
  padding: 0 var(--spacing-6) var(--spacing-12);
  max-width: 1400px;
  margin: 0 auto;

  &.coming-soon-section {
    .show-card .cover-wrapper img {
      filter: grayscale(60%) contrast(1.1);
    }

    .view-btn.reserve-btn {
      border-color: var(--color-accent);
      color: var(--color-accent);
    }

    .coming-soon-badge {
      background-color: var(--color-accent);
      color: #000;
    }
  }

  .section-header {
    margin-bottom: var(--spacing-8);
    display: flex;
    align-items: baseline;
    gap: var(--spacing-3);

    h2 {
      font-family: var(--font-family-display);
      font-size: 32px;
      font-weight: 700;
      letter-spacing: 0.05em;
    }

    .section-count {
      font-family: var(--font-family-sans);
      font-size: 14px;
      color: var(--color-text-secondary);
    }
  }

  .show-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: var(--spacing-8) var(--spacing-6);
  }
}

.show-card {
  cursor: pointer;

  .cover-wrapper {
    position: relative;
    aspect-ratio: 3 / 4;
    overflow: hidden;
    margin-bottom: var(--spacing-4);
    background-color: var(--color-bg-elevated);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 500ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
    }

    .category-badge {
      position: absolute;
      top: var(--spacing-3);
      left: var(--spacing-3);
      background-color: rgba(0, 0, 0, 0.7);
      color: var(--color-accent);
      font-family: var(--font-family-sans);
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      padding: 4px 10px;
      z-index: 2;
    }

    .overlay {
      position: absolute;
      inset: 0;
      background-color: rgba(0, 0, 0, 0.4);
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 300ms ease;

      .view-btn {
        padding: 12px 24px;
        border: 1px solid #fff;
        color: #fff;
        font-family: var(--font-family-sans);
        font-size: 14px;
        text-transform: uppercase;
        letter-spacing: 0.1em;
      }
    }
  }

  &:hover {
    .cover-wrapper img {
      transform: scale(1.05);
    }
    .cover-wrapper .overlay {
      opacity: 1;
    }
  }

  .info {
    .title {
      font-family: var(--font-family-display);
      font-size: 24px;
      margin-bottom: var(--spacing-1);
      line-height: 1.2;
    }

    .subtitle {
      font-family: var(--font-family-sans);
      font-size: 14px;
      color: var(--color-text-secondary);
    }
  }
}

.empty-state {
  text-align: center;
  padding: var(--spacing-12);
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
}

@media (max-width: 768px) {
  .recommendation-section {
    padding-top: var(--spacing-8);

    .recommendation-header {
      align-items: flex-start;
      flex-direction: column;

      h2 {
        font-size: 26px;
      }
    }

    .recommendation-rail {
      grid-auto-columns: minmax(210px, 78vw);
    }
  }
}
</style>
