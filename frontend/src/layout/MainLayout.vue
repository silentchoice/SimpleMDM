<template>
  <el-container class="shell">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="brand">SimpleMDM</div>
      <el-menu :default-active="activeMenu" :collapse="appStore.sidebarCollapsed" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff">
        <el-menu-item index="/mdm"><el-icon><Grid /></el-icon><span>Master data</span></el-menu-item>
        <el-menu-item index="/workflow/approvals"><el-icon><DocumentChecked /></el-icon><span>Approvals</span></el-menu-item>
        <el-menu-item index="/integration"><el-icon><Connection /></el-icon><span>Integration</span></el-menu-item>
        <el-menu-item index="/integration/logs"><el-icon><Tickets /></el-icon><span>Delivery logs</span></el-menu-item>
        <el-menu-item index="/mdm-metadata"><el-icon><SetUp /></el-icon><span>Metadata</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <el-icon @click="appStore.toggleSidebar"><Fold v-if="!appStore.sidebarCollapsed" /><Expand v-else /></el-icon>
        <span>{{ route.meta.title || 'SimpleMDM' }}</span>
        <el-button link @click="logout">Logout</el-button>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
<script setup>
import{computed}from'vue';import{useRoute,useRouter}from'vue-router';import{useUserStore}from'../stores/user';import{useAppStore}from'../stores/app';
const route=useRoute(),router=useRouter(),userStore=useUserStore(),appStore=useAppStore();
const activeMenu=computed(()=>route.path.startsWith('/workflow')?'/workflow/approvals':route.path.startsWith('/integration/logs')?'/integration/logs':route.path.startsWith('/integration')?'/integration':route.path.startsWith('/mdm-metadata')?'/mdm-metadata':'/mdm');
function logout(){userStore.logout();router.push('/login')}
</script>
<style scoped>.shell{height:100vh}.sidebar{background:#304156}.brand{height:60px;color:white;display:flex;align-items:center;justify-content:center;font-weight:600}.topbar{display:flex;align-items:center;justify-content:space-between;background:white}</style>
