<template>
  <div class="map-wrapper">
    <div class="map-header">
      <h3 class="section-title">地理标注</h3>
      <el-select v-model="selectedModel" placeholder="选择大模型" style="width: 200px" size="small" filterable>
        <el-option v-for="m in modelOptions" :key="m.id" :label="m.label" :value="m.id" />
      </el-select>
      <el-select
        v-model="selectedEntityId"
        placeholder="选择实体后点击地图放置"
        style="width: 220px"
        size="small"
        clearable
        filterable
      >
        <el-option
          v-for="e in selectableEntities"
          :key="e.id || e.label"
          :label="e.label || e.name"
          :value="e.id || e.label"
        />
      </el-select>
      <el-button type="primary" size="small" :loading="locating" @click="handleAutoLocate">自动模型标注</el-button>
      <el-tag v-if="!hasKey" type="warning" size="small">未配置 VITE_TMAP_KEY，无法显示地图</el-tag>
    </div>

    <div class="map-canvas" ref="mapEl">
      <div v-if="!hasKey" class="placeholder">请在 .env.local 配置 VITE_TMAP_KEY</div>
      <div v-else-if="loadError" class="placeholder">
        地图加载失败：{{ loadError }}。请检查 Key、域名/IP 白名单是否包含 http://localhost:5173 和当前出口 IP。
      </div>
    </div>

    <div class="legend">
      <div>已定位：{{ locatedMarkers.length }} 个</div>
      <div>未定位：{{ unlocatedCount }} 个</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import { locateEntities } from "@/api/geo";

const props = defineProps({
  locations: { type: Array, default: () => [] },
  points: { type: Array, default: () => [] },
  allEntities: { type: Array, default: () => [] }
});

const store = useTextStore();
const mapEl = ref(null);
const map = ref(null);
const markerLayer = ref(null);
const labelLayer = ref(null);
const locating = ref(false);
const selectedEntityId = ref(null);
const llmPoints = ref([]);
const locatedMarkers = ref([]);
const loadError = ref("");

const modelOptions = [
  { id: "deepseek-ai/DeepSeek-V3", label: "DeepSeek-V3" },
  { id: "deepseek-ai/DeepSeek-R1", label: "DeepSeek-R1" },
  { id: "Qwen/Qwen2.5-72B-Instruct", label: "Qwen2.5-72B" },
  { id: "Qwen/Qwen3-8B", label: "Qwen3-8B" }
];
const selectedModel = ref(modelOptions[0].id);

const hasKey = computed(() => !!import.meta.env.VITE_TMAP_KEY);
const storeEntities = computed(() => store.entities || []);

const locationList = computed(() => {
  const base =
    (props.locations && props.locations.length && props.locations) ||
    (props.allEntities && props.allEntities.length && props.allEntities) ||
    storeEntities.value;
  return (base || []).filter((e) => e.category === "LOCATION");
});

const selectableEntities = computed(() => {
  if (locationList.value.length) return locationList.value;
  if (props.allEntities && props.allEntities.length) return props.allEntities;
  return storeEntities.value;
});

const unlocatedCount = computed(() => {
  const locatedIds = new Set(locatedMarkers.value.map((m) => m.entityId));
  return (locationList.value || []).filter((e) => !locatedIds.has(e.id)).length;
});

const loadTMap = () =>
  new Promise((resolve, reject) => {
    if (window.TMap) return resolve(window.TMap);
    const script = document.createElement("script");
    script.src = `https://map.qq.com/api/gljs?v=1.exp&key=${import.meta.env.VITE_TMAP_KEY}`;
    script.onload = () => (window.TMap ? resolve(window.TMap) : reject(new Error("TMap 未加载")));
    script.onerror = (err) => reject(err);
    document.body.appendChild(script);
  });

const MARKER_ICON_LLM = "data:image/svg+xml;base64," + btoa(`
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="40" viewBox="0 0 32 40">
  <path d="M16 0C7.2 0 0 7.2 0 16c0 12 16 24 16 24s16-12 16-24C32 7.2 24.8 0 16 0z" fill="#3b82f6"/>
  <circle cx="16" cy="14" r="6" fill="#ffffff"/>
</svg>`);

const MARKER_ICON_MANUAL = "data:image/svg+xml;base64," + btoa(`
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="40" viewBox="0 0 32 40">
  <path d="M16 0C7.2 0 0 7.2 0 16c0 12 16 24 16 24s16-12 16-24C32 7.2 24.8 0 16 0z" fill="#f97316"/>
  <circle cx="16" cy="14" r="6" fill="#ffffff"/>
</svg>`);

const markerStyles = {
  llm: () =>
    new window.TMap.MarkerStyle({
      width: 32,
      height: 40,
      src: MARKER_ICON_LLM,
      anchor: { x: 16, y: 40 }
    }),
  manual: () =>
    new window.TMap.MarkerStyle({
      width: 32,
      height: 40,
      src: MARKER_ICON_MANUAL,
      anchor: { x: 16, y: 40 }
    })
};

const labelStyle = () =>
  new window.TMap.LabelStyle({
    color: "#111",
    size: 14,
    bold: true,
    background: true,
    borderRadius: 4,
    padding: "4px 6px",
    align: "center",
    verticalAlign: "middle"
  });

