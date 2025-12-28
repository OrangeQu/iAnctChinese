<template>
  <div class="section">
    <div class="grid-two">
      <div class="form-card">
        <h2>{{ editingId ? "Edit Map" : "Create Map" }}</h2>
        <div class="row">
          <label>ID</label>
          <input class="input" v-model.number="form.id" type="number" :disabled="editingId !== null" />
        </div>
        <div class="row">
          <label>Dynasty</label>
          <input class="input" v-model="form.dynasty" />
        </div>
        <div class="row">
          <label>Filename</label>
          <input class="input" v-model="form.filename" />
        </div>
        <div class="row">
          <label>Start Year</label>
          <input class="input" v-model.number="form.startYear" type="number" />
        </div>
        <div class="row">
          <label>End Year</label>
          <input class="input" v-model.number="form.endYear" type="number" />
        </div>
        <div class="row">
          <label>Width</label>
          <input class="input" v-model.number="form.width" type="number" />
        </div>
        <div class="row">
          <label>Height</label>
          <input class="input" v-model.number="form.height" type="number" />
        </div>
        <div style="display: flex; gap: 10px;">
          <button class="button primary" type="button" @click="saveMap">
            {{ editingId ? "Update" : "Create" }}
          </button>
          <button v-if="editingId" class="button ghost" type="button" @click="resetForm">
            Cancel
          </button>
        </div>
      </div>
      <div class="form-card">
        <h2>Preview</h2>
        <div class="banner" v-if="!isPreviewReady">Provide image filename or URL to preview.</div>
        <img v-else class="map-preview" :src="form.filename" alt="map preview" />
      </div>
    </div>
  </div>

  <div class="section">
    <h2>Maps</h2>
    <table class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Dynasty</th>
          <th>Filename</th>
          <th>Years</th>
          <th>Size</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="maps.length === 0">
          <td colspan="6">No maps.</td>
        </tr>
        <tr v-for="map in maps" :key="map.id">
          <td>#{{ map.id }}</td>
          <td>{{ map.dynasty || "-" }}</td>
          <td>
            <div>{{ map.filename || "-" }}</div>
            <img v-if="isImagePath(map.filename)" class="thumb" :src="map.filename" alt="preview" />
          </td>
          <td>{{ map.startYear || "-" }} - {{ map.endYear || "-" }}</td>
          <td>{{ map.width || "-" }} x {{ map.height || "-" }}</td>
          <td>
            <button class="button secondary" type="button" @click="editMap(map)">Edit</button>
            <button class="button primary" type="button" @click="deleteMapItem(map.id)">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { notify } from "@/services/toast";
import { createMap, deleteMap, listMaps, updateMap, type MapInfo } from "@/services/api/geo";

const maps = ref<MapInfo[]>([]);
const editingId = ref<number | null>(null);

const form = reactive<MapInfo>({
  id: 0,
  dynasty: "",
  filename: "",
  startYear: undefined,
  endYear: undefined,
  width: undefined,
  height: undefined,
});

const isImagePath = (value?: string) => {
  if (!value) return false;
  return /(\.png|\.jpg|\.jpeg|\.gif|\.webp)$/i.test(value) || value.startsWith("/") || value.startsWith("http");
};

const isPreviewReady = computed(() => isImagePath(form.filename));

const resetForm = () => {
  editingId.value = null;
  form.id = 0;
  form.dynasty = "";
  form.filename = "";
  form.startYear = undefined;
  form.endYear = undefined;
  form.width = undefined;
  form.height = undefined;
};

const loadMaps = async () => {
  maps.value = await listMaps();
};

const editMap = (map: MapInfo) => {
  editingId.value = map.id;
  form.id = map.id;
  form.dynasty = map.dynasty || "";
  form.filename = map.filename || "";
  form.startYear = map.startYear;
  form.endYear = map.endYear;
  form.width = map.width;
  form.height = map.height;
};

const saveMap = async () => {
  try {
    if (editingId.value) {
      await updateMap(editingId.value, { ...form });
      notify("Map updated.", "success");
    } else {
      await createMap({ ...form });
      notify("Map created.", "success");
    }
    resetForm();
    await loadMaps();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to save map.", "error");
  }
};

const deleteMapItem = async (id: number) => {
  try {
    await deleteMap(id);
    await loadMaps();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to delete map.", "error");
  }
};

onMounted(loadMaps);
</script>

<style scoped>
.map-preview {
  width: 100%;
  border-radius: 12px;
  border: 1px solid var(--stroke);
  margin-top: 8px;
}

.thumb {
  width: 120px;
  margin-top: 6px;
  border-radius: 8px;
  border: 1px solid var(--stroke);
}
</style>
