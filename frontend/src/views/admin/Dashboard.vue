<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import {
  adminGetUserStats,
  adminGetPostStats,
  adminGetCommentStats,
  adminGetDailyTrend
} from '../../api'

const userStats = ref({})
const postStats = ref({})
const commentStats = ref({})
const trend = ref([])

const cards = computed(() => [
  { label: '注册用户', value: userStats.value.totalUsers ?? 0, delta: `今日 +${userStats.value.todayNewUsers ?? 0}` },
  { label: '好友关系', value: userStats.value.totalFriends ?? 0, delta: '' },
  { label: '帖子总数', value: postStats.value.totalPosts ?? 0, delta: `今日 +${postStats.value.todayNewPosts ?? 0}` },
  { label: '评论总数', value: commentStats.value.totalComments ?? 0, delta: `今日 +${commentStats.value.todayNewComments ?? 0}` }
])

const maxVal = computed(() => {
  let m = 1
  trend.value.forEach((d) => {
    m = Math.max(m, d.newPosts || 0, d.newComments || 0)
  })
  return m
})

function h(v) {
  return Math.round(((v || 0) / maxVal.value) * 100)
}

onMounted(async () => {
  try {
    const [u, p, c, t] = await Promise.all([
      adminGetUserStats(),
      adminGetPostStats(),
      adminGetCommentStats(),
      adminGetDailyTrend()
    ])
    userStats.value = u || {}
    postStats.value = p || {}
    commentStats.value = c || {}
    trend.value = t || []
  } catch {
    /* toast 已提示 */
  }
})
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">概览</h1>
        <p class="page-header__sub">平台运营数据一览</p>
      </div>
    </header>

    <div class="page__scroll">
    <div class="card-grid" style="margin-bottom: 18px">
      <div v-for="c in cards" :key="c.label" class="stat-card">
        <div class="stat-card__label">{{ c.label }}</div>
        <div class="stat-card__value">{{ c.value }}</div>
        <div v-if="c.delta" class="stat-card__delta" style="color: var(--primary)">{{ c.delta }}</div>
      </div>
    </div>

    <section class="card card--pad">
      <div class="section__head">
        <h2 class="section__title">近 7 日趋势</h2>
      </div>

      <div class="chart">
        <div class="chart__legend">
          <span class="chart__legend-item"><i class="chart__dot chart__dot--posts"></i>新帖</span>
          <span class="chart__legend-item"><i class="chart__dot chart__dot--comments"></i>新评论</span>
        </div>
        <div class="chart__plot">
          <div v-for="d in trend" :key="d.date" class="chart__col">
            <div class="chart__bars">
              <div class="chart__bar chart__bar--posts" :style="{ height: h(d.newPosts) + '%' }" :title="`新帖 ${d.newPosts}`"></div>
              <div class="chart__bar chart__bar--comments" :style="{ height: h(d.newComments) + '%' }" :title="`新评论 ${d.newComments}`"></div>
            </div>
            <div class="chart__label">{{ (d.date || '').slice(5) }}</div>
          </div>
        </div>
      </div>
    </section>
    </div>
  </div>
</template>

<style scoped>
.chart {
  margin-top: 6px;
}

.chart__legend {
  display: flex;
  gap: 16px;
  margin-bottom: 14px;
}

.chart__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.chart__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.chart__dot--posts {
  background: var(--primary);
}

.chart__dot--comments {
  background: var(--success);
}

.chart__plot {
  display: flex;
  gap: 12px;
  height: 220px;
  align-items: flex-end;
}

.chart__col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  height: 100%;
}

.chart__bars {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
}

.chart__bar {
  width: 22px;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
  transition: height 400ms ease;
}

.chart__bar--posts {
  background: var(--primary);
}

.chart__bar--comments {
  background: var(--success);
}

.chart__label {
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
