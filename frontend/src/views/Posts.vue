<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { getPostList, createPost, likePost, uploadPostImages, searchPosts } from '../api'
import { fmtTime, initials } from '../utils/constants'

const router = useRouter()
const posts = ref([])
const loading = ref(true)
const keyword = ref('')

const composerOpen = ref(false)
const composer = ref({ content: '', images: [] })
const uploading = ref(false)
const publishing = ref(false)

/* 搜索：走服务端（帖子文案 + 作者昵称），带 300ms 防抖 */
const searchMode = ref(false)
const searchResults = ref([])
const searching = ref(false)
let searchTimer = null

const displayPosts = computed(() => (searchMode.value ? searchResults.value : posts.value))

async function runSearch () {
  const k = keyword.value.trim()
  if (!k) {
    searchMode.value = false
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const res = await searchPosts(k, 1, 30, 'relevance')
    searchResults.value = (res && res.records) || []
    searchMode.value = true
  } catch (e) {
    searchResults.value = []
    searchMode.value = true
  } finally {
    searching.value = false
  }
}

watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(runSearch, 300)
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

async function load() {
  loading.value = true
  try {
    posts.value = (await getPostList()) || []
  } catch {
    /* toast 已提示 */
  } finally {
    loading.value = false
  }
}

async function toggleLike(p) {
  const before = p.liked
  p.liked = !before
  p.likeCount = (p.likeCount || 0) + (p.liked ? 1 : -1)
  try {
    await likePost(p.id)
  } catch {
    p.liked = before
    p.likeCount = (p.likeCount || 0) + (p.liked ? 1 : -1)
  }
}

async function pickImages(e) {
  const files = Array.from(e.target.files || [])
  if (!files.length) return
  uploading.value = true
  try {
    const urls = await uploadPostImages(files)
    composer.value.images.push(...(urls || []))
  } catch {
    /* toast 已提示 */
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

function removeImage(i) {
  composer.value.images.splice(i, 1)
}

async function publish() {
  if (!composer.value.content.trim()) return
  publishing.value = true
  try {
    await createPost(composer.value.content, composer.value.images)
    composerOpen.value = false
    composer.value = { content: '', images: [] }
    await load()
  } catch {
    /* toast 已提示 */
  } finally {
    publishing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">社区广场</h1>
        <p class="page-header__sub">分享与宠物的日常，看看大家都在聊什么</p>
      </div>
      <div class="page-header__actions">
        <div class="search" style="width: 220px">
          <span class="search__icon"><AppIcon name="search" :size="15" /></span>
          <input v-model="keyword" class="input" placeholder="搜索帖子或作者" />
        </div>
        <button class="btn btn-primary" @click="composerOpen = true">
          <AppIcon name="plus" :size="16" />发布
        </button>
      </div>
    </header>

    <div class="page__scroll">
    <div v-if="loading" class="empty">
      <span class="spinner"></span>
      <span class="empty__desc">加载中…</span>
    </div>

    <div v-else-if="!displayPosts.length" class="card empty">
      <div class="empty__icon"><AppIcon name="message" :size="22" /></div>
      <div class="empty__title">{{ searchMode ? '没有找到相关帖子' : '还没有帖子' }}</div>
      <div class="empty__desc">成为第一个分享的人吧</div>
      <button class="btn btn-primary btn-sm" style="margin-top: 8px" @click="composerOpen = true">
        发布第一条
      </button>
    </div>

    <div v-else class="feed">
      <article v-for="p in displayPosts" :key="p.id" class="card post" @click="router.push(`/post/${p.id}`)">
        <header class="post__head">
          <span class="avatar"><template v-if="!p.author?.avatar">{{ initials(p.author?.nickname) }}</template><img v-else :src="p.author.avatar" alt="" /></span>
          <div class="post__meta">
            <div v-if="p.authorHighlight" class="post__author" v-html="p.authorHighlight"></div>
            <div v-else class="post__author">{{ p.author?.nickname || '匿名用户' }}</div>
            <div class="post__time">{{ fmtTime(p.createTime) }}</div>
          </div>
        </header>

        <p v-if="p.contentHighlight" class="post__content" v-html="p.contentHighlight"></p>
        <p v-else class="post__content">{{ p.content }}</p>

        <div v-if="p.images?.length" class="post__imgs" :class="`is-${Math.min(p.images.length, 3)}`">
          <img v-for="(img, i) in p.images" :key="i" :src="img" alt="" loading="lazy" />
        </div>

        <footer class="post__actions">
          <button class="post__act" :class="{ 'is-liked': p.liked }" @click.stop="toggleLike(p)">
            <AppIcon name="heart" :size="16" />
            <span>{{ p.likeCount || 0 }}</span>
          </button>
          <button class="post__act" @click.stop="router.push(`/post/${p.id}`)">
            <AppIcon name="message" :size="16" />
            <span>评论</span>
          </button>
          <span class="post__act post__act--muted">
            <AppIcon name="eye" :size="16" />
            <span>{{ p.viewCount || 0 }}</span>
          </span>
        </footer>
      </article>
      </div>
    </div>

    <!-- 发布弹窗 -->
    <div v-if="composerOpen" class="modal-scrim" @click.self="composerOpen = false">
      <div class="modal">
        <div class="modal__head">
          <h2 class="modal__title">发布帖子</h2>
          <button class="btn-icon" aria-label="关闭" @click="composerOpen = false">
            <AppIcon name="x" :size="18" />
          </button>
        </div>
        <div class="modal__body">
          <textarea
            v-model="composer.content"
            class="textarea"
            rows="5"
            placeholder="分享你和宠物的故事…"
          ></textarea>

          <div v-if="composer.images.length" class="composer__imgs">
            <div v-for="(img, i) in composer.images" :key="i" class="composer__img">
              <img :src="img" alt="" />
              <button class="composer__remove" aria-label="移除图片" @click="removeImage(i)">
                <AppIcon name="x" :size="13" />
              </button>
            </div>
          </div>
        </div>
        <div class="modal__foot" style="justify-content: space-between">
          <label class="btn btn-ghost btn-sm" style="cursor: pointer">
            <AppIcon name="image" :size="15" />{{ uploading ? '上传中…' : '添加图片' }}
            <input type="file" multiple accept="image/*" hidden @change="pickImages" />
          </label>
          <div class="row">
            <button class="btn btn-ghost" @click="composerOpen = false">取消</button>
            <button class="btn btn-primary" :disabled="publishing || !composer.content.trim()" @click="publish">
              发布
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feed {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 680px;
}

.post {
  padding: 16px 18px;
  cursor: pointer;
  transition: border-color 160ms;
}

.post:hover {
  border-color: var(--border-strong);
}

.post__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.post__meta {
  min-width: 0;
}

.post__author {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.post__time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.post__content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.post__imgs {
  display: grid;
  gap: 6px;
  margin-top: 12px;
  border-radius: 10px;
  overflow: hidden;
}

.post__imgs img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 10px;
}

.post__imgs.is-1 {
  grid-template-columns: 1fr;
  max-height: 360px;
}

.post__imgs.is-2 {
  grid-template-columns: 1fr 1fr;
  max-height: 220px;
}

.post__imgs.is-3 {
  grid-template-columns: repeat(3, 1fr);
  max-height: 160px;
}

.post__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.post__act {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border-radius: 7px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  transition: background 160ms, color 160ms;
}

.post__act:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.post__act.is-liked {
  color: var(--danger);
}

.post__act--muted {
  cursor: default;
}

.composer__imgs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.composer__img {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 1;
}

.composer__img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.composer__remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border: none;
  display: grid;
  place-items: center;
}
</style>
