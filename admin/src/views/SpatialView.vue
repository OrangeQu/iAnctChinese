<template>
  <div class="section">
    <FilterBar>
      <select class="input" v-model.number="selectedMapId">
        <option :value="0">Select map</option>
        <option v-for="map in maps" :key="map.id" :value="map.id">
          #{{ map.id }} {{ map.dynasty || map.filename || "" }}
        </option>
      </select>
      <input class="input" v-model="yearFilter" placeholder="Year filter" />
      <button class="button secondary" type="button" @click="loadSpatial">Search</button>
      <span class="hint">Pick an entity, then click map to place a point.</span>
    </FilterBar>
  </div>

  <div class="section split">
    <div class="panel">
      <h2>Spatial Data</h2>
      <DataTable>
        <template #head>
          <th>Entity</th>
          <th>Map</th>
          <th>X</th>
          <th>Y</th>
          <th>Year</th>
          <th>Description</th>
          <th>Actions</th>
        </template>
        <tr v-if="spatialItems.length === 0">
          <td colspan="7">No spatial data.</td>
        </tr>
        <tr v-for="item in spatialItems" :key="item.id">
          <td>
            <div>{{ item.entityName || "-" }}</div>
            <small>#{{ item.entityId || "-" }}</small>
          </td>
          <td>#{{ item.mapId || "-" }}</td>
          <td>{{ formatNumber(item.x) }}</td>
          <td>{{ formatNumber(item.y) }}</td>
          <td>
            <input class="input small" v-model.number="editYear[item.id]" type="number" />
          </td>
          <td>
            <input class="input small" v-model="editDescription[item.id]" />
          </td>
          <td>
            <button class="button secondary" type="button" @click="saveSpatial(item.id)">
              Save
            </button>
            <button class="button ghost" type="button" @click="deleteSpatialItem(item.id)">
              Delete
            </button>
          </td>
        </tr>
      </DataTable>
    </div>

    <div class="panel">
      <h2>Map Editor</h2>
      <div class="entity-toolbar">
        <input class="input" v-model="entityQuery" placeholder="Search entity label" />
        <button class="button secondary" type="button" @click="searchEntityList">Search</button>
        <button class="button ghost" type="button" @click="clearEntitySelection">Clear</button>
      </div>
      <div class="entity-results" v-if="entityResults.length">
        <button
          v-for="entity in entityResults"
          :key="entity.id"
          class="chip"
          :class="{ active: selectedEntity?.id === entity.id }"
          type="button"
          @click="selectEntity(entity)"
        >
          {{ entity.label || "Unnamed" }} #{{ entity.id }}
        </button>
      </div>
      <div class="entity-current">
        <strong>Selected:</strong>
        <span v-if="selectedEntity">{{ selectedEntity.label }} (#{{ selectedEntity.id }})</span>
        <span v-else>None</span>
      </div>

      <div v-if="!currentMap" class="banner">Select a map to start editing.</div>
      <div v-else class="map-stage">
        <img
          ref="imageRef"
          class="map-image"
          :src="currentMap.filename"
          :style="mapStyle"
          alt="map"
          @load="syncScale"
          @click="handleMapClick"
        />
        <div
          v-for="item in spatialItems"
          :key="item.id"
          class="marker"
          :style="markerStyle(item)"
          @mousedown="startDrag($event, item)"
        >
          <span />
        </div>
      </div>
      <div class="map-meta" v-if="currentMap">
        <div>Map: #{{ currentMap.id }} {{ currentMap.dynasty || currentMap.filename }}</div>
        <div>Size: {{ currentMap.width || "-" }} x {{ currentMap.height || "-" }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import { notify } from "@/services/toast";
import { listMaps, type MapInfo } from "@/services/api/geo";
import {
  createSpatial,
  deleteSpatial,
  listSpatial,
  updateSpatial,
  type SpatialData,
} from "@/services/api/spatial";
import { searchEntities, type EntityAnnotation } from "@/services/api/annotations";

const maps = ref<MapInfo[]>([]);
const selectedMapId = ref<number>(0);
const yearFilter = ref("");
const spatialItems = ref<SpatialData[]>([]);
const editYear = ref<Record<number, number | null>>({});
const editDescription = ref<Record<number, string>>({});

const entityQuery = ref("");
const entityResults = ref<EntityAnnotation[]>([]);
const selectedEntity = ref<EntityAnnotation | null>(null);

const imageRef = ref<HTMLImageElement | null>(null);
const dragging = ref<{ id: number; offsetX: number; offsetY: number } | null>(null);
const draftPositions = ref<Record<number, { x: number; y: number }>>({});

const currentMap = computed(() => maps.value.find((map) => map.id === selectedMapId.value) || null);

const mapStyle = computed(() => {
  if (!currentMap.value?.width || !currentMap.value?.height) return undefined;
  return {
    width: `${currentMap.value.width}px`,
    height: `${currentMap.value.height}px`,
  };
});

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined) return "-";
  return Number(value).toFixed(2);
};

