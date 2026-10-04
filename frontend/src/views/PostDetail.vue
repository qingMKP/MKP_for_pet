<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import '../assets/mkp/mkp-pages.css'
import {
  getPostDetail,
  getPostComments,
  createComment,
  likeComment,
  likePost,
  deletePost
} from '../api'
import { fmtTime, initials } from '../utils/constants'

/* ================================================================
   帖子详情（社交广场 → 详情）
   - 版式：左主列（帖子卡 + 评论），右侧栏（作者 / 帖子数据 / 话题）
   - 数据：/api/post/:id、/api/post/:id/comments
   ================================================================ */
const route = useRoute()
const router = useRouter()

const post = ref(null)
const comments = ref([])
const loading = ref(true)
const commentText = ref('')
const replyTo = ref(null)
const replyText = ref('')

const isMine = computed(() => post.value?.author?.id == localStorage.getItem('userId'))

/* 从正文里取 #话题（规则与广场后端索引一致） */
const topics = computed(() => {
  const out = []
  const re = /#([\p{L}\p{N}_]{1,20})/gu
  let m
  while ((m = re.exec(post.value?.content || '')) !== null) {
    if (out.indexOf(m[1]) === -1) out.push(m[1])
  }
  return out.slice(0, 6)
})

async function loadComments () {
  comments.value = (await getPostComments(route.params.id)) || []
}

async function load () {
  loading.value = true
  try {
    post.value = await getPostDetail(route.params.id)
    await loadComments()
  } catch {
    /* toast 已提示 */
  } finally {
    loading.value = false
  }
}

async function toggleLikePost () {
  if (!post.value) return
  post.value.liked = !post.value.liked
  post.value.likeCount = (post.value.likeCount || 0) + (post.value.liked ? 1 : -1)
  try {
    await likePost(post.value.id)
  } catch {
    /* ignore */
  }
}

async function submitComment () {
  if (!commentText.value.trim()) return
  await createComment(post.value.id, commentText.value.trim())
  commentText.value = ''
  await loadComments()
}

function startReply (c) {
  replyTo.value = c
  replyText.value = ''
}

async function submitReply () {
  if (!replyText.value.trim() || !replyTo.value) return
  await createComment(post.value.id, replyText.value.trim(), replyTo.value.id)
  replyText.value = ''
  replyTo.value = null
  await loadComments()
}

async function toggleLikeComment (c) {
  c.liked = !c.liked
  c.likeCount = (c.likeCount || 0) + (c.liked ? 1 : -1)
  try {
    await likeComment(c.id)
  } catch {
    /* ignore */
  }
}

async function removePost () {
  if (!window.confirm('确定删除这篇帖子吗？')) return
  await deletePost(post.value.id)
  router.push('/square')
}

/* 点话题：回广场并带上 #话题 直接筛 */
function goTopic (t) {
  router.push({ path: '/square', query: { q: '#' + t } })
}

onMounted(load)
</script>

