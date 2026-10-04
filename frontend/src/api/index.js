import axios from 'axios'
import { toastError } from '../utils/toast'

const api = axios.create({ baseURL: '/api', timeout: 300000 })

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  res => {
    const { code, message, data } = res.data
    if (code !== 200) {
      toastError(message || '请求失败')
      return Promise.reject(new Error(message))
    }
    return data
  },
  err => {
    const status = err?.response?.status
    if (status === 401) {
      localStorage.clear()
      window.location.href = '/login'
    } else if (status === 403) {
      toastError('权限不足，无法访问该资源')
    } else if (status) {
      toastError(err?.response?.data?.message || `请求错误 (${status})`)
    } else {
      toastError('网络异常，请检查后端服务是否启动')
    }
    return Promise.reject(err)
  }
)

export default api

/* auth */
export const login = (username, password) =>
  api.post('/auth/login', { username, password })
export const register = (username, password, nickname) =>
  api.post('/auth/register', { username, password, nickname })

/* user */
export const getProfile = () => api.get('/user/profile')
export const updateProfile = (data) => api.put('/user/profile', data)
export const uploadAvatar = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/user/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const getFriendList = () => api.get('/user/friend/list')
export const addFriend = (friendId) => api.post(`/user/friend/add/${friendId}`)
export const acceptFriend = (friendId) => api.post(`/user/friend/accept/${friendId}`)
export const rejectFriend = (friendId) => api.post(`/user/friend/reject/${friendId}`)

/* post */
export const getPostList = () => api.get('/post/list')
/* 帖子搜索：keyword 同时匹配帖子文案与作者昵称；sort 可选 relevance / latest / hot；
   hasImage=true 只看有图 / false 只看纯文字；topic 按 #话题 精确筛 */
export const searchPosts = (keyword, page = 1, size = 10, sort = 'relevance', hasImage = null, topic = null) =>
  api.get('/post/search', { params: { keyword, page, size, sort, hasImage, topic } })
export const getPostDetail = (id) => api.get(`/post/${id}`)
export const createPost = (content, images = []) =>
  api.post('/post', { content, images })
