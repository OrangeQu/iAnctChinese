<template>
  <div class="map-wrapper">
    <div class="map-header">
      <h3 class="section-title">地理标注</h3>
      <el-select v-model="selectedModel" placeholder="选择大模型" style="width: 180px" size="small" filterable>
        <el-option v-for="m in modelOptions" :key="m.id" :label="m.label" :value="m.id" />
      </el-select>
      <el-button type="primary" size="small" :loading="locating" @click="handleAutoLocate">自动模型标注</el-button>
      <el-checkbox v-model="showTravelPath" v-if="isTravelogue" size="small">显示行进路线</el-checkbox>
      <el-checkbox v-model="showNonLocationEntities" size="small">显示非地点实体</el-checkbox>
      <el-tag v-if="!hasKey" type="warning" size="small">未配置 VITE_TMAP_KEY</el-tag>
      <el-button v-if="hasKey" size="small" @click="routeEditorOpen = true">路线编辑</el-button>
    </div>

    <div class="map-content">
      <!-- 左侧实体列表 -->
      <div class="entity-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">可用实体</span>
          <el-select v-model="entityCategoryFilter" placeholder="筛选类型" size="small" clearable style="width: 100px">
            <el-option label="全部" value="" />
            <el-option label="地点" value="LOCATION" />
            <el-option label="人物" value="PERSON" />
            <el-option label="事件" value="EVENT" />
            <el-option label="组织" value="ORGANIZATION" />
          </el-select>
        </div>
        <div class="entity-list">
          <div
            v-for="entity in filteredSidebarEntities"
            :key="entity.id || entity.label"
            class="entity-card"
            :class="{
              'is-located': isEntityLocated(entity.id),
              'is-selected': selectedEntityId === (entity.id || entity.label),
              'is-dragging': draggingEntityId === (entity.id || entity.label)
            }"
            draggable="true"
            @dragstart="onEntityDragStart($event, entity)"
            @dragend="onEntityDragEnd"
            @click="selectEntity(entity)"
          >
            <span class="entity-icon" :style="{ background: getEntityColor(entity.category) }"></span>
            <span class="entity-name">{{ entity.label || entity.name }}</span>
            <span v-if="isEntityLocated(entity.id)" class="located-badge">✓</span>
          </div>
          <div v-if="filteredSidebarEntities.length === 0" class="empty-tip">暂无实体</div>
        </div>
      </div>

      <!-- 地图区域 -->
      <div class="map-area">
        <div
          class="map-canvas"
          ref="mapEl"
          @dragover.prevent="onMapDragOver"
          @drop="onMapDrop"
        >
          <div v-if="!hasKey" class="placeholder">请在 .env.local 配置 VITE_TMAP_KEY</div>
          <div v-else-if="loadError" class="placeholder">
            地图加载失败：{{ loadError }}
          </div>
        </div>

        <!-- 底部信息栏 -->
        <div class="map-footer">
          <div class="legend">
            <span class="legend-item">
              <span class="marker-dot llm"></span>自动标注: {{ autoLocatedCount }}
            </span>
            <span class="legend-item">
              <span class="marker-dot manual"></span>手动标注: {{ manualLocatedCount }}
            </span>
            <span class="legend-item">未定位: {{ unlocatedCount }}</span>
          </div>
          <div class="tip" v-if="selectedEntityId">
            提示: 点击地图放置「{{ selectedEntityLabel }}」，或拖拽实体到地图上
          </div>
        </div>
      </div>

      <!-- 右侧已标注列表 -->
      <div class="located-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">已标注 ({{ locatedMarkers.length }})</span>
        </div>
        <div class="located-list">
          <div
            v-for="marker in locatedMarkers"
            :key="marker.entityId"
            class="located-card"
            :class="{ 'is-manual': marker.source === 'manual' }"
            @click="focusMarker(marker)"
          >
            <div class="located-info">
              <span class="entity-icon" :style="{ background: getEntityColor(marker.category) }"></span>
              <span class="located-name">{{ marker.label }}</span>
            </div>
            <div class="located-actions">
              <el-tooltip content="定位" placement="top">
                <el-button size="small" :icon="Location" circle @click.stop="focusMarker(marker)" />
              </el-tooltip>
              <el-tooltip content="删除标注" placement="top">
                <el-button size="small" :icon="Delete" circle type="danger" @click.stop="removeMarker(marker)" />
              </el-tooltip>
            </div>
          </div>
          <div v-if="locatedMarkers.length === 0" class="empty-tip">暂无标注</div>
        </div>
      </div>
    </div>

    <el-drawer v-model="routeEditorOpen" title="路线编辑" size="420px" :with-header="true">
      <div class="route-editor">
        <div class="route-section">
          <div class="route-section-header">
            <div class="route-section-title">自定义连线</div>
            <el-button size="small" type="danger" plain :disabled="manualRoutes.length === 0" @click="clearManualRoutes">清空</el-button>
          </div>
          <div class="route-form" v-if="locatedMarkers.length >= 2">
            <el-select v-model="routeFromId" placeholder="路线起点" style="width: 160px" size="small" clearable filterable>
              <el-option v-for="opt in routeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="routeToId" placeholder="路线终点" style="width: 160px" size="small" clearable filterable>
              <el-option v-for="opt in routeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button size="small" type="primary" @click="addManualRoute">添加</el-button>
          </div>
          <div v-else class="empty-tip">至少需要 2 个标注点才能添加路线</div>

          <div v-if="manualRoutes.length === 0" class="empty-tip">暂无自定义路线</div>
          <div v-for="route in manualRoutes" :key="route.id" class="route-row">
            <div class="route-row-name">{{ route.fromLabel }} → {{ route.toLabel }}</div>
            <el-button size="small" :icon="Delete" circle type="danger" @click="removeManualRoute(route.id)" />
          </div>
          <div class="route-hint">提示：点击地图上的橙色连线可直接删除该自定义路线</div>
        </div>

        <div v-if="isTravelogue" class="route-section">
          <div class="route-section-header">
            <div class="route-section-title">自动行进路线（顺序可编辑）</div>
            <el-button size="small" plain :disabled="travelRouteMarkers.length === 0" @click="resetTravelOrder">重置</el-button>
          </div>
          <div v-if="travelRouteMarkers.length === 0" class="empty-tip">暂无地点标注</div>
          <div v-for="(m, idx) in travelRouteMarkers" :key="String(m.entityId)" class="route-point-row">
            <div class="route-point-label">{{ idx + 1 }}. {{ m.label }}</div>
            <div class="route-point-actions">
              <el-button size="small" plain :disabled="idx === 0" @click="moveTravelPoint(idx, -1)">↑</el-button>
              <el-button size="small" plain :disabled="idx === travelRouteMarkers.length - 1" @click="moveTravelPoint(idx, 1)">↓</el-button>
              <el-button size="small" type="danger" plain @click="removeTravelPoint(idx)">移除</el-button>
            </div>
          </div>
          <div class="route-hint">提示：这里修改的是“显示行进路线”的连接顺序（默认只包含地点类实体）</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch, markRaw } from "vue";
