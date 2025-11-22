<template>
  <div class="app-container" :class="{ workbench: isWorkbench }">
    <!-- Workbench Layout (Sidebar + Header) -->
    <template v-if="isWorkbench">
      <aside class="sidebar">
        <div class="logo-area">
          <div class="logo-box">
            <span>古</span>
          </div>
          <span class="app-name">古籍视界</span>
        </div>

        <nav class="nav-menu">
          <router-link to="/dashboard" class="nav-item" active-class="active">
            <el-icon><Odometer /></el-icon>
            <span>工作台</span>
          </router-link>
          <router-link to="/documents" class="nav-item" active-class="active">
            <el-icon><Document /></el-icon>
            <span>文档管理</span>
          </router-link>
          <router-link to="/graph" class="nav-item" active-class="active">
            <el-icon><Share /></el-icon>
            <span>知识图谱</span>
          </router-link>
        </nav>

        <div class="user-profile">
           <el-popover trigger="click" placement="top" :width="200">
             <template #reference>
               <div class="user-card">
                 <div class="avatar-circle">
                   {{ userInitial }}
                 </div>
                 <div class="user-info">
                   <span class="username">{{ userName }}</span>
                   <span class="role">研究员</span>
                 </div>
                 <el-icon class="more-icon"><More /></el-icon>
               </div>
             </template>
             <div class="user-menu-content">
               <el-button type="danger" text style="width: 100%; justify-content: flex-start;" @click="handleLogout">
                 <el-icon class="mr-2"><SwitchButton /></el-icon> 退出登录
               </el-button>
             </div>
           </el-popover>
        </div>
      </aside>

      <div class="main-area">
        <header class="top-bar">
          <div class="breadcrumbs">
            <span>{{ currentPageName }}</span>
      </div>
      <div class="actions">
             <div class="search-box">
                <el-icon class="search-icon"><Search /></el-icon>
                <input 
                  type="text" 
          v-model="keywords"
                  placeholder="搜索..." 
                  @keyup.enter="emitSearch"
                />
             </div>
             <el-button 
               v-if="canExport" 
               circle 
               class="action-btn"
               :loading="exporting"
               @click="$emit('export')"
             >
               <el-icon><Download /></el-icon>
             </el-button>
             <el-button circle class="action-btn">
                <el-icon><Bell /></el-icon>
        </el-button>
      </div>
    </header>

        <main class="content-wrapper">
      <slot />
    </main>
      </div>
    </template>

    <!-- Full Screen Layout (Home, Login, Register) -->
    <template v-else>
      <slot />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/store/authStore";
import { Odometer, Document, Share, Search, Download, Bell, More, SwitchButton } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";

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
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const keywords = ref("");

const isWorkbench = computed(() => {
  const excludedRoutes = ["home", "login", "register"];
  return !excludedRoutes.includes(route.name);
});

const currentPageName = computed(() => {
  switch (route.name) {
    case "dashboard": return "工作台";
    case "documents": return "文档管理";
    case "knowledge-graph": return "知识图谱";
    case "text-workspace": return "文本标注";
    default: return "Dashboard";
  }
});

const userName = computed(() => authStore.user?.username || "User");
const userInitial = computed(() => userName.value.charAt(0).toUpperCase());

const emitSearch = () => {
  emit("search", keywords.value);
};

const handleLogout = () => {
  authStore.logout();
  ElMessage.success("已退出登录");
  router.push("/login");
};

const updateBodyClass = () => {
  if (typeof document === "undefined") return;
  if (isWorkbench.value) {
    document.body.classList.add("workbench-mode");
  } else {
    document.body.classList.remove("workbench-mode");
  }
};

onMounted(() => {
  updateBodyClass();
});

watch(
  isWorkbench,
  () => {
    updateBodyClass();
  }
);

onBeforeUnmount(() => {
  if (typeof document === "undefined") return;
  document.body.classList.remove("workbench-mode");
});
</script>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-container.workbench {
  display: flex;
  background-color: var(--bg-main);
}

/* Sidebar Styles */
.sidebar {
  width: 260px;
  background: var(--bg-card);
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  border-right: none; /* Removed border for cleaner look */
  position: fixed;
  height: 100vh;
  left: 0;
  top: 0;
  z-index: 100;
  box-shadow: 4px 0 24px rgba(0,0,0,0.02);
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
  padding: 0 12px;
}

.logo-box {
  width: 40px;
  height: 40px;
  background: var(--primary-color);
  color: white;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 20px;
}

.app-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.nav-menu {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-radius: 16px;
  color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.2s ease;
  font-weight: 600;
}

.nav-item:hover {
  background: rgba(0,0,0,0.03);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--primary-color);
  color: white;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.user-profile {
  margin-top: auto;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #F8F8F8;
  border-radius: 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-card:hover {
  background: #F0F0F0;
}

.avatar-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #E0E0E0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #555;
}

.user-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.username {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.role {
  font-size: 12px;
  color: var(--text-tertiary);
}

.more-icon {
  color: var(--text-tertiary);
}

/* Main Area Styles */
.main-area {
  flex: 1;
  margin-left: 260px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.top-bar {
  height: 90px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  background: transparent; /* Removed blur background for cleaner look */
  z-index: 90;
}

.breadcrumbs {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  position: relative;
  width: 320px;
}

.search-box input {
  width: 100%;
  padding: 12px 20px 12px 44px;
  border-radius: 50px;
  border: 1px solid transparent;
  background: #FFFFFF;
  font-size: 14px;
  transition: all 0.2s;
  outline: none;
  box-shadow: var(--shadow-sm);
}

.search-box input:focus {
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.05);
}

.search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-tertiary);
}

.action-btn {
  background: #FFFFFF;
  border: none;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  box-shadow: var(--shadow-sm);
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.content-wrapper {
  padding: 0 40px 40px 40px;
  flex: 1;
}
</style>
