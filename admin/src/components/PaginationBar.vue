<template>
  <div class="pagination">
    <button class="button secondary" type="button" :disabled="page <= 1" @click="$emit('update:page', page - 1)">
      Prev
    </button>
    <span>Page {{ page }} / {{ totalPages }}</span>
    <button
      class="button secondary"
      type="button"
      :disabled="page >= totalPages"
      @click="$emit('update:page', page + 1)"
    >
      Next
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ page: number; totalPages: number }>();

defineEmits<{ (event: "update:page", value: number): void }>();
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
</style>
