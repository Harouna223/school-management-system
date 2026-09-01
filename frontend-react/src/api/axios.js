import axios from 'axios'
import loadingService from '../services/loadingService'

/**
 * Client Axios centralisé avec interceptions :
 * - injection automatique du JWT
 * - rafraîchissement du token à l'expiration
 * - déconnexion sur jeton invalide
 * - loader global pendant les requêtes
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const isAuthEndpoint = config.url?.includes('/auth/')
  if (!isAuthEndpoint) {
    loadingService.start()
    config._trackedLoading = true
  }
  return config
})

let isRefreshing = false
let waitingQueue = []

api.interceptors.response.use(
  (response) => {
    if (response.config._trackedLoading) {
      loadingService.stop()
      response.config._trackedLoading = false
    }
    return response
  },
  async (error) => {
    if (error.config?._trackedLoading) {
      loadingService.stop()
      error.config._trackedLoading = false
    }
    const originalRequest = error.config
    const status = error.response?.status
    const isAuthEndpoint = originalRequest?.url?.includes('/auth/')

    if (status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            waitingQueue.push({
              resolve: () => resolve(api(originalRequest)),
              reject,
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true
        try {
          const { data } = await axios.post(
            `${import.meta.env.VITE_API_URL || '/api'}/auth/refresh`,
            { refreshToken },
          )
          if (!data?.data?.accessToken) {
            throw new Error('Réponse de rafraîchissement invalide')
          }
          localStorage.setItem('accessToken', data.data.accessToken)
          localStorage.setItem('refreshToken', data.data.refreshToken)
          waitingQueue.forEach((entry) => entry.resolve())
          waitingQueue = []
          originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`
          return api(originalRequest)
        } catch (refreshError) {
          localStorage.removeItem('accessToken')
          localStorage.removeItem('refreshToken')
          localStorage.removeItem('user')
          waitingQueue.forEach((entry) => entry.reject(refreshError))
          waitingQueue = []
          window.dispatchEvent(new CustomEvent('auth:logout'))
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }
      // Aucun refresh token : la session est expirée, on déconnecte
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('user')
      window.dispatchEvent(new CustomEvent('auth:logout'))
    }
    return Promise.reject(error)
  },
)

/**
 * Extrait le message d'erreur utilisable depuis une réponse Axios.
 */
export function extractError(error) {
  const data = error?.response?.data
  if (data?.message) return data.message
  if (data?.data && typeof data.data === 'object') {
    return Object.values(data.data).join(', ')
  }
  return 'Une erreur est survenue'
}

export default api