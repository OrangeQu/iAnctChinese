<template>
  <div class="shell">
    <header class="global-bar">
      <div class="title">
        <span>iAnctChinese · 古籍智能标注平台</span>
        <small>项目 | 类型 | 时间 | 范围 | 搜索</small>
      </div>
      <div class="actions">
        <template v-if="authStore.isAuthenticated">
          <el-tooltip content="个人中心" placement="bottom">
            <el-button class="icon-circle" circle @click="goProfile">
              <el-icon><User /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="退出登录" placement="bottom">
            <el-button class="icon-circle" circle @click="handleLogout">
              <el-icon><Right /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
      </div>
    </header>
    <main class="content">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { User, Right } from "@element-plus/icons-vue";
import { useAuthStore } from "@/store/authStore";

const props = defineProps({
  exporting: {
    type: Boolean,
    default: false
  },
  canExport: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(["search", "export"]);
const keywords = ref("");
const router = useRouter();
const authStore = useAuthStore();

const emitSearch = () => {
  emit("search", keywords.value);
};

const goProfile = () => {
  router.push("/profile");
};

const handleLogout = () => {
  authStore.logout();
  router.push("/login");
};
</script>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.global-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 32px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
}

.title {
  display: flex;
  flex-direction: column;
  font-size: 20px;
  font-weight: 600;
}

.title small {
  font-size: 12px;
  color: var(--muted);
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content {
  flex: 1;
  padding: 24px;
}

.icon-circle {
  background: #f3f4f6;
  color: #111827;
  border-color: #e5e7eb;
}

.icon-circle:hover {
  background: #e5e7eb;
  color: #0f172a;
}
</style>