<template>
  <div class="mkp-page pg-post">
    <div class="bg-layer" aria-hidden="true">
      <div class="blob b1"></div><div class="blob b2"></div><div class="blob b3"></div>
    </div>

    <MkpTopNav active="/square" :show-search="false" :show-action="false" />

    <main id="pm-main" class="pm-main">
      <div class="pg-wrap">
        <div class="pd-topbar">
          <button class="pd-back" type="button" @click="router.push('/square')">
            <AppIcon name="arrow-left" :size="15" />返回社交广场
          </button>
          <span class="pd-crumb">社交广场 / 帖子详情</span>
        </div>

        <!-- 加载：按最终两栏占位，避免进页面时抖一下 -->
        <div v-if="loading" class="pd-grid">
          <div class="pd-skel glass">
            <span class="sp"></span>
            <p>正在打开帖子…</p>
          </div>
          <aside class="pd-side">
            <div class="pd-skel-sm glass"></div>
            <div class="pd-skel-sm glass"></div>
          </aside>
        </div>

        <div v-else-if="!post" class="pg-empty pd-miss glass">
          <div class="ico" aria-hidden="true">🐾</div>
          <h4>帖子不存在或已删除</h4>
          <p>它可能已经被作者删除，回广场看看别的吧</p>
          <button class="btn btn-dark" type="button" @click="router.push('/square')">回到社交广场</button>
        </div>

        <div v-else class="pd-grid">
          <!-- ===== 主列 ===== -->
          <div class="pd-main">
            <article class="pd-post glass">
              <header class="pd-head">
                <span class="pd-av">
                  <img v-if="post.author?.avatar" :src="post.author.avatar" alt="" />
                  <template v-else>{{ initials(post.author?.nickname) }}</template>
                </span>
                <div class="pd-head-txt">
                  <b>{{ post.author?.nickname || '匿名用户' }}</b>
                  <small>{{ fmtTime(post.createTime) }} 发布</small>
                </div>
                <button v-if="isMine" class="btn btn-ghost btn-sm" type="button" @click="removePost">
                  <AppIcon name="trash" :size="13" />删除
                </button>
              </header>

              <p v-if="post.content" class="pd-content">{{ post.content }}</p>

              <div v-if="topics.length" class="pd-topics">
                <button v-for="t in topics" :key="t" class="chip" type="button" @click="goTopic(t)">#{{ t }}</button>
              </div>

              <div v-if="post.images?.length" class="pd-imgs" :class="'is-' + Math.min(post.images.length, 3)">
                <img v-for="(img, i) in post.images" :key="i" :src="img" alt="" loading="lazy" />
              </div>

              <footer class="pd-actions">
                <button class="pd-act" :class="{ 'is-liked': post.liked }" type="button" @click="toggleLikePost">
                  <AppIcon name="heart" :size="16" />{{ post.likeCount || 0 }}
                </button>
                <span class="pd-act is-muted"><AppIcon name="eye" :size="16" />{{ post.viewCount || 0 }}</span>
                <span class="pd-act is-muted"><AppIcon name="message" :size="16" />{{ comments.length }}</span>
              </footer>
            </article>

            <!-- 评论 -->
            <section class="pd-comments glass">
              <div class="pd-sec-head">
                <h2>评论</h2>
                <span class="pd-count">{{ comments.length }}</span>
              </div>

              <div class="pd-composer">
                <textarea v-model="commentText" rows="2" placeholder="写下你的评论…"></textarea>
                <div class="pd-composer-foot">
                  <span class="pd-hint">友善交流，广告与人身攻击会被删除</span>
                  <button class="btn btn-dark btn-sm" type="button" :disabled="!commentText.trim()" @click="submitComment">
                    发表评论
                  </button>
                </div>
              </div>

              <div v-if="!comments.length" class="pg-empty pd-empty">
                <div class="ico" aria-hidden="true">🐾</div>
                <h4>还没有评论</h4>
                <p>来抢个沙发，说点什么吧</p>
              </div>

              <div v-else class="pd-list">
                <article v-for="c in comments" :key="c.id" class="pd-comment">
                  <span class="pd-av pd-av--sm">
                    <img v-if="c.author?.avatar" :src="c.author.avatar" alt="" />
                    <template v-else>{{ initials(c.author?.nickname) }}</template>
                  </span>

                  <div class="pd-c-body">
                    <div class="pd-c-head">
                      <b>{{ c.author?.nickname || '匿名' }}</b>
                      <span class="pd-time">{{ fmtTime(c.createTime) }}</span>
                    </div>
                    <p class="pd-c-text">{{ c.content }}</p>

                    <div class="pd-c-acts">
                      <button class="pd-mini" :class="{ 'is-liked': c.liked }" type="button" @click="toggleLikeComment(c)">
                        <AppIcon name="heart" :size="13" />{{ c.likeCount || 0 }}
                      </button>
                      <button class="pd-mini" type="button" @click="startReply(c)">回复</button>
                    </div>

                    <div v-if="c.replies?.length" class="pd-replies">
                      <div v-for="r in c.replies" :key="r.id" class="pd-reply">
                        <b>{{ r.author?.nickname || '匿名' }}</b>
                        <span>{{ r.content }}</span>
                        <i>{{ fmtTime(r.createTime) }}</i>
                      </div>
                    </div>

                    <div v-if="replyTo?.id === c.id" class="pd-replybox">
                      <input
                        v-model="replyText"
                        :placeholder="'回复 @' + (replyTo.author?.nickname || '')"
                        @keyup.enter="submitReply"
                      />
                      <div class="pd-replybox-foot">
                        <button class="btn btn-ghost btn-sm" type="button" @click="replyTo = null">取消</button>
                        <button class="btn btn-dark btn-sm" type="button" @click="submitReply">回复</button>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>

          <!-- ===== 右侧栏 ===== -->
          <aside class="pd-side">
            <section class="pd-card glass">
              <div class="pd-side-head"><h3>作者</h3></div>
              <div class="pd-author">
                <span class="pd-av pd-av--lg">
                  <img v-if="post.author?.avatar" :src="post.author.avatar" alt="" />
                  <template v-else>{{ initials(post.author?.nickname) }}</template>
                </span>
                <div class="pd-author-txt">
                  <b>{{ post.author?.nickname || '匿名用户' }}</b>
                  <small>{{ fmtTime(post.createTime) }} 发布</small>
                </div>
              </div>
              <button v-if="isMine" class="btn btn-ghost btn-sm pd-wide" type="button" @click="removePost">
                <AppIcon name="trash" :size="13" />删除这篇帖子
              </button>
              <router-link v-else class="btn btn-ghost btn-sm pd-wide" to="/messages">
                <AppIcon name="message" :size="13" />去私信
              </router-link>
            </section>

            <section class="pd-card glass">
              <div class="pd-side-head"><h3>帖子数据</h3></div>
              <div class="pd-stats">
                <div><b>{{ post.likeCount || 0 }}</b><span>点赞</span></div>
                <div><b>{{ post.viewCount || 0 }}</b><span>浏览</span></div>
                <div><b>{{ comments.length }}</b><span>评论</span></div>
              </div>
            </section>

            <section v-if="topics.length" class="pd-card glass">
              <div class="pd-side-head"><h3>相关话题</h3></div>
              <div class="pd-side-topics">
                <button v-for="t in topics" :key="t" class="chip" type="button" @click="goTopic(t)">#{{ t }}</button>
              </div>
            </section>
          </aside>
        </div>
      </div>
    </main>

    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 帖子详情</span>
        <span>内容由社区用户发布，交易请走平台托管</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ---------- 顶部返回条 ---------- */
