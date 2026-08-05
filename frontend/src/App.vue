<script setup lang="ts">
import { currentElementLocale } from './i18n'
</script>

<template>
  <el-config-provider :locale="currentElementLocale">
    <router-view />
  </el-config-provider>
</template>