export const likePost = (postId) => api.post(`/post/${postId}/like`)
export const deletePost = (postId) => api.delete(`/post/${postId}`)
export const uploadPostImages = (files) => {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  return api.post('/post/image/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/* chat */
export const getConversations = () => api.get('/message/conversations')
export const getConversation = (friendId) => api.get(`/message/conversation/${friendId}`)
export const sendMessage = (toUserId, content) =>
  api.post('/message/send', { toUserId, content })
export const getUnreadCount = () => api.get('/message/unread')
export const markAsRead = (friendId) => api.put(`/message/read/${friendId}`)

/* comment */
export const getPostComments = (postId) => api.get(`/post/${postId}/comments`)
export const createComment = (postId, content, parentId = null) =>
  api.post(`/post/${postId}/comments`, { content, parentId })
export const likeComment = (commentId) => api.post(`/post/comment/${commentId}/like`)
export const deleteComment = (commentId) => api.delete(`/post/comment/${commentId}`)

/* chatbot */
export const askChatbot = (question, conversationId) =>
  api.post('/chatbot/ask', { question, conversationId })
export const getChatbotConversations = () => api.get('/chatbot/conversations')
export const getChatbotMessages = (conversationId) => api.get(`/chatbot/conversation/${conversationId}/messages`)
export const deleteChatbotConversation = (conversationId) => api.delete(`/chatbot/conversation/${conversationId}`)
export const getChatbotTools = () => api.get('/chatbot/tools')

/* chatbot SSE 流式问答（fetch 解析，事件：think / token / tool / obs / done / error） */
export const askChatbotStream = (question, conversationId, handlers = {}) => {
  const token = localStorage.getItem('token')
  const params = new URLSearchParams({ question })
  if (conversationId) params.set('conversationId', conversationId)

  return fetch(`/api/chatbot/ask/stream?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream'
    }
  }).then(async (res) => {
    if (!res.ok || !res.body) throw new Error('流式请求失败')
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''

    const dispatch = (raw) => {
      let event = 'message'
      let data = ''
      for (const line of raw.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim() + '\n'
      }
      if (!data) return
      let payload
      try {
        payload = JSON.parse(data)
      } catch {
        payload = data.trim()
      }
      if (event === 'token') handlers.onToken?.(payload)
      else if (event === 'think') handlers.onThink?.(payload)
      else if (event === 'tool' || event === 'obs') handlers.onTool?.(payload)
      else if (event === 'done') handlers.onDone?.(payload)
      else if (event === 'error') handlers.onError?.(payload)
    }

    const pump = () =>
      reader.read().then(({ done, value }) => {
        if (done) return
        buf += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buf.indexOf('\n\n')) >= 0) {
          dispatch(buf.slice(0, idx))
          buf = buf.slice(idx + 2)
        }
        return pump()
      })

    return pump()
  })
}
export const getKnowledgeList = () => api.get('/chatbot/knowledge/list')
export const addKnowledge = (data) => api.post('/chatbot/knowledge', data)
export const updateKnowledge = (id, data) => api.put(`/chatbot/knowledge/${id}`, data)
export const deleteKnowledge = (id) => api.delete(`/chatbot/knowledge/${id}`)
export const batchDeleteKnowledge = (ids) => api.delete('/chatbot/knowledge/batch', { data: ids })

/* admin - user */
export const adminListUsers = (page, size, keyword) =>
  api.get('/admin/user/list', { params: { page, size, keyword } })
export const adminToggleUserStatus = (id, status) =>
  api.put(`/admin/user/${id}/status`, null, { params: { status } })
export const adminUpdateUserRole = (id, role) =>
  api.put(`/admin/user/${id}/role`, null, { params: { role } })
export const adminResetPassword = (id) =>
  api.put(`/admin/user/${id}/reset-password`)
export const adminGetUserStats = () => api.get('/admin/user/stats')

/* admin - post */
export const adminListPosts = (page, size, keyword) =>
  api.get('/admin/post/list', { params: { page, size, keyword } })
export const adminDeletePost = (id) => api.delete(`/admin/post/${id}`)
export const adminGetPostStats = () => api.get('/admin/post/stats')

/* admin - comment */
export const adminListComments = (page, size, keyword) =>
  api.get('/admin/comment/list', { params: { page, size, keyword } })
export const adminDeleteComment = (id) => api.delete(`/admin/comment/${id}`)
export const adminGetCommentStats = () => api.get('/admin/comment/stats')

/* admin - chart */
export const adminGetDailyTrend = () => api.get('/admin/post/chart/daily')

/* admin - knowledge */
export const adminGetKnowledgeList = () => api.get('/admin/chatbot/knowledge/list')
export const adminAddKnowledge = (data) => api.post('/admin/chatbot/knowledge', data)
export const adminUpdateKnowledge = (id, data) => api.put(`/admin/chatbot/knowledge/${id}`, data)
export const adminBatchDeleteKnowledge = (ids) => api.delete('/admin/chatbot/knowledge/batch', { data: ids })

/* pet service 宠物服务（大模型建议） */
export const getPetAdvice = (data) => api.post('/pet-service/advice', data)

/* 交易平台（发布单/申请/订单/评价） */
export const getPostingList = (params) => api.get('/posting/list', { params })
export const getPostingDetail = (id) => api.get(`/posting/${id}`)
export const createPosting = (data) => api.post('/posting', data)
export const offPosting = (id) => api.post(`/posting/${id}/off`)
export const uploadPostingImages = (files) => {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  return api.post('/posting/image/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const applyPosting = (postingId, data) => api.post(`/apply/posting/${postingId}`, data)
export const getReceivedApplies = () => api.get('/apply/received')
export const getSentApplies = () => api.get('/apply/sent')
export const acceptApply = (id) => api.post(`/apply/${id}/accept`)
export const rejectApply = (id) => api.post(`/apply/${id}/reject`)
export const withdrawApply = (id) => api.post(`/apply/${id}/withdraw`)

export const getMyTradeOrders = () => api.get('/trade/my')
export const payTradeOrder = (id) => api.post(`/trade/${id}/pay`)
export const startTradeOrder = (id) => api.post(`/trade/${id}/start`)
export const finishTradeOrder = (id) => api.post(`/trade/${id}/finish`)
export const completeTradeOrder = (id) => api.post(`/trade/${id}/complete`)
export const cancelTradeOrder = (id) => api.post(`/trade/${id}/cancel`)
export const refundTradeOrder = (id) => api.post(`/trade/${id}/refund`)

/* 钱包（虚拟钱包：余额 + 冻结 + 流水） */
export const getMyWallet = (logLimit = 50) => api.get('/wallet/me', { params: { logLimit } })
export const adminSetWalletBalance = (data) => api.post('/admin/wallet/set', data)

export const submitReview = (data) => api.post('/review', data)
export const getUserReviews = (userId) => api.get(`/review/user/${userId}`)
