import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'
import { i18n, setLocale } from '../i18n'

const { push, login } = vi.hoisted(() => ({ push: vi.fn(), login: vi.fn() }))

vi.mock('../api/auth', () => ({ login }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { redirect: '/metadata/active' } }), useRouter: () => ({ push }) }))

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockReset()
    login.mockReset()
    localStorage.clear()
    setLocale('zh-CN')
  })

  const mountLogin = () => mount(LoginView, { global: { plugins: [ElementPlus, i18n] } })

  it('validates required credentials before submitting', async () => {
    const wrapper = mountLogin()

    await wrapper.get('form').trigger('submit')

    expect(login).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入用户名和密码')
  })

  it('shows request-id errors returned by login', async () => {
    login.mockRejectedValue({ message: 'Invalid credentials', requestId: 'req-login-9' })
    const wrapper = mountLogin()
    await wrapper.get('[name="username"]').setValue('editor')
    await wrapper.get('[name="password"]').setValue('bad')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Invalid credentials (请求 ID：req-login-9)')
  })

  it('shows loading state and redirects after login', async () => {
    let resolveLogin: (value: unknown) => void = () => undefined
    login.mockImplementation(() => new Promise((resolve) => { resolveLogin = resolve }))
    const wrapper = mountLogin()
    await wrapper.get('[name="username"]').setValue('editor')
    await wrapper.get('[name="password"]').setValue('secret')

    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('button[type="submit"]').text()).toContain('登录中')
    resolveLogin({ accessToken: 'token', user: { id: 2, username: 'editor', displayName: 'Editor' }, roles: ['DEPT_EDITOR'], department: null })
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/metadata/active')
  })

  it('renders the language switcher and updates login text without navigation', async () => {
    const wrapper = mountLogin()

    expect(wrapper.get('[data-testid="language-switcher"]').text()).toBe('English')
    expect(wrapper.text()).toContain('管理控制台')

    await wrapper.get('[data-testid="language-switcher"]').trigger('click')

    expect(wrapper.text()).toContain('Management Console')
    expect(push).not.toHaveBeenCalled()
  })
})
