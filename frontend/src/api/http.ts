import axios, { type AxiosAdapter, type AxiosInstance } from 'axios'
import { useAuthStore } from '../stores/auth'
import type { ApiEnvelope, ApiError } from '../types'

export interface HttpClient {
  get<T>(url: string): Promise<T>
  post<T>(url: string, body?: unknown): Promise<T>
  put<T>(url: string, body?: unknown): Promise<T>
  patch<T>(url: string, body?: unknown): Promise<T>
  delete<T>(url: string): Promise<T>
}

export interface HttpOptions {
  adapter?: AxiosAdapter
  requestId?: () => string
}

function makeRequestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object'
}

function isApiEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return isRecord(value) && typeof value.code === 'number' && typeof value.message === 'string'
    && 'data' in value && typeof value.requestId === 'string'
}

function apiError(response: unknown, status?: number): ApiError {
  if (isRecord(response) && typeof response.code === 'number' && typeof response.message === 'string') {
    return {
      code: response.code,
      message: response.message || `Request failed${status ? ` (HTTP ${status})` : ''}`,
      requestId: typeof response.requestId === 'string' ? response.requestId : undefined,
      status
    }
  }
  return { message: status && status >= 400 ? `Request failed (HTTP ${status})` : 'Malformed API response', status }
}

export function createHttpClient(options: HttpOptions = {}): HttpClient {
  const client: AxiosInstance = axios.create({ baseURL: '/api', adapter: options.adapter, validateStatus: () => true })
  const nextRequestId = options.requestId ?? makeRequestId

  client.interceptors.request.use((config) => {
    const token = useAuthStore().session?.accessToken
    config.headers.set('X-Request-Id', nextRequestId())
    if (token) config.headers.set('Authorization', `Bearer ${token}`)
    return config
  })

  async function request<T>(method: 'get' | 'post' | 'put' | 'patch' | 'delete', url: string, data?: unknown): Promise<T> {
    try {
      const response = await client.request<ApiEnvelope<T>>({ method, url, data })
      const envelope: unknown = response.data
      if (response.status === 401) useAuthStore().clearSession()
      if (!isApiEnvelope(envelope)) throw apiError(envelope, response.status)
      if (response.status >= 400 || envelope.code !== 0) throw apiError(envelope, response.status)
      return envelope.data as T
    } catch (error) {
      if (axios.isAxiosError(error)) {
        const envelope: unknown = error.response?.data
        if (error.response?.status === 401) useAuthStore().clearSession()
        if (envelope !== undefined) throw apiError(envelope, error.response?.status)
        throw { message: error.message, status: error.response?.status } satisfies ApiError
      }
      throw error
    }
  }

  return {
    get: <T>(url: string) => request<T>('get', url),
    post: <T>(url: string, body?: unknown) => request<T>('post', url, body),
    put: <T>(url: string, body?: unknown) => request<T>('put', url, body),
    patch: <T>(url: string, body?: unknown) => request<T>('patch', url, body),
    delete: <T>(url: string) => request<T>('delete', url)
  }
}

export const http = createHttpClient()
