import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppLayout from './AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { createHttpClient } from '../api/http'
import type { AxiosAdapter } from 'axios'
import { i18n, setLocale } from '../i18n'

const { logout, push } = vi.hoisted(() => ({ logout: vi.fn(), push: vi.fn() }))

vi.mock('../api/auth', () => ({ logout }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', meta: { titleKey: 'routes.dashboard' } }),
  useRouter: () => ({ push })
}))

describe('AppLayout', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setSession({
      accessToken: 'viewer-token',
      user: { id: 4, username: 'viewer', displayName: 'Viewer' },
      roles: ['DEPT_VIEWER'],
      department: { id: 3, code: 'OPS', name: 'Operations' }
    })
    logout.mockReset()
    push.mockReset()
    localStorage.clear()
    setLocale('zh-CN')
  })

  const mountLayout = () => mount(AppLayout, { global: { plugins: [ElementPlus, i18n], stubs: { RouterView: true } } })

  it('opens a mobile navigation panel with read-only menu entries', async () => {
    const wrapper = mountLayout()

    await wrapper.get('[data-testid="mobile-nav-toggle"]').trigger('click')

    expect(wrapper.get('[data-testid="mobile-navigation"]').text()).toContain('当前元数据')
    expect(wrapper.text()).not.toContain('Submit Change')
  })

  it('renders the language switcher and updates header text without navigation', async () => {
    const wrapper = mountLayout()

    expect(wrapper.get('[data-testid="language-switcher"]').text()).toBe('English')
    expect(wrapper.get('[data-testid="mobile-nav-toggle"]').text()).toBe('菜单')

    await wrapper.get('[data-testid="language-switcher"]').trigger('click')

    expect(wrapper.get('[data-testid="mobile-nav-toggle"]').text()).toBe('Menu')
    expect(push).not.toHaveBeenCalled()
  })

  it('sends the bearer token and clears local access after a successful logout attempt', async () => {
    let authorization: unknown
    let tokenDuringRequest: string | undefined
    const adapter: AxiosAdapter = async (config) => {
      authorization = config.headers?.Authorization
      tokenDuringRequest = useAuthStore().session?.accessToken
      return { data: { code: 0, message: 'OK', data: null, requestId: 'logout-success' }, status: 200, statusText: 'OK', headers: {}, config }
    }
    logout.mockImplementation(() => createHttpClient({ adapter }).post('/auth/logout'))
    const wrapper = mountLayout()

    await wrapper.get('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(useAuthStore().session).toBeNull()
    expect(authorization).toBe('Bearer viewer-token')
    expect(tokenDuringRequest).toBe('viewer-token')
    expect(push).toHaveBeenCalledWith('/login')
  })

  it('sends the bearer token and clears local access after a failed logout attempt', async () => {
    let authorization: unknown
    let tokenDuringRequest: string | undefined
    const adapter: AxiosAdapter = async (config) => {
      authorization = config.headers?.Authorization
      tokenDuringRequest = useAuthStore().session?.accessToken
      return { data: { code: 500, message: 'Offline', data: null, requestId: 'logout-failure' }, status: 500, statusText: 'Service Unavailable', headers: {}, config }
    }
    logout.mockImplementation(() => createHttpClient({ adapter }).post('/auth/logout'))
    const wrapper = mountLayout()

    await wrapper.get('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(useAuthStore().session).toBeNull()
    expect(authorization).toBe('Bearer viewer-token')
    expect(tokenDuringRequest).toBe('viewer-token')
    expect(push).toHaveBeenCalledWith({ path: '/login', query: { logout: 'local' } })
  })
})
