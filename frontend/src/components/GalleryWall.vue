<template>
  <section class="gw" aria-label="精选画廊">
    <div
      ref="stageRef"
      class="gw-stage"
      tabindex="0"
      role="group"
      aria-label="精选画廊 3D 轮播（拖拽 / 滚轮 / 左右方向键）"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
      @pointerleave="clearTilt"
      @wheel="onWheel"
      @keydown="onKey"
      @click="onClick"
    >
      <div ref="trackRef" class="gw-track"></div>
      <div ref="dotsRef" class="gw-dots"></div>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import galleryItems from '../config/gallery'

/* ================================================================
   GalleryWall · 精选画廊横向 3D cover-flow（桌面端）
   - 无限缓转（鼠标悬停在轮播上也不会暂停）· 拖拽惯性 · 滚轮 · ← →
     · 圆点导航 · 居中卡悬停 3D 倾斜
   - 性能约定：单个 rAF 循环、逐帧只写「值有变化」的样式；压暗用
     opacity 叠层而不是模糊滤镜；离屏 / 窗口不可见时暂停渲染
   - 数据维护：src/config/gallery.js（远程图失败自动降级本地图）
   ================================================================ */
const items = galleryItems
const N = items.length
/* 图片加载失败时的兜底（每个位置各一张，避免破版） */
const FALLBACK = [
  '/mkp/p06.jpg', '/mkp/p09.jpg', '/mkp/p08.jpg', '/mkp/p05.jpg',
  '/mkp/p01.png', '/mkp/p02.png', '/mkp/p07.png', '/mkp/p10.png'
]

/* —— 可调参数 —— */
const CFG = {
  auto: 0.12,     // 自动缓转速度：张/秒（0 则关闭自动转动）
  stepX: 164,     // 相邻卡片横向位移(px)
  stepZ: 98,      // 相邻卡片景深(px)
  maxRot: 30,     // 侧卡最大 rotateY(度)
  dragPerCard: 200, // 拖拽灵敏度：多少 px 折算一张
  wheelGap: 220,  // 滚轮换张的最小间隔(ms)
  minScale: 0.78  // 最远卡片缩放下限
}
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

const stageRef = ref(null)
const trackRef = ref(null)
const dotsRef = ref(null)

let cards = []
let dims = []
let inners = []
let dotEls = []
let raf = 0
let io = null
let inView = true
let lastT = performance.now()

let cur = 0          // 当前连续位置（可为小数）
let target = 0       // 目标位置（自动缓转 / 拖拽 / 换张都写它）
let vel = 0          // 拖拽释放后的惯性
let dragging = false
let moved = false
let startX = 0
let startTarget = 0
let lastX = 0
let lastMT = 0
let lastStep = 0
let hoverIdx = -1
let hovEl = null
let tiltX = 0
let tiltY = 0
let tiltRaf = 0

const wrapOff = (d) => {
  const n = ((d % N) + N) % N
  return n > N / 2 ? n - N : n
}

function build () {
  const track = trackRef.value
  track.innerHTML = items.map((it, i) => `
    <figure class="gw-card" data-i="${i}">
      <div class="gw-inner">
        <img src="${it.img}" alt="${(it.t1 || '')} ${(it.t2 || '')}" draggable="false" onerror="window.__gwImgFail(this)">
        <span class="gw-dim"></span>
      </div>
      <span class="gw-ring"></span>
      <span class="gw-no">N° ${String(i + 1).padStart(2, '0')}</span>
      <span class="gw-tx">
        <span class="gw-kick">MKP · Featured Gallery</span>
        <span class="gw-tt">${it.t1 || ''}${it.t2 ? '<br>' + it.t2 : ''}</span>
        <span class="gw-ss">${it.sub || ''}</span>
      </span>
    </figure>`).join('')
  dotsRef.value.innerHTML = items
    .map((_, i) => `<button type="button" data-i="${i}" aria-label="第 ${i + 1} 张"></button>`)
    .join('')
  window.__gwImgFail = (img) => {
    const i = +img.closest('.gw-card').dataset.i
    const lv = +(img.dataset.lv || 0)
    if (lv === 0) {
      img.dataset.lv = '1'
      img.src = FALLBACK[i % FALLBACK.length]
    } else {
      img.closest('.gw-card').classList.add('gw-noimg')
    }
  }
  cards = Array.from(track.children)
  dims = cards.map((c) => c.querySelector('.gw-dim'))
  inners = cards.map((c) => c.querySelector('.gw-inner'))
  dotEls = Array.from(dotsRef.value.children)
}