import { ElMessage } from "element-plus";
import { Location, Delete } from "@element-plus/icons-vue";
import { useTextStore } from "@/store/textStore";
import { locateEntities } from "@/api/geo";

const MARKER_ICON_LLM =
  "data:image/svg+xml;base64," +
  btoa(`
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="40" viewBox="0 0 32 40">
  <path d="M16 0C7.2 0 0 7.2 0 16c0 12 16 24 16 24s16-12 16-24C32 7.2 24.8 0 16 0z" fill="#3b82f6"/>
  <circle cx="16" cy="14" r="6" fill="#ffffff"/>
</svg>`);

const MARKER_ICON_MANUAL =
  "data:image/svg+xml;base64," +
  btoa(`
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="40" viewBox="0 0 32 40">
  <path d="M16 0C7.2 0 0 7.2 0 16c0 12 16 24 16 24s16-12 16-24C32 7.2 24.8 0 16 0z" fill="#f97316"/>
  <circle cx="16" cy="14" r="6" fill="#ffffff"/>
</svg>`);

const props = defineProps({
  locations: { type: Array, default: () => [] },
  points: { type: Array, default: () => [] },
  allEntities: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] }
});

const emit = defineEmits(["marker-updated", "marker-removed"]);

const store = useTextStore();
const mapEl = ref(null);
const map = shallowRef(null);
const markerLayer = shallowRef(null);
const labelLayer = shallowRef(null);
const polylineLayer = shallowRef(null);
const locating = ref(false);
const selectedEntityId = ref(null);
const draggingEntityId = ref(null);
const llmPoints = ref([]);
const locatedMarkers = ref([]);
const loadError = ref("");
const entityCategoryFilter = ref("");
const showTravelPath = ref(true);
const showNonLocationEntities = ref(false);
const routeEditorOpen = ref(false);

const modelOptions = [
  { id: "deepseek-ai/DeepSeek-V3", label: "DeepSeek-V3" },
  { id: "deepseek-ai/DeepSeek-R1", label: "DeepSeek-R1" },
  { id: "Qwen/Qwen2.5-72B-Instruct", label: "Qwen2.5-72B" },
  { id: "Qwen/Qwen3-8B", label: "Qwen3-8B" }
];
const selectedModel = ref(modelOptions[0].id);

const hasKey = computed(() => !!import.meta.env.VITE_TMAP_KEY);
const storeEntities = computed(() => store.entities || []);
const storeRelations = computed(() => props.relations?.length ? props.relations : (store.relations || []));

const isTravelogue = computed(() => {
  const category = store.selectedText?.category;
  return category === "travelogue" || category === "warfare" || category === "other";
});

const allEntityList = computed(() => {
  const base =
    (props.allEntities && props.allEntities.length && props.allEntities) ||
    storeEntities.value;
  return base || [];
});

const locationList = computed(() => {
  return allEntityList.value.filter((e) => e.category === "LOCATION");
});

const selectableEntities = computed(() => {
  if (showNonLocationEntities.value) {
    return allEntityList.value;
  }
  return locationList.value.length ? locationList.value : allEntityList.value;
});

const filteredSidebarEntities = computed(() => {
  let list = selectableEntities.value;
  if (entityCategoryFilter.value) {
    list = list.filter((e) => e.category === entityCategoryFilter.value);
  }
  return list;
});