const resetLayers = () => {
  if (!map.value || !window.TMap) return;
  markerLayer.value = new window.TMap.MultiMarker({
    map: map.value,
    styles: {
      llm: markerStyles.llm(),
      manual: markerStyles.manual()
    },
    geometries: []
  });
  labelLayer.value = new window.TMap.MultiLabel({
    map: map.value,
    styles: {
      default: labelStyle()
    },
    geometries: []
  });
};

const clearMarkers = () => {
  if (markerLayer.value) markerLayer.value.setGeometries([]);
  if (labelLayer.value) labelLayer.value.setGeometries([]);
  locatedMarkers.value = [];
};

const addMarker = ({ entityId, label, lat, lng, source }) => {
  if (!markerLayer.value || !labelLayer.value || !window.TMap) return;
  const id = String(entityId);
  const position = new window.TMap.LatLng(lat, lng);

  // 去重：同一个实体只留最新
  const geos = markerLayer.value.getGeometries().filter((g) => g.id !== id);
  geos.push({
    id,
    styleId: source === "manual" ? "manual" : "llm",
    position,
    properties: { label, source }
  });
  markerLayer.value.setGeometries(geos);

  const labels = labelLayer.value.getGeometries().filter((g) => g.id !== `lbl-${id}`);
  labels.push({
    id: `lbl-${id}`,
    styleId: "default",
    position,
    content: label || id
  });
  labelLayer.value.setGeometries(labels);

  locatedMarkers.value = geos.map((g) => ({
    entityId: Number(g.id),
    label: g.properties?.label || g.id,
    source: g.properties?.source || "llm"
  }));
};

const focusToMarkers = () => {
  if (!markerLayer.value) return;
  const geos = markerLayer.value.getGeometries();
  if (!geos.length) return;
  if (geos.length === 1) {
    map.value.setCenter(geos[0].position);
    map.value.setZoom(7);
    return;
  }
  const bounds = new window.TMap.LatLngBounds();
  geos.forEach((g) => bounds.extend(g.position));
  map.value.fitBounds(bounds);
};

const applyLlmPoints = () => {
  (llmPoints.value || []).forEach((p) => {
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng;
    if (lat && lng) {
      addMarker({
        entityId: p.entityId || p.id || p.label,
        label: p.label || p.name || p.id,
        lat,
        lng,
        source: "llm"
      });
    }
  });
  focusToMarkers();
};

const updateMarkers = () => {
  if (!map.value || !markerLayer.value) return;
  clearMarkers();

  (props.points || []).forEach((p) => {
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng;
    if (lat && lng) {
      addMarker({
        entityId: p.entityId || p.id || p.label,
        label: p.label || p.name || p.id,
        lat,
        lng,
        source: "llm"
      });
    }
  });

  applyLlmPoints();
};

const initMap = async () => {
  if (!hasKey.value) return;
  try {
    const TMap = await loadTMap();
    map.value = new TMap.Map(mapEl.value, {
      center: new TMap.LatLng(35.8617, 104.1954),
      zoom: 4
    });
    resetLayers();
    map.value.on("click", handleMapClick);
    updateMarkers();
  } catch (err) {
    console.error("TMap 加载失败", err);
    loadError.value =
      err?.message || "网络/授权问题，建议检查 Key、域名/IP 白名单或刷新重试";
  }
};

const handleMapClick = (evt) => {
  if (!selectedEntityId.value || !evt?.latLng) return;
  const target = selectableEntities.value.find(
    (e) => (e.id || e.label) === selectedEntityId.value
  );
  if (!target) return;
  addMarker({
    entityId: target.id || target.label,
    label: target.label || target.name || target.id,
    lat: evt.latLng.getLat(),
    lng: evt.latLng.getLng(),
    source: "manual"
  });
  focusToMarkers();
  ElMessage.success(`已放置标注：${target.label || target.name || target.id}`);
};

const handleAutoLocate = async () => {
  try {
    if (!store.selectedTextId) {
      ElMessage.warning("请先选择一篇文本");
      return;
    }
    const entities = (locationList.value || []).map((e) => ({
      id: e.id,
      label: e.label || e.name,
      category: e.category
    }));
    if (!entities.length) {
      ElMessage.warning("没有地点类实体可标注");
      return;
    }
    locating.value = true;
    const { data } = await locateEntities({
      textId: store.selectedTextId,
      model: selectedModel.value,
      entities
    });
    llmPoints.value = Array.isArray(data) ? data : data ? [data] : [];
    updateMarkers();
    if (!llmPoints.value.length) {
      ElMessage.warning("模型未返回坐标");
    } else {
      ElMessage.success("自动标注完成");
    }
  } catch (err) {
    console.error(err);
    ElMessage.error("自动标注失败");
  } finally {
    locating.value = false;
  }
};

watch(
  () => [props.points, llmPoints.value],
  () => nextTick(() => updateMarkers()),
  { deep: true }
);

onMounted(async () => {
  await nextTick();
  await initMap();
});

onBeforeUnmount(() => {
  clearMarkers();
  map.value = null;
});
</script>

<style scoped>
.map-wrapper {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}
.map-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.map-canvas {
  width: 100%;
  height: 600px;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f7fa;
  position: relative;
}
.placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}
.legend {
  margin-top: 8px;
  color: #555;
  font-size: 13px;
  display: flex;
  gap: 16px;
}
.map-label {
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
}
</style>
