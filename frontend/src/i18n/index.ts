import { computed } from 'vue'
import { createI18n } from 'vue-i18n'
import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { messages } from './messages'

export type SupportedLocale = 'zh-CN' | 'en-US'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export const LOCALE_STORAGE_KEY = 'mdm.locale'

export function resolveInitialLocale(): SupportedLocale {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  return stored === 'en-US' || stored === 'zh-CN' ? stored : 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages
})

export function setLocale(locale: SupportedLocale): void {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
}

export const currentElementLocale = computed(() => i18n.global.locale.value === 'en-US' ? en : zhCn)
