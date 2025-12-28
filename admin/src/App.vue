<template>
  <RouterView v-slot="{ Component, route }">
    <AppShell
      v-if="route.meta.layout !== 'auth'"
      :title="String(route.meta.title || 'Admin')"
      @refresh="triggerRefresh"
    >
      <component :is="Component" />
    </AppShell>
    <component v-else :is="Component" />
  </RouterView>
  <ToastHost />
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import ToastHost from "@/components/ToastHost.vue";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();

const triggerRefresh = () => {
  appStore.triggerRefresh();
};
</script>
