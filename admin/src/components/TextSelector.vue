<template>
  <div>
    <label>{{ label }}</label>
    <select class="select" :value="modelValue ?? ''" @change="onChange">
      <option value="" disabled>Select text</option>
      <option v-for="text in texts" :key="text.id" :value="text.id">
        #{{ text.id }} {{ text.title }}
      </option>
    </select>
  </div>
</template>

<script setup lang="ts">
export interface TextItem {
  id: number;
  title: string;
}

const props = defineProps<{
  label: string;
  texts: TextItem[];
  modelValue: number | null;
}>();

const emit = defineEmits<{ (event: "update:modelValue", value: number | null): void }>();

const onChange = (event: Event) => {
  const target = event.target as HTMLSelectElement;
  const value = target.value ? Number(target.value) : null;
  emit("update:modelValue", Number.isNaN(value as number) ? null : value);
};
</script>