const syncScale = () => {
  // placeholder to trigger computed updates after image load
};

const getScale = () => {
  const map = currentMap.value;
  const image = imageRef.value;
  if (!map || !image) {
    return { scaleX: 1, scaleY: 1, rect: null as DOMRect | null };
  }
  const rect = image.getBoundingClientRect();
  const scaleX = map.width ? map.width / rect.width : 1;
  const scaleY = map.height ? map.height / rect.height : 1;
  return { scaleX, scaleY, rect };
};

const markerStyle = (item: SpatialData) => {
  if (item.x === null || item.x === undefined || item.y === null || item.y === undefined) return {};
  const { scaleX, scaleY } = getScale();
  const draft = draftPositions.value[item.id];
  const x = (draft?.x ?? item.x) / scaleX;
  const y = (draft?.y ?? item.y) / scaleY;
  return {
    left: `${x}px`,
    top: `${y}px`,
  };
};

const loadMaps = async () => {
  try {
    maps.value = await listMaps();
    if (maps.value.length && selectedMapId.value === 0) {
      selectedMapId.value = maps.value[0].id;
    }
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load maps.", "error");
  }
};

const loadSpatial = async () => {
  try {
    const year = yearFilter.value ? Number(yearFilter.value) : undefined;
    spatialItems.value = await listSpatial({
      mapId: selectedMapId.value || undefined,
      year: Number.isNaN(year) ? undefined : year,
    });
    draftPositions.value = {};
    const nextYear: Record<number, number | null> = {};
    const nextDesc: Record<number, string> = {};
    spatialItems.value.forEach((item) => {
      nextYear[item.id] = item.year ?? null;
      nextDesc[item.id] = item.description || "";
    });
    editYear.value = nextYear;
    editDescription.value = nextDesc;
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load spatial data.", "error");
  }
};

const saveSpatial = async (id: number) => {
  try {
    await updateSpatial(id, {
      year: editYear.value[id] ?? undefined,
      description: editDescription.value[id],
    });
    notify("Spatial data updated.", "success");
    await loadSpatial();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to update spatial data.", "error");
  }
};

const deleteSpatialItem = async (id: number) => {
  try {
    await deleteSpatial(id);
    await loadSpatial();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to delete spatial data.", "error");
  }
};

const searchEntityList = async () => {
  try {
    if (!entityQuery.value.trim()) {
      entityResults.value = [];
      return;
    }
    entityResults.value = await searchEntities(entityQuery.value.trim());
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to search entities.", "error");
  }
};

const selectEntity = (entity: EntityAnnotation) => {
  selectedEntity.value = entity;
};

const clearEntitySelection = () => {
  selectedEntity.value = null;
  entityResults.value = [];
  entityQuery.value = "";
};

