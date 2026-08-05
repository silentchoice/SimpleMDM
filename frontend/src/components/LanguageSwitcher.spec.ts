import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it } from 'vitest'
import { i18n, setLocale } from '../i18n'
import LanguageSwitcher from './LanguageSwitcher.vue'

describe('LanguageSwitcher', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('zh-CN')
  })

  it('switches from Chinese to English and persists the preference', async () => {
    const wrapper = mount(LanguageSwitcher, { global: { plugins: [ElementPlus, i18n] } })

    expect(wrapper.get('[data-testid="language-switcher"]').text()).toBe('English')

    await wrapper.get('[data-testid="language-switcher"]').trigger('click')

    expect(i18n.global.locale.value).toBe('en-US')
    expect(localStorage.getItem('mdm.locale')).toBe('en-US')
    expect(wrapper.get('[data-testid="language-switcher"]').text()).toBe('中文')
  })
})
