<template>
  <aside class="sidebar panel">
    <div class="logo">iAnct Admin</div>
    <el-menu
      :default-active="active"
      class="menu"
      background-color="transparent"
      @select="onSelect"
    >
      <el-menu-item index="/dashboard">
        <el-icon><DataBoard /></el-icon>
        仪表盘
      </el-menu-item>
      <el-menu-item index="/texts">
        <el-icon><Document /></el-icon>
        文献管理
      </el-menu-item>
      <el-menu-item index="/annotations">
        <el-icon><Connection /></el-icon>
        标注审查
      </el-menu-item>
      <el-menu-item index="/model-jobs">
        <el-icon><Cpu /></el-icon>
        模型任务
      </el-menu-item>
      <el-menu-item index="/users">
        <el-icon><User /></el-icon>
        用户
      </el-menu-item>
      <el-menu-item index="/settings">
        <el-icon><Setting /></el-icon>
        设置
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { DataBoard, Document, Connection, Cpu, User, Setting } from "@element-plus/icons-vue";

const route = useRoute();
const router = useRouter();
const active = computed(() => route.path);

const onSelect = (path) => {
  if (route.path !== path) {
    router.push(path);
  }
};
</script>

<style scoped>
.sidebar {
  padding: 18px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.logo {
  font-weight: 700;
  font-size: 18px;
  color: var(--brand);
  padding: 10px 16px;
}

.menu {
  border: none;
}
</style>
