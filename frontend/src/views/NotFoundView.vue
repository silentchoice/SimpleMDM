<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <main class="centered-view"><h1>{{ t('errors.notFoundTitle') }}</h1><p>{{ t('errors.notFoundDescription') }}</p></main>
</template>