const unlocatedCount = computed(() => {
  const locatedIds = new Set(locatedMarkers.value.map((m) => m.entityId));
  return selectableEntities.value.filter((e) => !locatedIds.has(e.id)).length;
});

const autoLocatedCount = computed(() => {
  return locatedMarkers.value.filter((m) => m.source === "llm").length;
});

const manualLocatedCount = computed(() => {
  return locatedMarkers.value.filter((m) => m.source === "manual").length;
});

const selectedEntityLabel = computed(() => {
  if (!selectedEntityId.value) return "";
  const entity = selectableEntities.value.find((e) => (e.id || e.label) === selectedEntityId.value);
  return entity?.label || entity?.name || "";
});

const isEntityLocated = (entityId) => {
  return locatedMarkers.value.some((m) => m.entityId === entityId);
};

const getEntityColor = (category) => {
  const colors = {
    LOCATION: "#2ecc71",
    PERSON: "#e74c3c",
    EVENT: "#f1c40f",
    ORGANIZATION: "#3498db",
    OBJECT: "#9b59b6",
    CUSTOM: "#95a5a6"
  };
  return colors[category] || colors.CUSTOM;
};

const loadTMap = () =>
  new Promise((resolve, reject) => {
    if (window.TMap) return resolve(window.TMap);
    const script = document.createElement("script");
    script.src = `https://map.qq.com/api/gljs?v=1.exp&key=${import.meta.env.VITE_TMAP_KEY}`;
    script.onload = () => (window.TMap ? resolve(window.TMap) : reject(new Error("TMap 未加载")));
    script.onerror = (err) => reject(err);
    document.body.appendChild(script);
  });

const getMarkerStyleId = (source) => {
  return source === "manual" ? "manual" : "llm";
};

const toTMapLatLng = (lat, lng) => new window.TMap.LatLng(Number(lat), Number(lng));

const readLatLng = (geo) => {
  const lat = geo?.position?.getLat?.() ?? geo?.position?.lat ?? geo?.lat;
  const lng = geo?.position?.getLng?.() ?? geo?.position?.lng ?? geo?.lng;
  return { lat, lng };
};

const createLabelStyle = (TMap) => {
  if (!TMap) return null;
  return new TMap.LabelStyle({
    color: "#111",
    size: 14,
    bold: true,
    background: true,
    borderRadius: 4,
    padding: "4px 6px",
    align: "center",
    verticalAlign: "middle"
  });
};

const resetLayers = () => {
  if (!map.value || !window.TMap) {
    console.warn("[MapView] resetLayers: map or TMap not ready");
    return;
  }
  const TMap = window.TMap;
  console.log("[MapView] resetLayers: TMap available, creating styles...");

  // 显式指定 src，避免默认示例图标 404 导致标记不可见
  const styles = {
    llm: new TMap.MarkerStyle({
      width: 32,
      height: 40,
      src: MARKER_ICON_LLM,
      anchor: { x: 16, y: 40 }
    }),
    manual: new TMap.MarkerStyle({
      width: 32,
      height: 40,
      src: MARKER_ICON_MANUAL,
      anchor: { x: 16, y: 40 }
    })
  };
  console.log("[MapView] resetLayers: using inline SVG marker styles");
  markerLayer.value = markRaw(
    new TMap.MultiMarker({
      map: map.value,
      styles,
      geometries: []
    })
  );
  console.log("[MapView] resetLayers: MultiMarker created, visible:", markerLayer.value.getVisible());
  const lblStyle = createLabelStyle(TMap);
  labelLayer.value = markRaw(
    new TMap.MultiLabel({
      map: map.value,
      styles: lblStyle ? { default: lblStyle } : {},
      geometries: []
    })
  );
  console.log("[MapView] resetLayers: layers initialized successfully");
};

const clearMarkers = () => {
  if (markerLayer.value) markerLayer.value.setGeometries([]);
  if (labelLayer.value) labelLayer.value.setGeometries([]);
  locatedMarkers.value = [];
};

