<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../../api/auth'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    userStore.setLogin(data)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect ? redirect : '/')
  } catch (e) {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <div class="login-brand">EP</div>
      <div class="login-title">EP 造数平台</div>
      <div class="login-subtitle">测试数据构造 · 管理端</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large"
        @keyup.enter="handleLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password
            autocomplete="current-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fafafa;
  background-image: radial-gradient(#e4e4e7 1px, transparent 1px);
  background-size: 22px 22px;
}

.login-card {
  width: 380px;
  padding: 20px 16px 4px;
  border: 1px solid #e4e4e7;
  border-radius: 14px;
  box-shadow: 0 8px 30px -12px rgba(24, 24, 27, 0.15) !important;
}

.login-brand {
  width: 44px;
  height: 44px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #18181b;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  text-align: center;
  margin-top: 4px;
  color: #18181b;
}

.login-subtitle {
  text-align: center;
  color: #71717a;
  font-size: 13px;
  margin: 6px 0 22px;
}

.login-btn {
  width: 100%;
}
</style>
