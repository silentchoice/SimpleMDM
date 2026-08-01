import axios from 'axios'
import { ElMessage } from 'element-plus'
import { chineseError } from './labels'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// Request interceptor: attach JWT token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor: unwrap {code, message, data} envelope
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code !== 200) {
      const message = chineseError({ message: body.message }, '请求失败')
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    const msg = chineseError(error, error.response ? '请求处理失败' : '网络请求失败')
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
