<template>
  <section class="fw" :class="{ 'is-ready': ready }" aria-label="桌面胶片画廊">
    <div class="fw-scene" ref="stageRef" :class="{ dragging }"
      @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointercancel="onUp"
      @wheel="onWheel">
      <!-- 微微后仰的倾斜层：倾斜角度调 CFG.tilt 即可 -->
      <div class="fw-tilt"><div ref="ringRef" class="fw-ring"></div></div>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import filmWallItems from '../config/filmWall'

/* ================================================================
   FilmWall · 3D 弧形胶片画廊（桌面端主视觉）
   - 彩色竖版宠物写真沿弧线排列，无限拖拽 / 滚轮 / 惯性
   - 整列微微后仰（rotateX），形成放映胶片般的空间感
   - 性能约定：逐帧只写「值有变化」的样式（SET 去重）；不用逐帧
     模糊滤镜，避免每次栅格失效都重算模糊；离屏 / 页面不可见时暂停渲染
   ================================================================ */
const items = filmWallItems
const BASE_N = items.length

// —— 可调参数 ——
const CFG = {
  copies: 4,          // 每组图循环次数（环足够长，循环无感）
  autoSpeed: 0.12,    // 自动缓速（度/帧）
  drag: 0.3,          // 拖拽灵敏度
  wheel: 0.6,
  friction: 0.93,
  gap: 22,            // 弧上相邻间距
  fadeAt: 80,         // 超过该角度淡出（弧线收尾）
  tilt: 7             // 整列倾斜角度（度）：想更平改小，更躺改大
}
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

const stageRef = ref(null)
const ringRef = ref(null)
const dragging = ref(false)
const ready = ref(false)
const inView = ref(true)

/* 蛇形复制帧：相邻帧不出现同一张图 */
function buildFrames () {
  const ring = ringRef.value
  ring.innerHTML = ''
  const frames = []
  for (let c = 0; c < CFG.copies; c++) {
    items.forEach((d, i) => {
      const n = c * BASE_N + (c % 2 ? BASE_N - 1 - i : i)
      const src = items[n % BASE_N]
      const el = document.createElement('figure')
      el.className = 'fw-card'
      el.innerHTML = `
        <span class="fw-ph"><img src="${src.img}" alt="${src.label}" draggable="false"></span>
        <span class="fw-no">NO.${String((n % BASE_N) + 1).padStart(2, '0')}</span>
        <span class="fw-lab">${src.label}</span>`
      ring.appendChild(el)
      frames.push(el)
    })
  }
  return frames
}
let frames = []
let COUNT = 0
let STEP = 0
const rad = (d) => (d * Math.PI) / 180
const norm = (d) => ((d % 360) + 360) % 360
const wrap = (d) => { d = norm(d); return d > 180 ? d - 360 : d }

let base = 0
let vel = 0
let R = 0
let moved = false
let lastX = 0
let hoverIdx = -1
let idleUntil = 0
let lastT = performance.now()

/* 只写变化过的样式：避免逐帧做无谓的样式失效与重栅格 */
function SET (el, key, prop, value) {
  const cache = el.__fws || (el.__fws = {})
  if (cache[key] === value) return
  cache[key] = value
  el.style[prop] = value
}

/* 布局：由帧宽与夹角推弧半径；后仰会把弧线正前方整体抬高，
   这里按几何量把整环下移补偿，使可见弧线在场景内视觉居中 */
function layout () {
  const first = frames[0]
  const H = first.offsetHeight || 340
  const W = first.offsetWidth || H * 0.47
  R = (W + CFG.gap) / rad(STEP)
  const shift = R * Math.sin(rad(CFG.tilt)) * 0.78
  frames.forEach((el) => {
    el.style.left = `calc(50% - ${W / 2}px)`
    el.style.top = `calc(50% + ${shift.toFixed(1)}px)`
    el.style.marginTop = `-${H / 2}px`
  })
}

function render () {
  frames.forEach((el, i) => {
    const a = base + i * STEP
    const off = wrap(a)
    const hovered = hoverIdx === i && Math.abs(off) < 70
    el.style.transform = `rotateY(${a}deg) translateZ(${R + (hovered ? 26 : 0)}px)`
    const p = Math.cos(rad(off))
    if (Math.abs(off) > CFG.fadeAt || p <= 0) {
      SET(el, 'op', 'opacity', hovered ? '1' : '0')
      SET(el, 'vi', 'visibility', Math.abs(off) > CFG.fadeAt + 8 ? 'hidden' : 'visible')
    } else {
      SET(el, 'op', 'opacity', (0.08 + 0.92 * p).toFixed(2))
      SET(el, 'vi', 'visibility', 'visible')
    }
    el.classList.toggle('hov', hovered)
  })
}

/* 悬停不再打断自转；页面不可见或胶片滚出视口时跳过渲染 */
function tick (now) {
  raf = requestAnimationFrame(tick)
  if (document.hidden || !inView.value) { lastT = now; return }
  const dt = Math.min(now - lastT, 50) / 16.7
  lastT = now
  if (!dragging.value) {
    if (Math.abs(vel) > 0.02) {
      base += vel * dt
      vel *= Math.pow(CFG.friction, dt)
    } else if (!reduced && performance.now() > idleUntil) {
      base += CFG.autoSpeed * dt
    }
  }
  render()
}

