<template>
  <el-container style="height: 100vh">
    <!-- Sidebar -->
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" style="background: #304156; transition: width 0.3s; overflow: hidden;">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; border-bottom: 1px solid rgba(255,255,255,0.1);">
        <el-icon :size="24" color="#409EFF"><DataBoard /></el-icon>
        <span v-show="!appStore.sidebarCollapsed" style="color: #fff; font-size: 16px; font-weight: 600; margin-left: 8px; white-space: nowrap;">SimpleMDM</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
        style="border-right: none;"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/personnel">
          <el-icon><UserFilled /></el-icon>
          <span>人员管理</span>
        </el-menu-item>
        <el-menu-item index="/approvals">
          <el-icon><DocumentChecked /></el-icon>
          <span>审批中心</span>
          <el-badge v-if="pendingCount > 0" :value="pendingCount" style="margin-left: 8px;" />
        </el-menu-item>
        <el-menu-item index="/push-apis">
          <el-icon><Setting /></el-icon>
          <span>推送API管理</span>
        </el-menu-item>
        <el-menu-item index="/push-logs">
          <el-icon><Connection /></el-icon>
          <span>推送日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- Topbar -->
      <el-header style="height: 60px; background: #fff; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 1px 4px rgba(0,0,0,0.08); padding: 0 20px; z-index: 1;">
        <div style="display: flex; align-items: center;">
          <el-icon :size="20" style="cursor: pointer; margin-right: 16px;" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div style="display: flex; align-items: center; gap: 16px;">
          <el-tag :type="roleTagType" size="small">{{ roleLabel }}</el-tag>
          <el-dropdown trigger="click">
            <span style="cursor: pointer; display: flex; align-items: center; gap: 6px;">
              <el-icon :size="18"><UserFilled /></el-icon>
              {{ userStore.user?.real_name || '' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>角色: {{ roleLabel }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main content -->
      <el-main style="background: #f0f2f5; padding: 24px;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useAppStore } from '../stores/app'
import { useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const pendingCount = ref(0)

const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/personnel')) return '/personnel'
  if (p.startsWith('/approvals')) return '/approvals'
  if (p.startsWith('/push-logs')) return '/push-logs'
  return p
})

const roleLabel = computed(() => {
  if (userStore.isAdmin) return '管理员'
  const map = { operator: 'HR操作员', approver: 'HR审批人', viewer: '查看者' }
  return map[userStore.role] || userStore.role || '普通用户'
})

const roleTagType = computed(() => {
  if (userStore.isAdmin) return 'danger'
  const map = { operator: 'warning', approver: 'success', viewer: 'info' }
  return map[userStore.role] || ''
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>
