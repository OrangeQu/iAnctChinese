<template>
  <div class="section">
    <FilterBar>
      <input class="input" v-model="textIdInput" placeholder="Text ID" />
      <button class="button secondary" type="button" @click="loadHides">Search</button>
      <button class="button ghost" type="button" @click="clearAll" :disabled="!textIdInput">
        Clear All
      </button>
    </FilterBar>
  </div>
  <div class="section">
    <DataTable>
      <template #head>
        <th>ID</th>
        <th>Entity</th>
        <th>Label</th>
        <th>Created</th>
        <th>Actions</th>
      </template>
      <tr v-if="hides.length === 0">
        <td colspan="5">No hidden markers.</td>
      </tr>
      <tr v-for="hide in hides" :key="hide.id">
        <td>#{{ hide.id }}</td>
        <td>{{ hide.entityId || "-" }}</td>
        <td>{{ hide.entityLabel || "-" }}</td>
        <td>{{ formatDate(hide.createdAt) }}</td>
        <td>
          <button class="button ghost" type="button" @click="restoreHide(hide.id)">
            Restore
          </button>
        </td>
      </tr>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import { notify } from "@/services/toast";
import {
  listGeoHides,
  clearGeoHides,
  restoreGeoHide,
  type HiddenGeoMarker,
} from "@/services/api/geo";

const textIdInput = ref("");
const hides = ref<HiddenGeoMarker[]>([]);

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const loadHides = async () => {
  if (!textIdInput.value.trim()) {
    notify("Please input textId.", "error");
    return;
  }
  try {
    hides.value = await listGeoHides(Number(textIdInput.value));
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load hides.", "error");
  }
};

const clearAll = async () => {
  if (!textIdInput.value.trim()) return;
  try {
    await clearGeoHides(Number(textIdInput.value));
    hides.value = [];
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to clear hides.", "error");
  }
};

const restoreHide = async (id: number) => {
  try {
    await restoreGeoHide(id);
    hides.value = hides.value.filter((item) => item.id !== id);
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to restore hide.", "error");
  }
};

onMounted(() => {
  if (textIdInput.value) {
    loadHides();
  }
});
</script>
