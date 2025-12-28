<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        iAnctChinese
        <span>Admin Deck</span>
      </div>
      <nav class="nav">
        <RouterLink to="/dashboard">Overview</RouterLink>
        <RouterLink to="/texts">Texts</RouterLink>
        <RouterLink to="/projects">Projects</RouterLink>
        <RouterLink to="/annotations">Annotations</RouterLink>
        <RouterLink to="/quality/entities">Quality - Entities</RouterLink>
        <RouterLink to="/quality/relations">Quality - Relations</RouterLink>
        <RouterLink to="/model-jobs">Model Jobs</RouterLink>
        <RouterLink v-if="canSeeUsers" to="/users">Users</RouterLink>
        <RouterLink to="/me">Profile</RouterLink>
      </nav>
    </aside>
    <main class="main">
      <div class="topbar">
        <h1>{{ title }}</h1>
        <div class="actions">
          <input class="input small" v-model="apiBase" />
          <button class="button secondary" type="button" @click="saveApiBase">Save API</button>
          <button class="button ghost" type="button" @click="$emit('refresh')">Refresh</button>
          <button class="button primary" type="button" @click="logout">Logout</button>
        </div>
      </div>
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watchEffect } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { useAppStore } from "@/stores/app";

defineProps<{ title: string }>();

defineEmits<{ (event: "refresh"): void }>();

const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();
const canSeeUsers = computed(() => {
  const role = authStore.user?.role;
  return !role || role === "ADMIN";
});

const apiBase = ref(appStore.apiBase);

watchEffect(() => {
  apiBase.value = appStore.apiBase;
});

const logout = () => {
  authStore.logout();
  router.replace("/login");
};

const saveApiBase = () => {
  appStore.setApiBase(apiBase.value.trim());
};
</script>
