import { beforeEach, describe, expect, it } from 'vitest'
import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { currentElementLocale, LOCALE_STORAGE_KEY, resolveInitialLocale, setLocale } from './index'

describe('i18n runtime', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('defaults to Simplified Chinese when no locale preference exists', () => {
    expect(resolveInitialLocale()).toBe('zh-CN')
  })

  it('restores a saved English locale preference', () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'en-US')

    expect(resolveInitialLocale()).toBe('en-US')
  })

  it('falls back to Simplified Chinese for an unsupported preference', () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'invalid')

    expect(resolveInitialLocale()).toBe('zh-CN')
  })

  it('persists the selected locale', () => {
    setLocale('en-US')

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en-US')
  })

  it('selects the matching Element Plus locale reactively', () => {
    setLocale('zh-CN')
    expect(currentElementLocale.value).toBe(zhCn)

    setLocale('en-US')
    expect(currentElementLocale.value).toBe(en)
  })
})