function paint () {
  for (let i = 0; i < N; i++) {
    const off = wrapOff(i - cur)
    const abs = Math.abs(off)
    const x = off * CFG.stepX
    const z = -Math.min(abs, 5) * CFG.stepZ
    const ry = Math.max(-CFG.maxRot, Math.min(CFG.maxRot, -off * 18))
    const sc = Math.max(CFG.minScale, 1 - abs * 0.055) + (i === hoverIdx ? 0.035 : 0)
    const c = cards[i]
    c.style.transform = `translate3d(${x.toFixed(1)}px,0,${z.toFixed(0)}px) rotateY(${ry.toFixed(2)}deg) scale(${sc.toFixed(3)})`
    let o = abs * 0.3 - 0.05
    if (o < 0) o = 0
    if (o > 0.62) o = 0.62
    if (dims[i].__o !== o) {
      dims[i].style.opacity = o.toFixed(2)
      dims[i].__o = o
    }
    c.style.zIndex = String(300 - Math.round(abs * 10))
    const isC = abs < 0.5
    if (c.__c !== isC) {
      c.classList.toggle('is-center', isC)
      c.__c = isC
    }
  }
  const ni = ((Math.round(cur) % N) + N) % N
  for (let k = 0; k < dotEls.length; k++) {
    if (dotEls[k].__on !== (k === ni)) {
      dotEls[k].classList.toggle('on', k === ni)
      dotEls[k].__on = (k === ni)
    }
  }
}

function loop (t) {
  let dt = (t - lastT) / 1000
  if (dt > 0.05) dt = 0.05
  if (dt < 0) dt = 0
  lastT = t
  if (dragging) {
    cur = target
  } else {
    if (vel > 0.003 || vel < -0.003) {
      target += vel * dt
      vel *= Math.pow(0.02, dt)
    } else {
      vel = 0
    }
    if (!reduced && inView) target += CFG.auto * dt
    cur += (target - cur) * (1 - Math.pow(0.0006, dt))
    if (Math.abs(target - cur) < 0.0005) cur = target
  }
  paint()
  raf = requestAnimationFrame(loop)
}

/* ---------------- 悬停 3D 倾斜 ---------------- */
function clearTilt () {
  if (hoverIdx >= 0 && inners[hoverIdx]) inners[hoverIdx].style.transform = ''
  hoverIdx = -1
  hovEl = null
}
function onMove (e) {
  if (dragging) {
    const dx = e.clientX - startX
    if (Math.abs(dx) > 4) moved = true
    const now = performance.now()
    const ddx = e.clientX - lastX
    const ddt = Math.max(now - lastMT, 1)
    vel = (-ddx / CFG.dragPerCard) / (ddt / 1000)
    if (vel > 8) vel = 8
    if (vel < -8) vel = -8
    lastX = e.clientX
    lastMT = now
    target = startTarget - dx / CFG.dragPerCard
    return
  }
  const el = e.target && e.target.closest ? e.target.closest('.gw-card') : null
  if (!el) {
    clearTilt()
    return
  }
  const idx = +el.dataset.i
  if (idx !== hoverIdx && hoverIdx >= 0 && inners[hoverIdx]) inners[hoverIdx].style.transform = ''
  const r = el.getBoundingClientRect()
  hoverIdx = idx
  hovEl = inners[idx]
  tiltX = -(((e.clientY - r.top) / r.height) - 0.5) * 9
  tiltY = (((e.clientX - r.left) / r.width) - 0.5) * 13
  if (!tiltRaf) {
    tiltRaf = requestAnimationFrame(() => {
      tiltRaf = 0
      if (hovEl) hovEl.style.transform = `rotateX(${tiltX.toFixed(2)}deg) rotateY(${tiltY.toFixed(2)}deg) translateZ(14px)`
    })
  }
}
function onDown (e) {
  if (e.pointerType === 'mouse' && e.button !== 0) return
  dragging = true
  moved = false
  vel = 0
  startX = e.clientX
  lastX = e.clientX
  lastMT = performance.now()
  startTarget = target
  try { stageRef.value.setPointerCapture(e.pointerId) } catch (err) { /* ignore */ }
  stageRef.value.classList.add('gw-grab')
}
function onUp () {
  if (!dragging) return
  dragging = false
  stageRef.value.classList.remove('gw-grab')
  target = Math.round(target)
}
/* 滚轮：横向（触控板）或 Shift+滚轮才转动；纵向滚动一律放行给页面 */
function onWheel (e) {
  const horiz = Math.abs(e.deltaX) > Math.abs(e.deltaY)
  if (!horiz && !e.shiftKey) return
  e.preventDefault()
  const now = performance.now()
  if (now - lastStep < CFG.wheelGap) return
  lastStep = now
  const d = horiz ? e.deltaX : e.deltaY
  if (d) target = Math.round(target) + (d > 0 ? 1 : -1)
}
function onKey (e) {
  if (e.key === 'ArrowLeft') { target = Math.round(target) - 1; e.preventDefault() }
  if (e.key === 'ArrowRight') { target = Math.round(target) + 1; e.preventDefault() }
}
function onClick (e) {
  if (moved) { moved = false; return }
  const el = e.target.closest ? e.target.closest('.gw-card') : null
  if (!el) return
  const off = wrapOff(+el.dataset.i - target)
  if (Math.abs(off) > 0.5) target = Math.round(target) + Math.round(off)
}
function onDot (e) {
  const b = e.target.closest ? e.target.closest('[data-i]') : null
  if (!b) return
  const i = +b.dataset.i
  target = i + Math.round((target - i) / N) * N
}

