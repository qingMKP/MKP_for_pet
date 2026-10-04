import { reactive } from 'vue'

export const toasts = reactive([])
let seed = 0

export function toast(message, type = 'info') {
  const t = { id: ++seed, message, type }
  toasts.push(t)
  setTimeout(() => {
    const i = toasts.findIndex((x) => x.id === t.id)
    if (i >= 0) toasts.splice(i, 1)
  }, 2600)
}

export function toastError(message) {
  toast(message, 'error')
}

export function toastSuccess(message) {
  toast(message, 'success')
}
