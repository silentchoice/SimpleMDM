<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import type { ApiError } from '../types'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const logoutNotice = route.query.logout === 'local'

async function submit(): Promise<void> {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = t('auth.required')
    return
  }
  loading.value = true
  try {
    auth.setSession(await login(username.value.trim(), password.value))
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (reason) {
    const apiError = reason as ApiError
    error.value = apiError.requestId ? `${apiError.message} (${t('common.requestId', { id: apiError.requestId })})` : apiError.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card" shadow="always">
      <h1>SimpleMDM</h1>
      <LanguageSwitcher />
      <p class="login-subtitle">{{ t('auth.console') }}</p>
      <p v-if="logoutNotice" class="logout-notice" role="status">{{ t('auth.localLogout') }}</p>
      <form @submit.prevent="submit">
        <label for="username">{{ t('auth.username') }}</label>
        <input id="username" v-model="username" name="username" autocomplete="username" />
        <label for="password">{{ t('auth.password') }}</label>
        <input id="password" v-model="password" name="password" type="password" autocomplete="current-password" />
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <el-button native-type="submit" type="primary" :loading="loading" class="login-submit">{{ loading ? t('auth.signingIn') : t('auth.signIn') }}</el-button>
      </form>
    </el-card>
  </main>
</template>