const addMarker = ({ entityId, label, lat, lng, source, category }) => {
  if (!markerLayer.value || !labelLayer.value || !window.TMap) return;
  const id = String(entityId);
  const position = toTMapLatLng(lat, lng);

  const entity = selectableEntities.value.find((e) => e.id === entityId || e.id === Number(entityId));
  const entityCategory = category || entity?.category || "LOCATION";
  const styleId = getMarkerStyleId(source);

  const geos = markerLayer.value.getGeometries().filter((g) => g.id !== id);
  geos.push({
    id,
    styleId,
    position,
    properties: { label, source, category: entityCategory, lat, lng }
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

  updateLocatedMarkersList();
};

const updateLocatedMarkersList = () => {
  if (!markerLayer.value) return;
  const geos = markerLayer.value.getGeometries();
  locatedMarkers.value = geos.map((g) => ({
    entityId: Number(g.id) || g.id,
    label: g.properties?.label || g.id,
    source: g.properties?.source || "llm",
    category: g.properties?.category || "LOCATION",
    lat: g.position?.getLat?.() ?? g.position?.lat ?? g.properties?.lat,
    lng: g.position?.getLng?.() ?? g.position?.lng ?? g.properties?.lng
  }));
};

let focusTimer = null;
const focusToMarkers = (markers = null) => {
  if (!map.value || !window.TMap) {
    console.warn("[MapView] focusToMarkers: map or TMap not ready");
    return;
  }

  // 防抖：取消之前的定时器
  if (focusTimer) {
    clearTimeout(focusTimer);
    focusTimer = null;
  }

  // 如果传入了 markers 参数，直接使用，否则从图层获取
  const geos = markers || (markerLayer.value ? markerLayer.value.getGeometries() : []);
  console.log("[MapView] focusToMarkers: processing", geos.length, "markers");

  if (!geos.length) {
    console.warn("[MapView] focusToMarkers: no markers to focus on");
    return;
  }

  // 收集所有有效坐标
  const validCoords = [];
  geos.forEach((g) => {
    const { lat, lng } = readLatLng(g);
    if (typeof lat === 'number' && typeof lng === 'number' &&
        lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      validCoords.push({ lat, lng });
    }
  });

  if (!validCoords.length) {
    console.warn("[MapView] focusToMarkers: no valid coordinates");
    return;
  }

  // 使用防抖延迟执行
  focusTimer = setTimeout(() => {
    if (!map.value) return;

    if (validCoords.length === 1) {
      console.log("[MapView] focusToMarkers: single marker at", validCoords[0].lat, validCoords[0].lng);
      map.value.setCenter(new window.TMap.LatLng(validCoords[0].lat, validCoords[0].lng));
      map.value.setZoom(12);
      return;
    }

    // 计算中心点和合适的缩放级别
    let minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
    validCoords.forEach(({ lat, lng }) => {
      minLat = Math.min(minLat, lat);
      maxLat = Math.max(maxLat, lat);
      minLng = Math.min(minLng, lng);
      maxLng = Math.max(maxLng, lng);
    });

    const centerLat = (minLat + maxLat) / 2;
    const centerLng = (minLng + maxLng) / 2;
    const latSpan = maxLat - minLat;
    const lngSpan = maxLng - minLng;
    const maxSpan = Math.max(latSpan, lngSpan);

    // 根据跨度计算缩放级别
    let zoom = 10;
    if (maxSpan < 0.01) zoom = 15;
    else if (maxSpan < 0.05) zoom = 13;
    else if (maxSpan < 0.1) zoom = 12;
    else if (maxSpan < 0.5) zoom = 10;
    else if (maxSpan < 1) zoom = 9;
    else if (maxSpan < 5) zoom = 7;
    else zoom = 5;

    console.log("[MapView] focusToMarkers: center=", centerLat, centerLng, "zoom=", zoom, "span=", maxSpan);
    map.value.setCenter(new window.TMap.LatLng(centerLat, centerLng));
    map.value.setZoom(zoom);
  }, 200);
};

const applyLlmPoints = () => {
  const markers = [];
  (llmPoints.value || []).forEach((p) => {
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng;
    if (Number.isFinite(Number(lat)) && Number.isFinite(Number(lng))) {
      const marker = {
        entityId: p.entityId || p.id || p.label,
        label: p.label || p.name || p.id,
        lat: Number(lat),
        lng: Number(lng),
        source: "llm"
      };
      addMarker(marker);
      markers.push(marker);
    }
  });
  if (markers.length > 0) {
    focusToMarkers(markers);
  }
};

const updateMarkers = () => {
  if (!map.value || !markerLayer.value) return;
  clearMarkers();

  (props.points || []).forEach((p) => {
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng;
    if (Number.isFinite(Number(lat)) && Number.isFinite(Number(lng))) {
      addMarker({
        entityId: p.entityId || p.id || p.label,
        label: p.label || p.name || p.id,
        lat: Number(lat),
        lng: Number(lng),
        source: "llm"
      });
    }
  });

  applyLlmPoints();
};

const initMap = async () => {
  if (!hasKey.value) return;
  try {
    console.log("[MapView] Loading TMap...");
    const TMap = await loadTMap();
    console.log("[MapView] TMap loaded, creating map...");
    map.value = markRaw(new TMap.Map(mapEl.value, {
      center: new TMap.LatLng(35.8617, 104.1954),
      zoom: 4
    }));
    console.log("[MapView] Map created, initializing layers...");
    resetLayers();
    initPolylineLayer();
    map.value.on("click", handleMapClick);
    setupMarkerDragEvents();
    updateMarkers();
    console.log("[MapView] Map initialization complete. Entities:", locationList.value.length);

    // 注意：除非用户主动点击“自动模型标注”，否则不要自动触发任何标注请求
  } catch (err) {
    console.error("TMap 加载失败", err);
    loadError.value = err?.message || "网络/授权问题";
  }
};

const initPolylineLayer = () => {
  if (!map.value || !window.TMap) return;
  polylineLayer.value = markRaw(
    new window.TMap.MultiPolyline({
      map: map.value,
      styles: {
        travel: new window.TMap.PolylineStyle({
          color: "#409eff",
          width: 4,
          lineCap: "round",
          showArrow: true,
          arrowOptions: { width: 6, space: 50 }
        }),
        route: new window.TMap.PolylineStyle({
          color: "#f97316",
          width: 4,
          lineCap: "round",
          showArrow: true,
          arrowOptions: { width: 6, space: 60 }
        })
      },
      geometries: []
    })
  );

  polylineLayer.value.on("click", (evt) => {
    const id = evt?.geometry?.id ? String(evt.geometry.id) : "";
    if (id.startsWith("route-")) {
      removeManualRoute(id);
    }
  });
};

const routeFromId = ref(null);
const routeToId = ref(null);
const manualRoutes = ref([]);

const routeOptions = computed(() => {
  return (locatedMarkers.value || []).map((m) => ({
    value: String(m.entityId),
    label: m.label || String(m.entityId)
  }));
});

const travelOrderIds = ref([]);

const travelOrderStorageKey = computed(() => {
  return store.selectedTextId ? `ianctchinese:travelOrder:${store.selectedTextId}` : null;
});

const getTravelCandidates = () => {
  return locatedMarkers.value.filter((m) => m.category === "LOCATION" || !m.category);
};

const loadTravelOrder = () => {
  const key = travelOrderStorageKey.value;
  if (!key) {
    travelOrderIds.value = [];
    return;
  }
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : [];
    travelOrderIds.value = Array.isArray(parsed) ? parsed.map((x) => String(x)) : [];
  } catch (e) {
    console.warn("[MapView] loadTravelOrder failed:", e);
    travelOrderIds.value = [];
  }
};

const saveTravelOrder = () => {
  const key = travelOrderStorageKey.value;
  if (!key) return;
  try {
    localStorage.setItem(key, JSON.stringify(travelOrderIds.value || []));
  } catch (e) {
    console.warn("[MapView] saveTravelOrder failed:", e);
  }
};

const travelRouteMarkers = computed(() => {
  const markers = getTravelCandidates();
  const byId = new Map(markers.map((m) => [String(m.entityId), m]));
  const ordered = [];

  (travelOrderIds.value || []).forEach((id) => {
    const m = byId.get(id);
    if (m) {
      ordered.push(m);
      byId.delete(id);
    }
  });

  markers.forEach((m) => {
    const id = String(m.entityId);
    if (byId.has(id)) {
      ordered.push(m);
      byId.delete(id);
    }
  });

  return ordered;
});

const manualRoutesStorageKey = computed(() => {
  return store.selectedTextId ? `ianctchinese:manualRoutes:${store.selectedTextId}` : null;
});

const loadManualRoutes = () => {
  const key = manualRoutesStorageKey.value;
  if (!key) {
    manualRoutes.value = [];
    return;
  }
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : [];
    manualRoutes.value = Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    console.warn("[MapView] loadManualRoutes failed:", e);
    manualRoutes.value = [];
  }
};

