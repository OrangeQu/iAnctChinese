<template>
  <header class="header panel">
    <div class="title">
      <strong>{{ title }}</strong>
      <span class="subtitle">古籍标注平台 · 后台管理</span>
    </div>
    <div class="actions">
      <el-button text @click="$emit('refresh')">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
      <el-dropdown>
        <span class="user">
          <el-icon><User /></el-icon>
          {{ user?.username || "未登录" }}
          <el-icon class="caret"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$emit('logout')">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { User, Refresh, ArrowDown } from "@element-plus/icons-vue";

defineProps({
  title: {
    type: String,
    default: "总览"
  },
  user: {
    type: Object,
    default: null
  }
});
</script>

<style scoped>
.header {
  height: 64px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.subtitle {
  color: #6b7280;
  font-size: 13px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f3f4f6;
  cursor: pointer;
}

.caret {
  font-size: 12px;
}
</style>
