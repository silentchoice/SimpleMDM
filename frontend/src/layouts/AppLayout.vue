<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { menuForRoles } from '../router/menu'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'

const auth = useAuthStore()
const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const menu = computed(() => menuForRoles(auth.session?.roles ?? []))
const pageTitle = computed(() => route.meta.titleKey ? t(route.meta.titleKey) : 'SimpleMDM')
const mobileMenuOpen = ref(false)

async function signOut(): Promise<void> {
  let logoutFailed = false
  try {
    await logout()
  } catch {
    logoutFailed = true
  } finally {
    auth.clearSession()
  }
  await router.push(logoutFailed ? { path: '/login', query: { logout: 'local' } } : '/login')
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="240px" class="app-sidebar">
      <div class="brand">SimpleMDM</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-for="item in menu" :key="item.to" :index="item.to">{{ t(item.labelKey) }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <el-breadcrumb separator="/"><el-breadcrumb-item>SimpleMDM</el-breadcrumb-item><el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item></el-breadcrumb>
        <button class="mobile-nav-toggle" data-testid="mobile-nav-toggle" type="button" @click="mobileMenuOpen = !mobileMenuOpen">{{ t('layout.menu') }}</button>
        <div class="account-summary">
          <span>{{ auth.session?.department?.name ?? t('layout.global') }}</span>
          <span>{{ auth.session?.user.displayName }}</span>
          <LanguageSwitcher />
          <el-button data-testid="logout-button" text type="primary" @click="signOut">{{ t('layout.signOut') }}</el-button>
        </div>
      </el-header>
      <nav v-if="mobileMenuOpen" data-testid="mobile-navigation" class="mobile-navigation" :aria-label="t('layout.mainNavigation')">
        <el-menu :default-active="route.path" router @select="mobileMenuOpen = false">
          <el-menu-item v-for="item in menu" :key="item.to" :index="item.to">{{ t(item.labelKey) }}</el-menu-item>
        </el-menu>
      </nav>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
