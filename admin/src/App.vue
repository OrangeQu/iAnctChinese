<template>
  <router-view v-slot="{ Component }">
    <div v-if="!isAuthed && $route.name !== 'login'" class="full-center">
      <el-result icon="warning" title="未登录" sub-title="请先登录后台">
        <template #extra>
          <el-button type="primary" @click="goLogin">去登录</el-button>
        </template>
      </el-result>
    </div>
    <div v-else-if="$route.name === 'login'">
      <component :is="Component" />
    </div>
    <div v-else class="layout">
      <AdminSidebar />
      <main class="main">
        <AdminHeader :title="pageTitle" :user="auth.user" @logout="logout" @refresh="refresh" />
        <section class="content">
          <component :is="Component" @refresh="refresh" />
        </section>
      </main>
    </div>
  </router-view>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import AdminSidebar from "@/components/AdminSidebar.vue";
import AdminHeader from "@/components/AdminHeader.vue";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

const isAuthed = computed(() => auth.isAuthenticated);
const pageTitle = computed(() => {
  const mapping = {
    dashboard: "仪表盘",
    texts: "文献管理",
    annotations: "标注审查",
    "model-jobs": "模型任务",
    users: "用户",
    settings: "设置"
  };
  return mapping[route.name] || "后台";
});

const goLogin = () => router.push({ name: "login" });
const logout = () => {
  auth.logout();
  router.push({ name: "login" });
};
const refresh = () => {
  router.replace({ path: route.fullPath });
};
</script>

<style scoped>
.main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
}

.content {
  overflow: auto;
}

.full-center {
  height: 100vh;
  display: grid;
  place-items: center;
}
</style>