const handleMapClick = async (event: MouseEvent) => {
  if (!selectedEntity.value || !currentMap.value) {
    notify("Select an entity before placing a marker.", "error");
    return;
  }
  const { scaleX, scaleY, rect } = getScale();
  if (!rect) return;
  const x = (event.clientX - rect.left) * scaleX;
  const y = (event.clientY - rect.top) * scaleY;
  const rawYear = yearFilter.value ? Number(yearFilter.value) : undefined;
  const year = rawYear !== undefined && Number.isNaN(rawYear) ? undefined : rawYear;
  try {
    await createSpatial({
      mapId: currentMap.value.id,
      entityId: selectedEntity.value.id,
      entityName: selectedEntity.value.label || "",
      x,
      y,
      year,
    });
    await loadSpatial();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to create spatial data.", "error");
  }
};

const startDrag = (event: MouseEvent, item: SpatialData) => {
  event.preventDefault();
  const { scaleX, scaleY, rect } = getScale();
  if (!rect || item.x === null || item.x === undefined || item.y === null || item.y === undefined) return;
  const displayX = item.x / scaleX;
  const displayY = item.y / scaleY;
  dragging.value = {
    id: item.id,
    offsetX: event.clientX - (rect.left + displayX),
    offsetY: event.clientY - (rect.top + displayY),
  };
};

const handleDragMove = (event: MouseEvent) => {
  if (!dragging.value) return;
  const { scaleX, scaleY, rect } = getScale();
  if (!rect) return;
  const map = currentMap.value;
  let x = (event.clientX - rect.left - dragging.value.offsetX) * scaleX;
  let y = (event.clientY - rect.top - dragging.value.offsetY) * scaleY;
  if (map?.width) x = Math.max(0, Math.min(map.width, x));
  if (map?.height) y = Math.max(0, Math.min(map.height, y));
  draftPositions.value = {
    ...draftPositions.value,
    [dragging.value.id]: { x, y },
  };
};

const handleDragEnd = async () => {
  if (!dragging.value) return;
  const id = dragging.value.id;
  const next = draftPositions.value[id];
  dragging.value = null;
  if (!next) return;
  try {
    await updateSpatial(id, { x: next.x, y: next.y });
    await loadSpatial();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to update position.", "error");
  }
};

onMounted(async () => {
  await loadMaps();
  await loadSpatial();
  window.addEventListener("mousemove", handleDragMove);
  window.addEventListener("mouseup", handleDragEnd);
});

watch(selectedMapId, () => {
  loadSpatial();
});

onUnmounted(() => {
  window.removeEventListener("mousemove", handleDragMove);
  window.removeEventListener("mouseup", handleDragEnd);
});
</script>

<style scoped>
.split {
  display: grid;
  grid-template-columns: 1.1fr 1.4fr;
  gap: 20px;
}

.panel {
  background: var(--panel);
  border: 1px solid var(--stroke);
  border-radius: 16px;
  padding: 16px;
}

.entity-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.entity-results {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.chip {
  border: 1px solid var(--stroke);
  background: rgba(255, 255, 255, 0.7);
  border-radius: 20px;
  padding: 6px 10px;
  font-size: 12px;
}

.chip.active {
  background: rgba(47, 92, 255, 0.2);
  border-color: rgba(47, 92, 255, 0.6);
}

.entity-current {
  font-size: 12px;
  margin-bottom: 12px;
}

.map-stage {
  position: relative;
  display: inline-block;
  border-radius: 14px;
  border: 1px solid var(--stroke);
  background: rgba(0, 0, 0, 0.02);
  overflow: hidden;
}

.map-image {
  display: block;
  max-width: 100%;
  cursor: crosshair;
}

.marker {
  position: absolute;
  left: 0;
  top: 0;
  width: 16px;
  height: 16px;
  transform: translate(-8px, -8px);
  cursor: grab;
}

.marker span {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(255, 90, 72, 0.85);
  box-shadow: 0 0 8px rgba(255, 90, 72, 0.5);
}

.map-meta {
  margin-top: 12px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.7);
}

.hint {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.6);
}

@media (max-width: 1080px) {
  .split {
    grid-template-columns: 1fr;
  }
}
</style>
