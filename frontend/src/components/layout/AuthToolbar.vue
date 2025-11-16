<template>
  <div class="auth-toolbar" v-if="authStore.isAuthenticated">
    <div class="left">
      <el-button v-if="showBack" link type="primary" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <slot name="left" />
    </div>
    <div class="right">
      <span class="username">当前用户：{{ authStore.user?.username || "用户" }}</span>
      <el-button type="primary" link @click="handleLogout">退出登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { ArrowLeft } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/store/authStore";

const props = defineProps({
  showBack: {
    type: Boolean,
    default: true
  },
  backTo: {
    type: String,
    default: "/documents"
  }
});

const router = useRouter();
const authStore = useAuthStore();

const goBack = () => {
  router.push(props.backTo);
};

const handleLogout = () => {
  authStore.logout();
  router.push("/login");
};
</script>

<style scoped>
.auth-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.left,
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-weight: 600;
  color: #374151;
}
</style>
