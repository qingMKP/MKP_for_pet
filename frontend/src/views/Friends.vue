<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import '../assets/mkp/mkp-pages.css'
import {
  getFriendList,
  addFriend,
  acceptFriend,
  rejectFriend,
  getConversations,
  getConversation,
  sendMessage
} from '../api'
import { fmtTime, initials } from '../utils/constants'
import { toastSuccess } from '../utils/toast'

/* ================================================================
   私信（桌面端）
   - 版式：左侧会话列表（好友请求 / 会话）+ 右侧对话流 + 底部输入
   - 数据：/api/friend/*、/api/message/*；MKP 官方（id=0）为系统通知，不可回复
   ================================================================ */
const myId = Number(localStorage.getItem('userId'))

const conversations = ref([])
const friends = ref([])
const activeFriend = ref(null)
const messages = ref([])
const input = ref('')

const ready = ref(false)
const scrollRef = ref(null)
const inputRef = ref(null)

const addOpen = ref(false)
const addId = ref('')

const pending = computed(() => friends.value.filter((f) => f.status === 0))
/* MKP 官方（id=0）的会话是系统通知，不支持回复 */
const isSystemConv = computed(() => !!activeFriend.value && activeFriend.value.id === 0)

async function loadConversations () {
  try {
    conversations.value = (await getConversations()) || []
  } catch {
    /* toast 已提示 */
  }
}

async function loadFriends () {
  try {
    friends.value = (await getFriendList()) || []
  } catch {
    /* toast 已提示 */
  }
}