.pd-topbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.pd-back {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 8px 16px; border-radius: 999px; cursor: pointer;
  border: 1px solid rgba(255, 255, 255, .9); background: rgba(255, 255, 255, .7);
  font-size: 12px; font-weight: 600; color: #3f3f46;
  transition: all .2s var(--mkp-ease);
}
.pd-back:hover { background: #fff; color: #18181b; border-color: rgba(0, 0, 0, .08); }
.pd-crumb { font-size: 11.5px; color: #a1a1aa; }

/* ---------- 加载 / 缺失 ---------- */
.pd-skel {
  min-height: 520px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px;
  color: #a1a1aa; font-size: 12px;
}
.pd-skel-sm { height: 148px; }
.pd-miss { min-height: 420px; max-width: 760px; margin-inline: auto; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.pd-miss .btn { margin-top: 14px; }

/* ---------- 两列骨架 ---------- */
.pd-grid { display: grid; grid-template-columns: minmax(0, 1fr) 336px; gap: 20px; align-items: start; }
.pd-main { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
.pd-side { display: flex; flex-direction: column; gap: 16px; position: sticky; top: 86px; }

/* ---------- 帖子卡 ---------- */
.pd-post { padding: 26px 28px; }
.pd-head { display: flex; align-items: center; gap: 12px; }
.pd-head-txt { flex: 1; min-width: 0; }
.pd-head-txt b { display: block; font-size: 14px; font-weight: 800; color: #18181b; }
.pd-head-txt small { display: block; margin-top: 3px; font-size: 11.5px; color: #a1a1aa; }
.pd-content { margin: 18px 0 0; font-size: 15px; line-height: 1.85; color: #27272a; white-space: pre-wrap; word-break: break-word; }
.pd-topics { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.pd-imgs { display: grid; gap: 10px; margin-top: 18px; }
.pd-imgs.is-1 { grid-template-columns: 1fr; }
.pd-imgs.is-2 { grid-template-columns: 1fr 1fr; }
.pd-imgs.is-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.pd-imgs img { width: 100%; border-radius: 16px; object-fit: cover; border: 1px solid rgba(0, 0, 0, .05); background: #fff; }
.pd-imgs.is-1 img { max-height: 520px; }
.pd-imgs.is-2 img, .pd-imgs.is-3 img { height: 216px; }
.pd-actions { display: flex; align-items: center; gap: 8px; margin-top: 20px; padding-top: 16px; border-top: 1px solid rgba(0, 0, 0, .06); }
.pd-act {
  display: inline-flex; align-items: center; gap: 6px;
  height: 34px; padding: 0 14px; border-radius: 999px;
  border: 1px solid transparent; background: rgba(255, 255, 255, .7);
  font-size: 12.5px; font-weight: 600; color: #52525b; cursor: pointer;
  transition: all .2s var(--mkp-ease);
}
.pd-act:hover { background: #fff; color: #18181b; border-color: rgba(0, 0, 0, .06); }
.pd-act.is-liked { color: var(--pg-rose); background: rgba(255, 228, 230, .7); border-color: rgba(254, 205, 211, .9); }
.pd-act.is-muted { cursor: default; }
.pd-act.is-muted:hover { background: rgba(255, 255, 255, .7); color: #52525b; border-color: transparent; }

/* ---------- 头像 ---------- */
.pd-av {
  width: 44px; height: 44px; flex: none; border-radius: 50%; overflow: hidden;
  display: grid; place-items: center; font-size: 15px; font-weight: 700; color: #52525b;
  background: linear-gradient(135deg, rgba(254, 243, 199, .95), rgba(219, 234, 254, .95));
  border: 1px solid rgba(255, 255, 255, .9);
}
.pd-av img { width: 100%; height: 100%; object-fit: cover; }
.pd-av--sm { width: 34px; height: 34px; font-size: 12px; }
.pd-av--lg { width: 52px; height: 52px; font-size: 17px; }

/* ---------- 评论 ---------- */
.pd-comments { padding: 26px 28px; display: flex; flex-direction: column; gap: 18px; }
.pd-sec-head { display: flex; align-items: center; gap: 10px; }
.pd-sec-head h2 { font-size: 17px; font-weight: 900; letter-spacing: -.01em; color: #18181b; }
.pd-count {
  font-size: 11.5px; font-weight: 700; color: #71717a;
  background: rgba(255, 255, 255, .85); border: 1px solid rgba(0, 0, 0, .06);
  border-radius: 999px; padding: 2px 10px;
}
.pd-composer { padding: 16px; border-radius: 18px; background: rgba(255, 255, 255, .62); border: 1px solid rgba(255, 255, 255, .9); }
.pd-composer-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; }
.pd-hint { font-size: 11px; color: #a1a1aa; }
.pd-empty { padding: 26px 12px; }
.pd-empty .ico { width: 56px; height: 56px; border-radius: 18px; margin-bottom: 10px; font-size: 20px; }
.pd-empty h4 { font-size: 13px; }
.pd-empty p { font-size: 11.5px; }
.pd-list { display: flex; flex-direction: column; gap: 18px; }
.pd-comment { display: flex; gap: 12px; }
.pd-c-body { flex: 1; min-width: 0; }
.pd-c-head { display: flex; align-items: baseline; gap: 8px; }
.pd-c-head b { font-size: 13px; font-weight: 700; color: #27272a; }
.pd-time { font-size: 11px; color: #a1a1aa; }
.pd-c-text { margin: 6px 0 0; font-size: 13.5px; line-height: 1.7; color: #3f3f46; white-space: pre-wrap; word-break: break-word; }
.pd-c-acts { display: flex; gap: 6px; margin-top: 8px; }
.pd-mini {
  display: inline-flex; align-items: center; gap: 5px;
  height: 26px; padding: 0 11px; border-radius: 999px;
  border: 1px solid rgba(0, 0, 0, .06); background: rgba(255, 255, 255, .7);
  font-size: 11.5px; font-weight: 600; color: #71717a; cursor: pointer;
  transition: all .18s var(--mkp-ease);
}
.pd-mini:hover { background: #fff; color: #27272a; }
.pd-mini.is-liked { color: var(--pg-rose); border-color: rgba(254, 205, 211, .9); background: rgba(255, 228, 230, .7); }
.pd-replies { margin-top: 10px; padding: 10px 12px; border-radius: 14px; background: rgba(255, 255, 255, .55); border: 1px solid rgba(0, 0, 0, .04); display: flex; flex-direction: column; gap: 7px; }
.pd-reply { font-size: 12.5px; line-height: 1.6; color: #52525b; word-break: break-word; }
.pd-reply b { color: #27272a; font-weight: 700; margin-right: 6px; }
.pd-reply i { display: inline-block; margin-left: 8px; font-style: normal; font-size: 11px; color: #a1a1aa; }
.pd-replybox { margin-top: 10px; padding: 12px; border-radius: 14px; background: rgba(255, 255, 255, .7); border: 1px solid rgba(0, 0, 0, .05); }
.pd-replybox-foot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }

/* ---------- 右侧栏 ---------- */
.pd-card { padding: 20px; display: flex; flex-direction: column; gap: 14px; }
.pd-side-head { padding-bottom: 12px; border-bottom: 1px solid rgba(0, 0, 0, .06); }
.pd-side-head h3 { font-size: 13.5px; font-weight: 800; color: #18181b; }
.pd-author { display: flex; align-items: center; gap: 12px; }
.pd-author-txt { min-width: 0; }
.pd-author-txt b { display: block; font-size: 14px; font-weight: 800; color: #18181b; }
.pd-author-txt small { display: block; margin-top: 3px; font-size: 11px; color: #a1a1aa; }
.pd-wide { width: 100%; }
.pd-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; text-align: center; }
.pd-stats > div { padding: 12px 6px; border-radius: 14px; background: rgba(255, 255, 255, .7); border: 1px solid rgba(255, 255, 255, .9); }
.pd-stats b { display: block; font-size: 17px; font-weight: 900; color: #18181b; font-variant-numeric: tabular-nums; }
.pd-stats span { display: block; margin-top: 4px; font-size: 11px; color: #a1a1aa; }
.pd-side-topics { display: flex; flex-wrap: wrap; gap: 8px; }

/* ---------- 响应式 ---------- */
@media (max-width: 1120px) {
  .pd-grid { grid-template-columns: minmax(0, 1fr); }
  .pd-side { position: static; }
}
@media (max-width: 720px) {
  .pd-post, .pd-comments { padding: 20px; }
  .pd-imgs.is-2, .pd-imgs.is-3 { grid-template-columns: 1fr; }
  .pd-imgs.is-2 img, .pd-imgs.is-3 img { height: 200px; }
}
</style>
