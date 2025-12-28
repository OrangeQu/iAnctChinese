<template>
  <div class="toast-host">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="toast-card"
      :class="toast.type"
    >
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import type { ToastEventDetail } from "@/services/toast";

interface ToastItem extends ToastEventDetail {
  timeoutId: number;
}

const toasts = ref<ToastItem[]>([]);

const addToast = (event: Event) => {
  const detail = (event as CustomEvent<ToastEventDetail>).detail;
  const timeoutId = window.setTimeout(() => {
    toasts.value = toasts.value.filter((item) => item.id !== detail.id);
  }, 3000);
  toasts.value.push({ ...detail, timeoutId });
};

onMounted(() => {
  window.addEventListener("admin-toast", addToast as EventListener);
});

onUnmounted(() => {
  window.removeEventListener("admin-toast", addToast as EventListener);
  toasts.value.forEach((item) => window.clearTimeout(item.timeoutId));
});
</script>

<style scoped>
.toast-host {
  position: fixed;
  top: 20px;
  right: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 1000;
}

.toast-card {
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.15);
  font-size: 13px;
}

.toast-card.error {
  border-color: rgba(207, 75, 47, 0.4);
  color: #b8331f;
}

.toast-card.success {
  border-color: rgba(44, 110, 102, 0.4);
  color: #2c6e66;
}
</style>
