// 业务枚举与展示格式化（与后端 TradeConstants 对齐）

export const CATEGORY = { 1: '领养', 2: '寄养', 3: '出售' }
export const PRICE_UNIT = { 1: '总价', 2: '/天', 3: '/小时', 4: '/分钟' }
export const GENDER = { 0: '未知', 1: '公', 2: '母' }

export const POSTING_STATUS = { 1: '开放中', 2: '已匹配', 3: '已下架', 4: '已关闭' }
export const APPLY_STATUS = { 0: '待确认', 1: '已接受', 2: '已拒绝', 3: '已撤回' }
export const ORDER_STATUS = {
  0: '待支付',
  1: '托管中',
  2: '履约中',
  3: '待结算',
  4: '已完成',
  5: '已取消',
  6: '已退款'
}

// 状态 → pill 色调（'' 为中性灰）
export const POSTING_STATUS_TONE = { 1: 'success', 2: 'primary', 3: '', 4: 'danger' }
export const APPLY_STATUS_TONE = { 0: 'warning', 1: 'success', 2: 'danger', 3: '' }
export const ORDER_STATUS_TONE = {
  0: 'warning',
  1: 'primary',
  2: 'primary',
  3: 'warning',
  4: 'success',
  5: 'danger',
  6: 'danger'
}

// 钱包流水业务类型（与后端 TradeConstants 对齐）
export const WALLET_BIZ = { 1: '手动调整', 2: '支付冻结', 3: '结算入账', 4: '退款解冻' }
export const WALLET_BIZ_TONE = { 1: '', 2: 'warning', 3: 'success', 4: 'danger' }

export function fmtMoney(v) {
  const n = Number(v || 0)
  const sign = n < 0 ? '-' : ''
  return sign + '¥' + Math.abs(n).toFixed(2)
}

export function fmtPrice(price, unit) {
  if (price == null || price === '') return '未定价'
  const u = PRICE_UNIT[unit]
  const suffix = u && u !== '总价' ? u : ''
  return '¥' + Number(price) + suffix
}

export function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return min + ' 分钟前'
  const h = Math.floor(min / 60)
  if (h < 24) return h + ' 小时前'
  const day = Math.floor(h / 24)
  if (day < 7) return day + ' 天前'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`
}

export function fmtDateTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(
    d.getHours()
  )}:${p(d.getMinutes())}`
}

export function initials(name) {
  if (!name) return '?'
  return name.slice(0, 1).toUpperCase()
}
