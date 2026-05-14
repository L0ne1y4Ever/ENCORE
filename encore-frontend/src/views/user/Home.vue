<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getShowList } from '../../api/show'
import type { Show } from '../../mock/shows'

const router = useRouter()
const shows = ref<Show[]>([])
const loading = ref(true)

onMounted(async () => {
  shows.value = await getShowList()
  loading.value = false
})

const goDetail = (id: string) => {
  router.push(`/show/${id}`)
}
</script>

<template>
  <div class="home-page">
    <section class="hero" v-if="shows.length > 0" @click="goDetail(shows[0].id)">
      <img :src="shows[0].coverUrl" class="hero-bg" />
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <div class="tag">{{ shows[0].category }}</div>
        <h1 class="title">{{ shows[0].title }}</h1>
        <p class="subtitle">{{ shows[0].subtitle }}</p>
      </div>
    </section>

    <section class="list-section">
      <h2 class="section-title">Now Showing</h2>
      
      <div class="show-grid" v-if="!loading">
        <div 
          class="show-card" 
          v-for="show in shows.slice(1)" 
          :key="show.id"
          @click="goDetail(show.id)"
        >
          <div class="card-image">
            <img :src="show.coverUrl" />
          </div>
          <div class="card-info">
            <h3 class="card-title">{{ show.title }}</h3>
            <p class="card-subtitle">{{ show.subtitle }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  width: 100%;
}

.hero {
  position: relative;
  width: 100%;
  height: 70vh;
  min-height: 500px;
  cursor: pointer;
  overflow: hidden;

  .hero-bg {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 6s ease-out;
  }

  &:hover .hero-bg {
    transform: scale(1.05);
  }

  .hero-overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(to top, var(--color-bg-base) 0%, rgba(8,8,8,0.4) 50%, rgba(8,8,8,0.1) 100%);
  }

  .hero-content {
    position: absolute;
    bottom: var(--spacing-8);
    left: var(--spacing-6);
    right: var(--spacing-6);
    z-index: 10;

    .tag {
      font-family: var(--font-family-sans);
      font-size: 12px;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--color-accent);
      margin-bottom: var(--spacing-2);
      font-weight: 500;
    }

    .title {
      font-family: var(--font-family-display);
      font-size: 72px;
      line-height: 1.1;
      font-weight: 900;
      color: var(--color-text-primary);
      margin-bottom: var(--spacing-2);
      max-width: 800px;
    }

    .subtitle {
      font-family: var(--font-family-sans);
      font-size: 18px;
      color: var(--color-text-secondary);
      font-weight: 400;
    }
  }
}

.list-section {
  padding: var(--spacing-8) var(--spacing-6);

  .section-title {
    font-family: var(--font-family-display);
    font-size: 32px;
    margin-bottom: var(--spacing-6);
  }

  .show-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: var(--spacing-6);
  }

  .show-card {
    cursor: pointer;
    group: hover;

    .card-image {
      width: 100%;
      aspect-ratio: 3/4;
      overflow: hidden;
      margin-bottom: var(--spacing-3);
      border-radius: var(--radius-none); // 极简，无圆角
      
      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        transition: transform 400ms ease, filter 400ms ease;
        filter: brightness(0.9);
      }
    }

    &:hover {
      .card-image img {
        transform: translateY(-4px);
        filter: brightness(1.1);
      }
      .card-title {
        color: var(--color-accent);
      }
    }

    .card-info {
      .card-title {
        font-family: var(--font-family-display);
        font-size: 20px;
        font-weight: 700;
        margin-bottom: var(--spacing-1);
        transition: color 200ms ease;
      }
      .card-subtitle {
        font-family: var(--font-family-sans);
        font-size: 14px;
        color: var(--color-text-secondary);
      }
    }
  }
}

@media (max-width: 768px) {
  .hero .hero-content .title {
    font-size: 48px;
  }
}
</style>
