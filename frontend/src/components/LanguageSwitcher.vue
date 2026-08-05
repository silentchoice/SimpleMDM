<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale } from '../i18n'

const { locale, t } = useI18n()

function switchLanguage(): void {
  setLocale(locale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
}
</script>

<template>
  <button
    data-testid="language-switcher"
    type="button"
    :aria-label="t('common.switchLanguage')"
    @click="switchLanguage"
  >
    {{ locale === 'zh-CN' ? 'English' : '中文' }}
  </button>
</template>