const saveManualRoutes = () => {
  const key = manualRoutesStorageKey.value;
  if (!key) return;
  try {
    localStorage.setItem(key, JSON.stringify(manualRoutes.value || []));
  } catch (e) {
    console.warn("[MapView] saveManualRoutes failed:", e);
  }
};

const updatePolylines = () => {
  if (!polylineLayer.value || !window.TMap) return;

  const geometries = [];

  if (showTravelPath.value && isTravelogue.value) {
    if (travelRouteMarkers.value.length >= 2) {
      const path = travelRouteMarkers.value.map((m) => toTMapLatLng(m.lat, m.lng));
      geometries.push({ id: "travel-path", styleId: "travel", paths: path });
    }
  }

  (manualRoutes.value || []).forEach((r) => {
    const from = locatedMarkers.value.find((m) => String(m.entityId) === String(r.fromId));
    const to = locatedMarkers.value.find((m) => String(m.entityId) === String(r.toId));
    if (!from || !to) return;
    geometries.push({
      id: r.id,
      styleId: "route",
      paths: [toTMapLatLng(from.lat, from.lng), toTMapLatLng(to.lat, to.lng)]
    });
  });

  polylineLayer.value.setGeometries(geometries);
};

const addManualRoute = () => {
  const fromId = routeFromId.value != null ? String(routeFromId.value) : "";
  const toId = routeToId.value != null ? String(routeToId.value) : "";
  if (!fromId || !toId) {
    ElMessage.warning("请选择路线起点和终点");
    return;
  }
  if (fromId === toId) {
    ElMessage.warning("起点和终点不能相同");
    return;
  }
  const from = locatedMarkers.value.find((m) => String(m.entityId) === fromId);
  const to = locatedMarkers.value.find((m) => String(m.entityId) === toId);
  if (!from || !to) {
    ElMessage.warning("起点/终点未找到对应标注");
    return;
  }
  manualRoutes.value.push({
    id: `route-${fromId}-${toId}-${Date.now()}`,
    fromId,
    toId,
    fromLabel: from.label || fromId,
    toLabel: to.label || toId
  });
  saveManualRoutes();
  updatePolylines();
  ElMessage.success("已添加路线");
};

const removeManualRoute = (routeId) => {
  manualRoutes.value = (manualRoutes.value || []).filter((r) => r.id !== routeId);
  saveManualRoutes();
  updatePolylines();
};

const clearManualRoutes = () => {
  manualRoutes.value = [];
  saveManualRoutes();
  updatePolylines();
};

