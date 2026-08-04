import type { AxiosAdapter } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createHttpClient } from './http'
import { useAuthStore } from '../stores/auth'
import { createPinia, setActivePinia } from 'pinia'
import type { Session } from '../types'

const loggedInSession: Session = {
  accessToken: 'access-token',
  user: { id: 1, username: 'admin', displayName: 'Administrator' },
  roles: ['SUPER_ADMIN'],
  department: null
}

function adapter(response: unknown, status = 200): AxiosAdapter {
  return async (config) => ({ data: response, status, statusText: 'OK', headers: {}, config })
}

describe('http client', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setSession(loggedInSession)
  })

  it('adds Bearer and a request id, then unwraps a successful envelope', async () => {
    const requestId = vi.fn(() => 'req-42')
    const client = createHttpClient({
      adapter: async (config) => {
        expect(config.headers?.Authorization).toBe('Bearer access-token')
        expect(config.headers?.['X-Request-Id']).toBe('req-42')
        return { data: { code: 0, message: 'OK', data: { id: 4 }, requestId: 'req-42' }, status: 200, statusText: 'OK', headers: {}, config }
      },
      requestId
    })

    await expect(client.get<{ id: number }>('/records')).resolves.toEqual({ id: 4 })
  })

  it('retains the response request id in API errors', async () => {
    const client = createHttpClient({ adapter: adapter({ code: 4403, message: 'Denied', data: null, requestId: 'server-77' }) })

    await expect(client.get('/records')).rejects.toMatchObject({ message: 'Denied', requestId: 'server-77', code: 4403 })
  })

  it('clears the session after a 401 response', async () => {
    const client = createHttpClient({ adapter: adapter({ code: 401, message: 'Expired', data: null, requestId: 'server-401' }, 401) })

    await expect(client.get('/records')).rejects.toMatchObject({ requestId: 'server-401' })
    expect(useAuthStore().session).toBeNull()
  })
})