function scrollBottom () {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

watch(() => messages.value.length, scrollBottom)

async function openConversation (f) {
  activeFriend.value = { id: f.id, nickname: f.nickname, avatar: f.avatar }
  try {
    messages.value = (await getConversation(f.id)) || []
  } catch {
    messages.value = []
  }
  f.unreadCount = 0
  scrollBottom()
  nextTick(() => inputRef.value?.focus())
}

async function send () {
  const text = input.value.trim()
  if (!text || !activeFriend.value || isSystemConv.value) return
  input.value = ''
  try {
    const msg = await sendMessage(activeFriend.value.id, text)
    if (msg) messages.value.push(msg)
  } catch {
    /* toast 已提示 */
  }
}

async function submitAdd () {
  const id = Number(addId.value.trim())
  if (!id) return
  try {
    await addFriend(id)
    toastSuccess('好友申请已发送')
    addOpen.value = false
    addId.value = ''
  } catch {
    /* toast 已提示 */
  }
}

async function accept (f) {
  try {
    await acceptFriend(f.id)
    toastSuccess('已接受好友申请')
    await Promise.all([loadFriends(), loadConversations()])
  } catch {
    /* toast 已提示 */
  }
}

async function reject (f) {
  try {
    await rejectFriend(f.id)
    await loadFriends()
  } catch {
    /* toast 已提示 */
  }
}

onMounted(async () => {
  await Promise.all([loadConversations(), loadFriends()])
  ready.value = true
  const c = conversations.value[0]
  if (c) await openConversation(c)
})
</script>

<template>
  <div class="mkp-page pg-msg">
    <div class="bg-layer" aria-hidden="true">
      <div class="blob b1"></div><div class="blob b2"></div><div class="blob b3"></div>
    </div>

    <MkpTopNav active="/messages" :show-search="false" :show-action="false" />

    <main id="pm-main" class="pm-main">
      <div class="pg-wrap">
        <div class="msg-shell glass">
          <!-- ===== 左：会话 ===== -->
          <aside class="msg-side" aria-label="会话列表">
            <div class="msg-side-head">
              <h2>私信</h2>
              <button class="btn btn-dark btn-sm" type="button" @click="addOpen = true">
                <AppIcon name="plus" :size="13" />添加好友
              </button>
            </div>

            <template v-if="pending.length">
              <p class="msg-label">好友请求 <i>{{ pending.length }}</i></p>
              <div class="msg-reqs">
                <div v-for="f in pending" :key="f.id" class="msg-req">
                  <span class="msg-av">
                    <img v-if="f.avatar" :src="f.avatar" alt="" />
                    <template v-else>{{ initials(f.nickname) }}</template>
                  </span>
                  <span class="msg-req-name">{{ f.nickname || '用户' }}</span>
                  <button class="msg-ico is-ok" type="button" aria-label="接受" @click="accept(f)">
                    <AppIcon name="check" :size="15" />
                  </button>
                  <button class="msg-ico" type="button" aria-label="拒绝" @click="reject(f)">
                    <AppIcon name="x" :size="15" />
                  </button>
                </div>
              </div>
            </template>

            <p class="msg-label">会话</p>
            <div class="msg-convs">
              <button
                v-for="f in conversations"
                :key="f.id"
                class="msg-conv"
                :class="{ 'is-active': activeFriend?.id === f.id }"
                type="button"
                @click="openConversation(f)"
              >
                <span class="msg-av">
                  <img v-if="f.avatar" :src="f.avatar" alt="" />
                  <template v-else>{{ initials(f.nickname) }}</template>
                </span>
                <span class="msg-conv-name">{{ f.nickname || '用户' }}</span>
                <span v-if="f.unreadCount" class="msg-badge">{{ f.unreadCount > 99 ? '99+' : f.unreadCount }}</span>
              </button>
              <p v-if="ready && !conversations.length" class="msg-hint">暂无会话，先添加好友吧</p>
            </div>
          </aside>

          <!-- ===== 右：对话 ===== -->
          <section class="msg-main">
            <header class="msg-head">
              <span v-if="activeFriend" class="msg-av msg-av--lg">
                <img v-if="activeFriend.avatar" :src="activeFriend.avatar" alt="" />
                <template v-else>{{ initials(activeFriend.nickname) }}</template>
              </span>
              <div class="msg-head-txt">
                <b>{{ activeFriend ? activeFriend.nickname : '私信' }}</b>
                <small v-if="isSystemConv">系统通知 · 不可回复</small>
                <small v-else-if="activeFriend">好友会话</small>
                <small v-else>从左侧选择一个会话开始聊天</small>
              </div>
            </header>

            <div ref="scrollRef" class="msg-body">
              <div v-if="!activeFriend" class="pg-empty">
                <div class="ico" aria-hidden="true"><AppIcon name="message" :size="22" /></div>
                <h4>还没有打开会话</h4>
                <p>从左侧选择好友，或先添加好友</p>
              </div>

              <div v-else-if="ready && !messages.length" class="pg-empty">
                <div class="ico" aria-hidden="true">🐾</div>
                <h4>你们还没有聊过</h4>
                <p>说点什么吧</p>
              </div>

              <div
                v-for="m in messages"
                :key="m.id"
                class="bubble"
                :class="{ 'is-mine': m.fromUserId === myId }"
              >
                <div class="bubble-content">{{ m.content }}</div>
                <div class="bubble-time">{{ fmtTime(m.createTime) }}</div>
              </div>
            </div>

            <div class="msg-composer">
              <textarea
                ref="inputRef"
                v-model="input"
                rows="1"
                :disabled="!activeFriend || isSystemConv"
                :placeholder="isSystemConv ? '系统通知不可回复' : '输入消息，Enter 发送'"
                @keydown.enter.exact.prevent="send"
              ></textarea>
              <button
                class="btn btn-dark"
                type="button"
                :disabled="!activeFriend || isSystemConv || !input.trim()"
                @click="send"
              >
                <AppIcon name="send" :size="14" />发送
              </button>
            </div>
          </section>
        </div>
      </div>
    </main>

    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 私信</span>
        <span>请勿私下转账，交易请走平台托管</span>
      </div>
    </footer>

    <!-- 添加好友 -->
    <div v-if="addOpen" class="pg-scrim" @click.self="addOpen = false">
      <div class="pg-modal">
        <h3>添加好友</h3>
        <label class="fld">
          <span>对方用户 ID</span>
          <input v-model="addId" type="number" placeholder="输入好友的用户 ID" @keyup.enter="submitAdd" />
        </label>
        <p class="fld-hint">通过用户 ID 发送好友申请，对方同意后就能开始私信。</p>
        <div class="pg-modal-foot">
          <button class="btn btn-ghost" type="button" @click="addOpen = false">取消</button>
          <button class="btn btn-dark" type="button" @click="submitAdd">发送申请</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 外壳：一屏高的双栏面板 ---------- */
.msg-shell {
  height: calc(100vh - 220px);
  min-height: 520px;
  display: flex;
  overflow: hidden;
  padding: 0;
}

/* ---------- 左：会话列表 ---------- */
.msg-side {
  width: 316px; flex: none; min-width: 0;
  display: flex; flex-direction: column; gap: 10px;
  padding: 18px 14px 18px 18px;
  border-right: 1px solid rgba(0, 0, 0, .06);
}
.msg-side-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-bottom: 14px; border-bottom: 1px solid rgba(0, 0, 0, .06); }
.msg-side-head h2 { font-size: 17px; font-weight: 900; letter-spacing: -.01em; color: #18181b; }
.msg-label { margin: 4px 0 0; padding-left: 4px; font-size: 11px; font-weight: 600; color: #a1a1aa; }
.msg-label i { font-style: normal; color: var(--pg-rose); }

.msg-reqs { display: flex; flex-direction: column; gap: 6px; }
.msg-req {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 14px;
  background: rgba(255, 255, 255, .75); border: 1px solid rgba(0, 0, 0, .05);
}
.msg-req-name { flex: 1; min-width: 0; font-size: 12.5px; font-weight: 600; color: #27272a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.msg-ico {
  width: 26px; height: 26px; flex: none; border-radius: 9px; border: 1px solid rgba(0, 0, 0, .08);
  background: rgba(255, 255, 255, .9); color: #52525b; display: grid; place-items: center; cursor: pointer;
  transition: all .18s var(--mkp-ease);
}
.msg-ico:hover { background: #fff; color: #18181b; }
.msg-ico.is-ok { background: rgba(220, 252, 231, .9); border-color: rgba(187, 247, 208, .9); color: #166534; }
.msg-ico.is-ok:hover { background: #dcfce7; }

.msg-convs { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; padding-right: 2px; }
.msg-conv {
  display: flex; align-items: center; gap: 10px; width: 100%;
  padding: 9px 10px; border: 1px solid transparent; border-radius: 14px;
  background: transparent; text-align: left; cursor: pointer;
  transition: background .18s var(--mkp-ease), border-color .18s var(--mkp-ease);
}
.msg-conv:hover { background: rgba(255, 255, 255, .8); border-color: rgba(0, 0, 0, .05); }
.msg-conv.is-active { background: rgba(255, 255, 255, .95); border-color: rgba(0, 0, 0, .08); box-shadow: 0 6px 18px rgba(0, 0, 0, .05); }
.msg-conv-name { flex: 1; min-width: 0; font-size: 13px; font-weight: 600; color: #27272a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.msg-badge {
  min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px;
  background: var(--pg-rose); color: #fff; font-size: 10.5px; font-weight: 700;
  display: grid; place-items: center; flex: none;
}
.msg-hint { padding: 18px 0; text-align: center; font-size: 11.5px; color: #a1a1aa; }

/* ---------- 头像 ---------- */
.msg-av {
  width: 32px; height: 32px; flex: none; border-radius: 50%; overflow: hidden;
  display: grid; place-items: center; font-size: 12px; font-weight: 700; color: #52525b;
  background: linear-gradient(135deg, rgba(254, 243, 199, .95), rgba(219, 234, 254, .95));
  border: 1px solid rgba(255, 255, 255, .9);
}
.msg-av img { width: 100%; height: 100%; object-fit: cover; }
.msg-av--lg { width: 40px; height: 40px; font-size: 14px; }

/* ---------- 右：对话 ---------- */
.msg-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.msg-head {
  display: flex; align-items: center; gap: 12px; flex: none;
  padding: 14px 22px; border-bottom: 1px solid rgba(0, 0, 0, .06);
  background: rgba(255, 255, 255, .5);
}
.msg-head-txt { min-width: 0; }
.msg-head-txt b { display: block; font-size: 14px; font-weight: 800; color: #18181b; }
.msg-head-txt small { display: block; margin-top: 2px; font-size: 11px; color: #a1a1aa; }

.msg-body {
  flex: 1; min-height: 0; overflow-y: auto;
  padding: 22px; display: flex; flex-direction: column; gap: 14px;
}
.msg-body .pg-empty { margin: auto; }

.bubble { max-width: 66%; display: flex; flex-direction: column; gap: 4px; align-self: flex-start; }
.bubble.is-mine { align-self: flex-end; align-items: flex-end; }
.bubble-content {
  padding: 10px 14px; font-size: 13px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
  background: rgba(255, 255, 255, .92); color: #27272a;
  border: 1px solid rgba(0, 0, 0, .05);
  border-radius: 6px 16px 16px 16px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, .03);
}
.bubble.is-mine .bubble-content {
  background: #1c1813; color: #fff; border-color: transparent;
  border-radius: 16px 16px 6px 16px;
  box-shadow: 0 8px 20px rgba(28, 24, 19, .18);
}
.bubble-time { font-size: 10.5px; color: #a1a1aa; }

.msg-composer {
  flex: none; display: flex; align-items: flex-end; gap: 10px;
  padding: 14px 22px 18px; border-top: 1px solid rgba(0, 0, 0, .06);
  background: rgba(255, 255, 255, .5);
}
.msg-composer textarea { flex: 1; min-height: 42px; max-height: 120px; }

/* ---------- 响应式 ---------- */
@media (max-width: 1080px) { .msg-side { width: 260px; } .bubble { max-width: 80%; } }
@media (max-width: 820px) {
  .msg-shell { flex-direction: column; height: auto; min-height: 620px; }
  .msg-side { width: 100%; border-right: none; border-bottom: 1px solid rgba(0, 0, 0, .06); }
  .msg-convs { max-height: 180px; }
  .msg-main { min-height: 420px; }
}
</style>