const resetTravelOrder = () => {
  travelOrderIds.value = getTravelCandidates().map((m) => String(m.entityId));
  saveTravelOrder();
  updatePolylines();
};

const moveTravelPoint = (idx, delta) => {
  const list = travelRouteMarkers.value.map((m) => String(m.entityId));
  const target = idx + delta;
  if (target < 0 || target >= list.length) return;
  const tmp = list[idx];
  list[idx] = list[target];
  list[target] = tmp;
  travelOrderIds.value = list;
  saveTravelOrder();
  updatePolylines();
};

const removeTravelPoint = (idx) => {
  const list = travelRouteMarkers.value.map((m) => String(m.entityId));
  list.splice(idx, 1);
  travelOrderIds.value = list;
  saveTravelOrder();
  updatePolylines();
};

const setupMarkerDragEvents = () => {
  if (!markerLayer.value) return;
  markerLayer.value.on("dragend", (evt) => {
    const geometry = evt.geometry;
    if (!geometry) return;
    const entityId = geometry.id;
    const newLat = geometry.position?.getLat?.() ?? geometry.position?.lat;
    const newLng = geometry.position?.getLng?.() ?? geometry.position?.lng;
    if (!Number.isFinite(newLat) || !Number.isFinite(newLng)) return;

    const labels = labelLayer.value.getGeometries();
    const labelIdx = labels.findIndex((l) => l.id === `lbl-${entityId}`);
    if (labelIdx >= 0) {
      labels[labelIdx].position = toTMapLatLng(newLat, newLng);
      labelLayer.value.setGeometries(labels);
    }

    updateLocatedMarkersList();
    updatePolylines();

    emit("marker-updated", {
      entityId: Number(entityId) || entityId,
      lat: newLat,
      lng: newLng,
      source: "manual"
    });

    ElMessage.success("标记位置已更新");
  });
};

const handleMapClick = (evt) => {
  if (!selectedEntityId.value || !evt?.latLng) return;
  const target = selectableEntities.value.find(
    (e) => (e.id || e.label) === selectedEntityId.value
  );
  if (!target) return;
  addMarkerWithDrag({
    entityId: target.id || target.label,
    label: target.label || target.name || target.id,
    lat: evt.latLng.getLat(),
    lng: evt.latLng.getLng(),
    source: "manual",
    category: target.category
  });
  updatePolylines();
  focusToMarkers();
  selectedEntityId.value = null;
  ElMessage.success(`已放置标注：${target.label || target.name || target.id}`);
};

const addMarkerWithDrag = ({ entityId, label, lat, lng, source, category }) => {
  if (!markerLayer.value || !labelLayer.value || !window.TMap) {
    console.warn("[MapView] addMarkerWithDrag: layer not ready", { markerLayer: !!markerLayer.value, labelLayer: !!labelLayer.value, TMap: !!window.TMap });
    return;
  }
  const id = String(entityId);
  const position = toTMapLatLng(lat, lng);

  const entity = selectableEntities.value.find((e) => e.id === entityId || e.id === Number(entityId));
  const entityCategory = category || entity?.category || "LOCATION";
  const styleId = getMarkerStyleId(source);

  console.log("[MapView] addMarkerWithDrag:", { id, label, lat, lng, source, styleId, entityCategory });
  console.log("[MapView] position object:", position);

  const geos = markerLayer.value.getGeometries().filter((g) => g.id !== id);
  const newGeo = {
    id,
    styleId,
    position,
    properties: { label, source, category: entityCategory, lat, lng }
  };
  geos.push(newGeo);
  console.log("[MapView] About to setGeometries with", geos.length, "markers");
  console.log("[MapView] Geometry objects:", JSON.stringify(geos.map(g => ({
    id: g.id,
    styleId: g.styleId,
    lat: g.position?.getLat?.() ?? g.position?.lat,
    lng: g.position?.getLng?.() ?? g.position?.lng,
    properties: g.properties
  }))));

  markerLayer.value.setGeometries(geos);

  console.log("[MapView] After setGeometries, markerLayer.getGeometries():", markerLayer.value.getGeometries().length);
  console.log("[MapView] MarkerLayer visible:", markerLayer.value.getVisible());
  console.log("[MapView] Map zoom:", map.value.getZoom(), "center:", map.value.getCenter().toString());

  const labels = labelLayer.value.getGeometries().filter((g) => g.id !== `lbl-${id}`);
  labels.push({
    id: `lbl-${id}`,
    styleId: "default",
    position,
    content: label || id
  });
  labelLayer.value.setGeometries(labels);
  updateLocatedMarkersList();
  console.log("[MapView] Marker added successfully, total markers:", markerLayer.value.getGeometries().length);
};

