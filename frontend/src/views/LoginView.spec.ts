import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'

const { push, login } = vi.hoisted(() => ({ push: vi.fn(), login: vi.fn() }))

vi.mock('../api/auth', () => ({ login }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { redirect: '/metadata/active' } }), useRouter: () => ({ push }) }))

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockReset()
    login.mockReset()
  })

  const mountLogin = () => mount(LoginView, { global: { plugins: [ElementPlus] } })

  it('validates required credentials before submitting', async () => {
    const wrapper = mountLogin()

    await wrapper.get('form').trigger('submit')

    expect(login).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Username and password are required')
  })

  it('shows request-id errors returned by login', async () => {
    login.mockRejectedValue({ message: 'Invalid credentials', requestId: 'req-login-9' })
    const wrapper = mountLogin()
    await wrapper.get('[name="username"]').setValue('editor')
    await wrapper.get('[name="password"]').setValue('bad')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Invalid credentials (Request ID: req-login-9)')
  })

  it('shows loading state and redirects after login', async () => {
    let resolveLogin: (value: unknown) => void = () => undefined
    login.mockImplementation(() => new Promise((resolve) => { resolveLogin = resolve }))
    const wrapper = mountLogin()
    await wrapper.get('[name="username"]').setValue('editor')
    await wrapper.get('[name="password"]').setValue('secret')

    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('button[type="submit"]').text()).toContain('Signing in')
    resolveLogin({ accessToken: 'token', user: { id: 2, username: 'editor', displayName: 'Editor' }, roles: ['DEPT_EDITOR'], department: null })
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/metadata/active')
  })
})
