<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section class="content-view">
    <h1>{{ t('dashboard.title') }}</h1>
    <p>{{ t('dashboard.description') }}</p>
  </section>
</template>
