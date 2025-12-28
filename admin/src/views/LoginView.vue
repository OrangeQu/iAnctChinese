<template>
  <div class="login">
    <h1>iAnctChinese Admin</h1>
    <p>Sign in to manage texts, projects, annotations, and model jobs.</p>
    <div class="banner">Default API base: <strong>{{ apiBase }}</strong></div>
    <form class="stagger" style="margin-top: 20px" @submit.prevent="onSubmit">
      <div class="row">
        <label>Username</label>
        <input class="input" v-model="form.username" placeholder="root" required />
      </div>
      <div class="row">
        <label>Password</label>
        <input class="input" v-model="form.password" type="password" required />
      </div>
      <div class="row">
        <label>API Base</label>
        <input class="input" v-model="apiBase" />
      </div>
      <button class="button primary" type="submit">Login</button>
      <div class="toast" v-if="error">{{ error }}</div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { useAppStore } from "@/stores/app";

const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();

const form = reactive({
  username: "",
  password: "",
});

const apiBase = ref(appStore.apiBase);
const error = ref("");

const onSubmit = async () => {
  error.value = "";
  try {
    appStore.setApiBase(apiBase.value.trim());
    await authStore.login(form.username.trim(), form.password.trim());
    router.replace("/dashboard");
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Login failed.";
  }
};
</script>
