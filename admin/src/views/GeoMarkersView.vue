<template>
  <div class="section">
    <FilterBar>
      <input class="input" v-model="filters.textId" placeholder="Text ID" />
      <input class="input" v-model="filters.category" placeholder="Category" />
      <input class="input" v-model="filters.source" placeholder="Source" />
      <label class="checkbox">
        <input type="checkbox" v-model="filters.missingCoords" /> Missing coords
      </label>
      <label class="checkbox">
        <input type="checkbox" v-model="filters.invalidCoords" /> Invalid coords
      </label>
      <button class="button secondary" type="button" @click="loadMarkers">Search</button>
    </FilterBar>
  </div>

  <div class="section">
    <DataTable>
      <template #head>
        <th>Entity</th>
        <th>Label</th>
        <th>Lat</th>
        <th>Lng</th>
        <th>Source</th>
        <th>Updated</th>
        <th>Actions</th>
      </template>
      <tr v-if="markers.length === 0">
        <td colspan="7">No markers.</td>
      </tr>
      <tr v-for="marker in markers" :key="marker.id">
        <td>{{ marker.entityId || "-" }}</td>
        <td>{{ marker.entityLabel || "-" }}</td>
        <td>
          <input class="input small" v-model.number="editLat[marker.id]" type="number" />
        </td>
        <td>
          <input class="input small" v-model.number="editLng[marker.id]" type="number" />
        </td>
        <td>{{ marker.source || "-" }}</td>
        <td>{{ formatDate(marker.updatedAt) }}</td>
        <td>
          <button class="button secondary" type="button" @click="saveMarker(marker.id)">
            Save
          </button>
          <button class="button ghost" type="button" @click="deleteMarkerItem(marker.id)">
            Delete
          </button>
        </td>
      </tr>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import { notify } from "@/services/toast";
import {
  listGeoMarkers,
  updateGeoMarker,
  deleteGeoMarker,
  type GeoMarker,
} from "@/services/api/geo";

const markers = ref<GeoMarker[]>([]);
const editLat = ref<Record<number, number | null>>({});
const editLng = ref<Record<number, number | null>>({});

const filters = reactive({
  textId: "",
  category: "",
  source: "",
  missingCoords: false,
  invalidCoords: false,
});

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const loadMarkers = async () => {
  try {
    markers.value = await listGeoMarkers({
      textId: filters.textId ? Number(filters.textId) : undefined,
      category: filters.category || undefined,
      source: filters.source || undefined,
      missingCoords: filters.missingCoords || undefined,
      invalidCoords: filters.invalidCoords || undefined,
    });
    const nextLat: Record<number, number | null> = {};
    const nextLng: Record<number, number | null> = {};
    markers.value.forEach((marker) => {
      nextLat[marker.id] = marker.latitude ?? null;
      nextLng[marker.id] = marker.longitude ?? null;
    });
    editLat.value = nextLat;
    editLng.value = nextLng;
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load markers.", "error");
  }
};

const saveMarker = async (id: number) => {
  try {
    await updateGeoMarker(id, { latitude: editLat.value[id], longitude: editLng.value[id] });
    notify("Marker updated.", "success");
    await loadMarkers();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to update marker.", "error");
  }
};

const deleteMarkerItem = async (id: number) => {
  try {
    await deleteGeoMarker(id);
    await loadMarkers();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to delete marker.", "error");
  }
};

onMounted(loadMarkers);
</script>

<style scoped>
.checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
</style>
