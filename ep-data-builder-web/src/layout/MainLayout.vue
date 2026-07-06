<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/factories')) return '/factories'
  if (route.path.startsWith('/builds')) return '/builds'
  return route.path
})

const shownName = computed(() => userStore.displayName || userStore.username || '未登录')

const roleLabel = computed(() => {
  const map = { ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读' }
  return map[userStore.role] || userStore.role
})

function handleCommand(command) {
  if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
      .then(() => {
        userStore.logout()
        router.push('/login')
      })
      .catch(() => {})
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-mark">EP</span>
        <span class="logo-text">造数平台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/envs">环境管理</el-menu-item>
        <el-menu-item index="/factories">工厂管理</el-menu-item>
        <el-menu-item index="/builds">造数记录</el-menu-item>
        <el-sub-menu v-if="userStore.isAdmin" index="/system">
          <template #title>系统管理</template>
          <el-menu-item index="/system/tokens">开放令牌</el-menu-item>
          <el-menu-item index="/system/users">用户管理</el-menu-item>
          <el-menu-item index="/system/logs">操作日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta.title || '' }}</div>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            {{ shownName }}
            <el-tag size="small" class="role-tag">{{ roleLabel }}</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background-color: #ffffff;
  border-right: 1px solid #e4e4e7;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid #e4e4e7;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #18181b;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.logo-text {
  color: #18181b;
  font-size: 15px;
  font-weight: 600;
}

.menu {
  border-right: none;
  flex: 1;
  padding: 8px;
  --el-menu-text-color: #52525b;
  --el-menu-active-color: #18181b;
  --el-menu-bg-color: #ffffff;
}

/* 菜单项：圆角块 + 柔和 hover/active，取代默认整条高亮 */
.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  border-radius: 8px;
  margin: 2px 0;
  font-weight: 500;
}
.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background-color: #f4f4f5;
}
.menu :deep(.el-menu-item.is-active) {
  background-color: #f4f4f5;
  color: #18181b;
  font-weight: 600;
}
.menu :deep(.el-sub-menu .el-menu-item) {
  padding-left: 40px !important;
}

.header {
  background: #ffffff;
  border-bottom: 1px solid #e4e4e7;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #18181b;
}

.user-info {
  cursor: pointer;
  color: #3f3f46;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  transition: background-color 0.15s;
  outline: none;
}
.user-info:hover {
  background-color: #f4f4f5;
}

.role-tag {
  margin-left: 2px;
}

.main {
  padding: 20px;
  overflow: auto;
  background-color: #fafafa;
}
</style>
