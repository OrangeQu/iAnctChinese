<template>
  <div class="tabs">
    <button
      v-for="tab in tabs"
      :key="tab"
      class="tab"
      :class="{ active: tab === modelValue }"
      type="button"
      @click="$emit('update:modelValue', tab)"
    >
      {{ tab }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ modelValue: string; tabs: string[] }>();

defineEmits<{ (event: "update:modelValue", value: string): void }>();
</script>

<style scoped>
.tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.tab {
  border: 1px solid var(--stroke);
  background: #fff;
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
}

.tab.active {
  border-color: rgba(207, 75, 47, 0.4);
  color: var(--accent);
}
</style>
