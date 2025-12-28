<template>
  <div class="json-viewer">
    <div class="json-toolbar">
      <button class="button secondary" type="button" @click="toggle">
        {{ expanded ? "Collapse" : "Expand" }}
      </button>
      <button class="button ghost" type="button" @click="formatJson">Format</button>
    </div>
    <pre v-if="expanded">{{ pretty }}</pre>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";

const props = defineProps<{ value: unknown }>();

const expanded = ref(true);
const pretty = ref("");

const formatJson = () => {
  try {
    pretty.value = JSON.stringify(props.value, null, 2);
  } catch (error) {
    pretty.value = String(props.value ?? "");
  }
};

const toggle = () => {
  expanded.value = !expanded.value;
};

watch(
  () => props.value,
  () => {
    formatJson();
  },
  { immediate: true }
);
</script>

<style scoped>
.json-viewer pre {
  background: rgba(0, 0, 0, 0.04);
  border-radius: 12px;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
}

.json-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}
</style>