const handleAutoLocate = async () => {
  try {
    if (!store.selectedTextId) {
      ElMessage.warning("请先选择一篇文本");
      console.warn("[MapView] handleAutoLocate: No text selected");
      return;
    }
    // 自动标注：对所有实体都尝试映射到一个现代可搜索地点（不限制为 LOCATION）
    const sourceList = allEntityList.value;
    const entities = (sourceList || []).map((e) => ({
      id: e.id,
      label: e.label || e.name,
      category: e.category
    }));
    console.log("[MapView] handleAutoLocate: entities to locate:", entities);
    if (!entities.length) {
      ElMessage.warning("没有实体可标注");
      return;
    }
    locating.value = true;
    console.log("[MapView] Calling locateEntities API...");
    const { data } = await locateEntities({
      textId: store.selectedTextId,
      model: selectedModel.value,
      entities
    });
    console.log("[MapView] locateEntities response:", data);
    llmPoints.value = Array.isArray(data) ? data : data ? [data] : [];
    console.log("[MapView] llmPoints after processing:", llmPoints.value);
    // watch 会自动调用 updateMarkersWithDrag 和 updatePolylines，所以这里不需要手动调用
    // updateMarkersWithDrag();
    // updatePolylines();
    if (!llmPoints.value.length) {
      ElMessage.warning("模型未返回坐标");
    } else {
      ElMessage.success(`自动标注完成，共 ${llmPoints.value.length} 个地点`);
    }
  } catch (err) {
    console.error("[MapView] handleAutoLocate error:", err);
    ElMessage.error("自动标注失败: " + (err.message || err));
  } finally {
    locating.value = false;
  }
};

const updateMarkersWithDrag = () => {
  if (!map.value || !markerLayer.value) return;
  console.log("[MapView] updateMarkersWithDrag: clearing and re-adding markers...");
  clearMarkers();

  const allMarkers = [];

  (props.points || []).forEach((p) => {
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng;
    if (Number.isFinite(Number(lat)) && Number.isFinite(Number(lng))) {
      allMarkers.push({
        entityId: p.entityId || p.id || p.label,
        label: p.label || p.name || p.id,
        lat: Number(lat),
        lng: Number(lng),
        source: "llm",
        category: p.category
      });
    }
  });

  (llmPoints.value || []).forEach((p) => {
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng;
    if (Number.isFinite(Number(lat)) && Number.isFinite(Number(lng))) {
      allMarkers.push({
        entityId: p.entityId || p.id || p.label,
        label: p.label || p.name || p.id,
        lat: Number(lat),
        lng: Number(lng),
        source: "llm",
        category: p.category
      });
    }
  });

  console.log("[MapView] Total markers to add:", allMarkers.length);

  // 批量添加所有标记
  const geometries = [];
  const labels = [];

  allMarkers.forEach(marker => {
    const id = String(marker.entityId);
    const position = toTMapLatLng(marker.lat, marker.lng);
    const styleId = getMarkerStyleId(marker.source);

    geometries.push({
      id,
      styleId,
      position,
      properties: {
        label: marker.label,
        source: marker.source,
        category: marker.category || "LOCATION",
        lat: marker.lat,
        lng: marker.lng
      }
    });

    labels.push({
      id: `lbl-${id}`,
      styleId: "default",
      position,
      content: marker.label || id
    });
  });

  console.log("[MapView] Setting", geometries.length, "geometries at once");
  markerLayer.value.setGeometries(geometries);
  labelLayer.value.setGeometries(labels);

  console.log("[MapView] After setGeometries, markerLayer has", markerLayer.value.getGeometries().length, "markers");

  updateLocatedMarkersList();

  // 直接传递标记数据给 focusToMarkers，不依赖图层获取
  if (allMarkers.length > 0) {
    console.log("[MapView] Focusing to", allMarkers.length, "markers...");
    focusToMarkers(allMarkers);
  }
};

const selectEntity = (entity) => {
  selectedEntityId.value = entity.id || entity.label;
};

const onEntityDragStart = (evt, entity) => {
  draggingEntityId.value = entity.id || entity.label;
  evt.dataTransfer.setData("application/json", JSON.stringify(entity));
  evt.dataTransfer.effectAllowed = "copy";
};

const onEntityDragEnd = () => {
  draggingEntityId.value = null;
};

const onMapDragOver = (evt) => {
  evt.preventDefault();
  evt.dataTransfer.dropEffect = "copy";
};

const onMapDrop = (evt) => {
  evt.preventDefault();
  try {
    const entityData = JSON.parse(evt.dataTransfer.getData("application/json"));
    if (!entityData || !map.value) return;
    const rect = mapEl.value.getBoundingClientRect();
    const x = evt.clientX - rect.left;
    const y = evt.clientY - rect.top;
    const containerPoint = new window.TMap.Point(x, y);
    const latLng = map.value.unprojectFromContainer(containerPoint);
    if (!latLng) return;
    addMarkerWithDrag({
      entityId: entityData.id || entityData.label,
      label: entityData.label || entityData.name,
      lat: latLng.getLat(),
      lng: latLng.getLng(),
      source: "manual",
      category: entityData.category
    });
    updatePolylines();
    ElMessage.success(`已放置：${entityData.label || entityData.name}`);
  } catch (e) {
    console.error("Drop failed:", e);
  }
  draggingEntityId.value = null;
};

const focusMarker = (marker) => {
  const lat = Number(marker?.lat);
  const lng = Number(marker?.lng);
  if (!map.value || !Number.isFinite(lat) || !Number.isFinite(lng)) return;
  map.value.setCenter(new window.TMap.LatLng(lat, lng));
  map.value.setZoom(8);
};

