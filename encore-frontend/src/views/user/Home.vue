<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getShowList } from '../../api/show'
import type { Show } from '../../mock/shows'
import { mockShows } from '../../mock/shows'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const shows = ref<Show[]>([])
const { t } = useI18n()

const activeCategory = ref('All')

onMounted(async () => {
  try {
    const data = await getShowList()
    // 后端数据可能没有 status 字段，需要合并 mock 中的 status
    if (data && data.length > 0) {
      shows.value = data.map(s => {
        const mockMatch = mockShows.find(m => m.id === s.id)
        return { ...s, status: s.status || mockMatch?.status || 'ON_SALE' }
      })
    } else {
      shows.value = mockShows
    }
  } catch {
    shows.value = mockShows
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
</style>
