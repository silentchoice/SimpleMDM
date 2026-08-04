<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { menuForRoles } from '../router/menu'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const menu = computed(() => menuForRoles(auth.session?.roles ?? []))
const pageTitle = computed(() => route.meta.title ?? 'SimpleMDM')

async function signOut(): Promise<void> {
  try {
    await logout()
  } finally {
    auth.clearSession()
    await router.push('/login')
  }
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="240px" class="app-sidebar">
      <div class="brand">SimpleMDM</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-for="item in menu" :key="item.to" :index="item.to">{{ item.label }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <el-breadcrumb separator="/"><el-breadcrumb-item>SimpleMDM</el-breadcrumb-item><el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item></el-breadcrumb>
        <div class="account-summary">
          <span>{{ auth.session?.department?.name ?? 'Global' }}</span>
          <span>{{ auth.session?.user.displayName }}</span>
          <el-button text type="primary" @click="signOut">Sign out</el-button>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
