<template>
  <div style="display: flex; justify-content: center; align-items: center; height: 100vh; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);">
    <el-card style="width: 440px; border-radius: 8px;">
      <template #header>
        <div style="text-align: center; padding: 12px 0;">
          <el-icon :size="40" color="#409EFF"><DataBoard /></el-icon>
          <h2 style="margin: 12px 0 4px; font-size: 24px; color: #303133;">SimpleMDM</h2>
          <p style="color: #909399; font-size: 14px; margin: 0;">主数据管理平台</p>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="系统编码" prop="systemCode">
          <el-input v-model="form.systemCode" placeholder="请输入系统编码" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%; margin-top: 8px;" @click="handleLogin">
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form>

      <el-divider />

      <div style="font-size: 12px; color: #909399;">
        <p style="margin: 4px 0; font-weight: 600;">演示账号：</p>
        <el-row :gutter="8">
          <el-col :span="24" v-for="acc in demoAccounts" :key="acc.user">
            <el-button size="small" text style="width: 100%;" @click="fillAccount(acc)">
              <div style="text-align: left; line-height: 1.4;">
                <div style="font-weight: 600;">{{ acc.label }}</div>
                <div style="font-size: 11px; color: #c0c4cc;">{{ acc.user }} / {{ acc.pwd }}</div>
              </div>
            </el-button>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({ systemCode: 'DEFAULT', username: '', password: '' })
const rules = {
  systemCode: [{ required: true, message: '请输入系统编码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const demoAccounts = [
  { label: '系统管理员', user: 'admin', pwd: '123456' },
]

function fillAccount(acc) {
  form.username = acc.user
  form.password = acc.pwd
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form.systemCode, form.username, form.password)
    router.push('/mdm')
  } catch {
    // Error already handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>
