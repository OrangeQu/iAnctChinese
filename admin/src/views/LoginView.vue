<template>
  <div class="login">
    <div class="card panel">
      <h2>后台登录</h2>
      <p class="desc">使用已有账号登录后台管理系统</p>
      <el-form :model="form" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" :loading="auth.loading" block @click="onSubmit">登录</el-button>
      </el-form>
      <el-alert
        v-if="error"
        type="error"
        :closable="false"
        :title="error"
        show-icon
        style="margin-top: 12px;"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "@/stores/authStore";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const form = ref({ username: "admin", password: "admin" });
const error = ref("");

const onSubmit = async () => {
  error.value = "";
  try {
    await auth.login(form.value);
    const redirect = route.query.redirect || "/dashboard";
    router.push(redirect);
  } catch (err) {
    error.value = err.response?.data?.message || "登录失败";
  }
};
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: radial-gradient(circle at 20% 20%, rgba(94, 141, 255, 0.15), transparent 25%),
    radial-gradient(circle at 80% 0%, rgba(31, 51, 73, 0.2), transparent 30%),
    var(--bg);
}

.card {
  width: 360px;
  padding: 30px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.desc {
  margin: 0;
  color: #6b7280;
}
</style>
