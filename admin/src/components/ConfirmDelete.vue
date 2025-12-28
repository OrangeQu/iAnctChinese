<template>
  <ModalDialog :open="open" title="Confirm Delete" @close="$emit('close')">
    <p>{{ message }}</p>
    <template #footer>
      <button class="button secondary" type="button" @click="$emit('close')">Cancel</button>
      <button class="button primary" type="button" @click="$emit('confirm')">Delete</button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import ModalDialog from "@/components/ModalDialog.vue";

defineProps<{ open: boolean; message: string }>();

defineEmits<{ (event: "close"): void; (event: "confirm"): void }>();
</script>