onMounted(() => {
  build()
  paint()
  lastT = performance.now()
  raf = requestAnimationFrame(loop)
  if ('IntersectionObserver' in window) {
    io = new IntersectionObserver(([en]) => { inView = en.isIntersecting }, { threshold: 0 })
    io.observe(stageRef.value)
  }
})
onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  if (tiltRaf) cancelAnimationFrame(tiltRaf)
  if (io) io.disconnect()
  delete window.__gwImgFail
})
</script>
<style>
/* 注意：卡片 DOM 由 JS 动态 innerHTML 生成，动态节点没有 data-v 属性，
   所以这里不能用 scoped；所有类名统一 gw- 前缀，避免污染全局。 */

/* ============ GalleryWall · 精选画廊横向 3D cover-flow ============ */
.gw {
  position: relative;
  width: 100%;
  height: 330px;
  align-self: center;      /* 与左侧 Hero 白卡垂直居中对齐 */
}

.gw-stage {
  position: relative;
  height: 330px;
  perspective: 1400px;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
  touch-action: pan-y;
  outline: none;
  /* 两端渐隐：远处卡片柔和融进页面底色，而不是被硬切 */
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 8%, #000 92%, transparent 100%);
  mask-image: linear-gradient(90deg, transparent 0, #000 8%, #000 92%, transparent 100%);
}
.gw-stage.gw-grab { cursor: grabbing; }
.gw-stage:focus-visible {
  outline: 2px solid var(--mkp-accent);
  outline-offset: 6px;
  border-radius: 26px;
}

.gw-track {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0;
  height: 0;
  transform-style: preserve-3d;
}

.gw-card {
  position: absolute;
  left: -101px;
  top: -139px;
  width: 202px;
  height: 278px;
  border-radius: 16px;
  background: #111;
  box-shadow: 0 14px 34px rgba(28, 24, 19, .16);
  transform-style: preserve-3d;
  will-change: transform, opacity;
}
.gw-inner {
  position: absolute;
  inset: 0;
  border-radius: 16px;
  overflow: hidden;
  transform-style: preserve-3d;
  transition: transform .5s var(--mkp-ease);
}
.gw-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
}
.gw-dim {
  position: absolute;
  inset: 0;
  background: #100e0b;
  opacity: 0;
  pointer-events: none;
}
.gw-ring {
  position: absolute;
  inset: 0;
  border-radius: 16px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .14);
  pointer-events: none;
  transition: box-shadow .3s var(--mkp-ease);
}
.gw-card:hover .gw-ring,
.gw-card.is-center .gw-ring {
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, .88), 0 20px 46px rgba(28, 24, 19, .26);
}
.gw-no {
  position: absolute;
  top: 12px;
  left: 12px;
  font-size: 9.5px;
  letter-spacing: .16em;
  color: rgba(255, 255, 255, .85);
  background: rgba(0, 0, 0, .26);
  border: 1px solid rgba(255, 255, 255, .24);
  border-radius: 999px;
  padding: 2px 8px;
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
}
.gw-tx {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px 14px 13px;
  color: #fff;
  background: linear-gradient(to top, rgba(0, 0, 0, .84), rgba(0, 0, 0, .26) 56%, transparent);
}
.gw-kick {
  display: block;
  font-size: 8.5px;
  letter-spacing: .24em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, .62);
}
.gw-tt {
  display: block;
  font-size: 17px;
  font-weight: 800;
  line-height: 1.05;
  margin-top: 4px;
  letter-spacing: .01em;
}
.gw-ss {
  display: block;
  font-size: 10.5px;
  color: rgba(255, 255, 255, .68);
  margin-top: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 远程图与本地降级图都失败时的兜底：不破版 */
.gw-card.gw-noimg {
  background: radial-gradient(120% 90% at 22% 0%, rgba(228, 87, 46, .26), transparent 58%),
    linear-gradient(165deg, #241c14, #100d09 80%);
}
.gw-card.gw-noimg img { display: none; }

.gw-dots {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 4px;
  display: flex;
  gap: 6px;
  justify-content: center;
  z-index: 400;
}
.gw-dots button {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: none;
  background: rgba(9, 9, 11, .16);
  padding: 0;
  cursor: pointer;
  transition: width .2s var(--mkp-ease), background .2s var(--mkp-ease);
}
.gw-dots button:hover { background: rgba(9, 9, 11, .34); }
.gw-dots button.on {
  width: 18px;
  border-radius: 999px;
  background: var(--mkp-ink);
}

@media (max-width: 1180px) {
  .gw { height: 300px; }
  .gw-stage { height: 300px; }
}
@media (prefers-reduced-motion: reduce) {
  .gw-inner { transition: none; }
}
</style>