const removeMarker = (marker) => {
  if (!markerLayer.value || !labelLayer.value) return;
  const id = String(marker.entityId);
  const geos = markerLayer.value.getGeometries().filter((g) => g.id !== id);
  markerLayer.value.setGeometries(geos);
  const labels = labelLayer.value.getGeometries().filter((g) => g.id !== `lbl-${id}`);
  labelLayer.value.setGeometries(labels);
  updateLocatedMarkersList();
  manualRoutes.value = (manualRoutes.value || []).filter((r) => String(r.fromId) !== id && String(r.toId) !== id);
  saveManualRoutes();
  updatePolylines();
  emit("marker-removed", { entityId: marker.entityId });
  ElMessage.success("已移除标注");
};

let updateTimer = null;
watch(() => [props.points, llmPoints.value], () => {
  // 防抖：避免频繁更新
  if (updateTimer) clearTimeout(updateTimer);
  updateTimer = setTimeout(() => {
    nextTick(() => {
      updateMarkersWithDrag();
      updatePolylines();
    });
  }, 100);
}, { deep: true });

watch(() => showTravelPath.value, () => updatePolylines());
watch(() => showNonLocationEntities.value, () => updateMarkersWithDrag());
watch(() => locatedMarkers.value, () => {
  const candidateIds = new Set(getTravelCandidates().map((m) => String(m.entityId)));
  if (!travelOrderIds.value.length && candidateIds.size) {
    travelOrderIds.value = Array.from(candidateIds);
    saveTravelOrder();
  } else if (travelOrderIds.value.length) {
    const next = travelOrderIds.value.filter((id) => candidateIds.has(String(id)));
    if (next.length !== travelOrderIds.value.length) {
      travelOrderIds.value = next;
      saveTravelOrder();
    }
  }
  updatePolylines();
}, { deep: true });
watch(() => store.selectedTextId, () => {
  loadManualRoutes();
  loadTravelOrder();
  routeFromId.value = null;
  routeToId.value = null;
  nextTick(() => updatePolylines());
}, { immediate: true });
watch(() => manualRoutes.value, () => saveManualRoutes(), { deep: true });

onMounted(async () => {
  await nextTick();
  await initMap();
});

onBeforeUnmount(() => {
  clearMarkers();
  if (polylineLayer.value) polylineLayer.value.setGeometries([]);
  map.value = null;
});
</script>

<style scoped>
.map-wrapper { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.map-header { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 12px; }
.section-title { margin: 0; font-size: 16px; font-weight: 600; }
.map-content { display: flex; gap: 16px; height: 650px; }
.entity-sidebar, .located-sidebar { width: 180px; background: #f9fafb; border-radius: 8px; display: flex; flex-direction: column; overflow: hidden; }
.sidebar-header { padding: 10px; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.sidebar-title { font-weight: 600; font-size: 13px; }
.entity-list, .located-list { flex: 1; overflow-y: auto; padding: 8px; }
.entity-card { display: flex; align-items: center; gap: 8px; padding: 8px 10px; margin-bottom: 6px; background: #fff; border-radius: 6px; cursor: grab; border: 2px solid transparent; transition: all 0.2s; }
.entity-card:hover { border-color: #409eff; }
.entity-card.is-located { opacity: 0.6; cursor: default; }
.entity-card.is-selected { border-color: #67c23a; background: #f0f9eb; }
.entity-card.is-dragging { opacity: 0.5; }
.entity-icon { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.entity-name { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.located-badge { font-size: 12px; color: #67c23a; }
.located-card { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; margin-bottom: 6px; background: #fff; border-radius: 6px; cursor: pointer; }
.located-card:hover { background: #f0f9ff; }
.located-card.is-manual { border-left: 3px solid #f97316; }
.located-info { display: flex; align-items: center; gap: 8px; flex: 1; overflow: hidden; }
.located-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.located-actions { display: flex; gap: 4px; }
.map-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.map-canvas { flex: 1; border-radius: 10px; overflow: hidden; background: #f5f7fa; position: relative; min-height: 500px; }
.placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #999; }
.map-footer { padding: 8px 0; display: flex; justify-content: space-between; align-items: center; }
.legend { display: flex; gap: 16px; font-size: 12px; color: #666; }
.legend-item { display: flex; align-items: center; gap: 4px; }
.marker-dot { width: 10px; height: 10px; border-radius: 50%; }
.marker-dot.llm { background: #3b82f6; }
.marker-dot.manual { background: #f97316; }
.tip { font-size: 12px; color: #409eff; }
.empty-tip { text-align: center; color: #999; font-size: 12px; padding: 20px; }
.route-editor { display: flex; flex-direction: column; gap: 14px; }
.route-section { border: 1px solid #e5e7eb; border-radius: 10px; padding: 10px; background: #fff; }
.route-section-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.route-section-title { font-weight: 700; font-size: 13px; color: #111827; }
.route-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.route-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px; border-radius: 8px; background: #fff7ed; margin-bottom: 8px; }
.route-row-name { font-size: 13px; color: #9a3412; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.route-point-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px; border-radius: 8px; background: #f9fafb; margin-bottom: 8px; }
.route-point-label { font-size: 13px; color: #111827; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.route-point-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.route-hint { font-size: 12px; color: #6b7280; margin-top: 6px; }
</style>