/* ---------------- 输入 ---------------- */
function onDown (e) {
  dragging.value = true
  moved = false
  lastX = e.clientX
  stageRef.value.setPointerCapture(e.pointerId)
}
function onMove (e) {
  if (!dragging.value) return
  const dx = e.clientX - lastX
  if (Math.abs(dx) > 3) moved = true
  base += dx * CFG.drag
  vel = dx * CFG.drag
  lastX = e.clientX
}
function onUp () {
  dragging.value = false
  idleUntil = performance.now() + 2600
}
function onWheel (e) {
  // 只有水平滚动 / 按住 Shift 才旋转，普通上下滚动留给页面
  const horizontal = Math.abs(e.deltaX) > Math.abs(e.deltaY)
  if (!horizontal && !e.shiftKey) return
  e.preventDefault()
  const dy = horizontal ? e.deltaX : e.deltaY
  vel += dy * CFG.wheel * 0.05
  vel = Math.max(-7, Math.min(7, vel))
  idleUntil = performance.now() + 2600
}
function onClickSnap (e) {
  if (moved) return
  const el = e.target.closest('.fw-card')
  if (el) {
    const i = frames.indexOf(el)
    if (i < 0) return
    let target = -i * STEP
    while (target - base > 180) target -= 360
    while (target - base < -180) target += 360
    const aim = () => {
      base += (target - base) * 0.16
      if (Math.abs(target - base) > 0.05) requestAnimationFrame(aim)
    }
    aim()
    idleUntil = performance.now() + 2600
  }
}

/* ---------------- 生命周期 ---------------- */
let io = null
let raf = 0
let rs = null

onMounted(() => {
  frames = buildFrames()
  COUNT = frames.length
  STEP = 360 / COUNT
  frames.forEach((el) => {
    el.addEventListener('pointerenter', () => { hoverIdx = frames.indexOf(el) })
    el.addEventListener('pointerleave', () => { if (hoverIdx === frames.indexOf(el)) hoverIdx = -1 })
  })
  stageRef.value.addEventListener('click', onClickSnap)
  layout()
  lastT = performance.now()
  raf = requestAnimationFrame(tick)
  requestAnimationFrame(() => setTimeout(() => { ready.value = true }, 90))
  if ('IntersectionObserver' in window) {
    io = new IntersectionObserver(([en]) => { inView.value = en.isIntersecting }, { threshold: 0 })
    io.observe(stageRef.value)
  }
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  io && io.disconnect()
  window.removeEventListener('resize', onResize)
  stageRef.value && stageRef.value.removeEventListener('click', onClickSnap)
})
function onResize () {
  clearTimeout(rs)
  rs = setTimeout(() => { layout(); render() }, 150)
}
</script>

<style>
/* 全局样式（帧由 JS 动态生成，不能用 scoped）；类名均带 fw- 前缀 */
.fw {
  position: relative; width: 100%; transform-origin: 50% 46%;
  padding-top: clamp(2px, 0.8vh, 8px);
  opacity: 0; transform: scale(.958) translateY(10px);
  transition: opacity 1s var(--mkp-ease), transform 1.2s var(--mkp-ease);
}
.fw.is-ready { opacity: 1; transform: none; }
.fw-scene {
  position: relative;
  height: clamp(540px, 63vh, 660px);
  perspective: 2600px;
  perspective-origin: 50% 42%;
  overflow: visible;
  touch-action: none;
  user-select: none; -webkit-user-select: none;
  cursor: grab;
}
.fw-scene.dragging { cursor: grabbing; }
.fw-tilt {
  position: absolute; inset: 0;
  transform: rotateX(7deg);                  /* 整列微倾斜（放映胶片感）；想改角度就改这里 */
  transform-style: preserve-3d;
}
.fw-ring { position: absolute; inset: 0; transform-style: preserve-3d; }

/* 卡片：margin 必须归零（figure 默认有 40px 左右外边距，会把整环推偏） */
.fw-card {
  position: absolute; margin: 0;
  width: auto; height: min(42vh, 400px);
  aspect-ratio: 0.47;
  background: #fff;
  border-radius: 13px;
  padding: 6px;
  box-shadow: 0 1px 3px rgba(33, 29, 22, .05), 0 14px 32px rgba(33, 29, 22, .10);
  will-change: transform;
  backface-visibility: hidden;
  cursor: pointer;
}
.fw-ph { position: absolute; inset: 6px; border-radius: 8px; overflow: hidden; background: #EDE9E0; }
.fw-ph::after {
  content: ""; position: absolute; inset: auto 0 0 0; height: 72px;
  background: linear-gradient(to top, rgba(20, 14, 8, .34), transparent);
  pointer-events: none;
}
.fw-card img {
  width: 100%; height: 100%; object-fit: cover;
  transition: transform .5s cubic-bezier(.2, .8, .2, 1);
}
.fw-no {
  position: absolute; left: 14px; top: 12px; z-index: 2;
  font-size: 9.5px; letter-spacing: .26em; color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, .5);
}
.fw-lab {
  position: absolute; left: 14px; bottom: 12px; z-index: 2;
  font-size: 10px; letter-spacing: .2em; text-transform: uppercase; color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, .5);
}
.fw-card:hover img { transform: scale(1.05); }
.fw-card.hov { box-shadow: 0 6px 14px rgba(33, 29, 22, .12), 0 22px 48px rgba(33, 29, 22, .18); }

@media (max-width: 620px) {
  .fw-scene { height: clamp(340px, 46vh, 440px); perspective-origin: 50% 50%; }
  .fw-card { height: min(30vh, 275px); }
}
@media (prefers-reduced-motion: reduce) {
  .fw { opacity: 1 !important; transform: none !important; transition: none !important; }
}
</style>
