import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppLayout from './AppLayout.vue'
import { useAuthStore } from '../stores/auth'

const { logout, push } = vi.hoisted(() => ({ logout: vi.fn(), push: vi.fn() }))

vi.mock('../api/auth', () => ({ logout }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', meta: { title: 'Dashboard' } }),
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
  })

  const mountLayout = () => mount(AppLayout, { global: { plugins: [ElementPlus], stubs: { RouterView: true } } })

  it('opens a mobile navigation panel with read-only menu entries', async () => {
    const wrapper = mountLayout()

    await wrapper.get('[data-testid="mobile-nav-toggle"]').trigger('click')

    expect(wrapper.get('[data-testid="mobile-navigation"]').text()).toContain('Active Metadata')
    expect(wrapper.text()).not.toContain('Submit Change')
  })

  it('clears local access and reports a logout service failure', async () => {
    logout.mockRejectedValue(new Error('offline'))
    const wrapper = mountLayout()

    await wrapper.get('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(useAuthStore().session).toBeNull()
    expect(push).toHaveBeenCalledWith({ path: '/login', query: { logout: 'local' } })
  })
})
