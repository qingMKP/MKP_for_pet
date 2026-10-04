/* 一次性滚动入场：观察 [data-mkp-reveal]，进入视口后加 .mkp-in */
export function initMkpReveal(root = document) {
  const els = Array.from(root.querySelectorAll('[data-mkp-reveal]'))
  const done = new Set()
  const show = (el) => { el.classList.add('mkp-in'); done.add(el) }
  if (els.length === 0) return () => {}
  if (!('IntersectionObserver' in window)) { els.forEach(show); return () => {} }

  // 为组内卡片设置阶梯延迟
  root.querySelectorAll('[data-mkp-group]').forEach((g) => {
    const kids = Array.from(g.querySelectorAll('[data-mkp-reveal]'))
    kids.forEach((k, i) => {
      if (!k.style.getPropertyValue('--d')) k.style.setProperty('--d', Math.min(i * 60, 360) + 'ms')
    })
  })

  const io = new IntersectionObserver(
    (entries) => {
      for (const en of entries) {
        if (en.isIntersecting) { show(en.target); io.unobserve(en.target) }
      }
    },
    { threshold: 0.06, rootMargin: '0px 0px -4% 0px' }
  )
  els.forEach((el) => io.observe(el))
  return () => io.disconnect()
}
