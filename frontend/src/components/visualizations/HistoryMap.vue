<template>
  <div class="history-map-container" v-loading="loading">
    <!-- 顶部工具栏 -->
    <div class="map-toolbar">
      <div class="selector-group">
        <label>选择时期：</label>
        <!-- 下拉框依然使用 relevantMaps，保证智能推荐功能正常 -->
        <select v-model="selectedMapId" @change="changeMap" class="ancient-select">
          <option v-for="map in relevantMaps" :key="map.id" :value="map.id">
            {{ map.dynasty }} ({{ map.startYear }} ~ {{ map.endYear }})
            <span v-if="map.isRecommended">✨推荐</span>
          </option>
        </select>
        
        <button 
          v-if="relevantMaps.length < mapList.length" 
          class="show-all-btn" 
          @click="showAllMaps = !showAllMaps"
        >
          {{ showAllMaps ? '只看相关' : '查看全部' }}
        </button>
      </div>

      <div class="toolbar-actions">
        <button class="auto-btn" :disabled="autoPlacing" @click="autoPlaceMarkers">
          🤖 自动标注
        </button>
        <button class="clear-btn" @click="clearAllMarkers" title="清空当前地图所有标注">
          🗑️ 清空标注
        </button>
      </div>
    </div>

    <div :class="['map-workspace', { 'no-sidebar': hideSidebar }]">
      <!-- 左侧：待标注实体列表（可隐藏） -->
      <div v-if="!hideSidebar" class="entity-sidebar">
        <h4>可用实体</h4>
        <div v-if="sidebarEntities.length === 0" class="empty-tip">暂无实体</div>
        <div 
          v-for="entity in sidebarEntities" 
          :key="entity.id"
          class="entity-card"
          :class="{ 'disabled': isEntityMapped(entity.id) }"
          :draggable="!isEntityMapped(entity.id)"
          @dragstart="onDragStart($event, entity)"
        >
          <div class="card-content">
            <span class="entity-icon" :style="{background: getEntityColor(entity.category)}"></span>
            <span class="entity-name">{{ entity.label || entity.name }}</span>
          </div>
          <span v-if="isEntityMapped(entity.id)" class="status-tag">已标注</span>
        </div>
      </div>

      <!-- 核心地图区域 -->
      <div class="map-view-wrapper">
        <div id="leaflet-map" class="leaflet-container" @drop="onDrop" @dragover.prevent></div>
        
        <div class="timeline-overlay" v-if="currentMapInfo">
          <span class="year-label">年份: {{ currentYear }}</span>
          <input 
            type="range" 
            class="ancient-slider"
            :min="currentMapInfo.startYear" 
            :max="currentMapInfo.endYear" 
            v-model="currentYear"
            @input="filterMarkers"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, watch, defineExpose } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { ElMessage, ElMessageBox } from 'element-plus';

const props = defineProps({
  availableEntities: {
    type: Array,
    default: () => []
  },
  activeCategories: {
    type: Array,
    default: () => []
  },
  hideSidebar: {
    type: Boolean,
    default: false
  }
});

const loading = ref(false);
const autoPlacing = ref(false);
const mapList = ref([]);
const selectedMapId = ref(null);
const currentMapInfo = ref(null);
const currentYear = ref(0);
const markersData = ref([]); 
const showAllMaps = ref(false);

let mapInstance = null;
let markersLayer = null;

const API_BASE = 'http://localhost:8080/api/visualization';

// --- 💡 过滤逻辑：侧边栏显示的实体 ---
const sidebarEntities = computed(() => {
  return props.availableEntities.filter(e => {
    const name = e.label || e.name;
    
    // 隐藏时间/日期类型的实体
    if (e.category === 'TIME' || e.category === 'DATE') {
      return false;
    }

    return true;
  });
});

// --- 💡 智能筛选地图 ---
const relevantMaps = computed(() => {
  if (showAllMaps.value || props.availableEntities.length === 0) {
    return mapList.value;
  }
  const years = [];
  const keywords = [];

  props.availableEntities.forEach(e => {
    if (e.category === 'TIME' || e.category === 'DATE') {
      const y = extractYear(e.label || e.name);
      if (y !== null) years.push(y);
    }
    if (['ORGANIZATION', 'DYNASTY', 'EVENT'].includes(e.category)) {
      keywords.push(e.label || e.name);
    }
  });

  const filtered = mapList.value.filter(map => {
    const nameMatch = keywords.some(k => map.dynasty.includes(k) || k.includes(map.dynasty.substring(0, 1)));
    const timeMatch = years.some(y => y >= map.startYear && y <= map.endYear);
    if (nameMatch || timeMatch) {
      map.isRecommended = true;
      return true;
    }
    return false;
  });

  return filtered.length > 0 ? filtered : mapList.value;
});

watch(relevantMaps, (newMaps) => {
  if (newMaps.length > 0 && !selectedMapId.value) {
    selectedMapId.value = newMaps[0].id;
    changeMap();
  } else if (newMaps.length > 0 && selectedMapId.value) {
     const exists = newMaps.find(m => m.id === selectedMapId.value);
     if (!exists) {
        selectedMapId.value = newMaps[0].id;
        changeMap();
     }
  }
}, { immediate: true });

const extractYear = (str) => {
  if (!str) return null;
  if (str.includes("公元前") || str.includes("B.C.")) {
    const match = str.match(/(\d+)/);
    return match ? -parseInt(match[1]) : null;
  }
  const match = str.match(/(-?\d+)/);
  return match ? parseInt(match[1]) : null;
};

// --- 常规逻辑 ---

onMounted(async () => {
  try {
    const res = await fetch(`${API_BASE}/maps`);
    const data = await res.json();
    mapList.value = data;
  } catch (e) {
    console.error("加载地图列表失败", e);
  }
});

const changeMap = async () => {
  if (!selectedMapId.value) return;
  const mapData = mapList.value.find(m => m.id === selectedMapId.value);
  if (!mapData) return;

  currentMapInfo.value = mapData;
  currentYear.value = mapData.startYear;
  
  await fetchMarkers(mapData.id);

  nextTick(() => {
    initLeafletMap(mapData);
    // 解决刷新后地图偏移：强制刷新尺寸计算
    setTimeout(() => {
      if (mapInstance) {
        mapInstance.invalidateSize();
        mapInstance.fitBounds([[0, 0], [mapData.height, mapData.width]]);
      }
    }, 50);
  });
};

const fetchMarkers = async (mapId) => {
  try {
    const res = await fetch(`${API_BASE}/markers/${mapId}`);
    markersData.value = await res.json();
    filterMarkers();
  } catch (e) {
    console.error("加载标记失败", e);
  }
};

const autoPlaceMarkers = async () => {
  if (!currentMapInfo.value || autoPlacing.value) return;
  autoPlacing.value = true;
  try {
    const res = await fetch(`${API_BASE}/markers/auto`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        mapId: currentMapInfo.value.id,
        entities: props.availableEntities
      })
    });
    const data = await res.json();
    if (Array.isArray(data) && data.length) {
      markersData.value.push(...data);
      filterMarkers();
      ElMessage.success(`自动标注完成，新增 ${data.length} 条`);
    } else {
      ElMessage.info('没有可自动标注的实体');
    }
  } catch (e) {
    console.error('自动标注失败', e);
    ElMessage.error('自动标注失败');
  } finally {
    autoPlacing.value = false;
  }
};

const isEntityMapped = (entityId) => {
  return markersData.value.some(marker => marker.entityId === entityId);
};

const clearAllMarkers = async () => {
  if (!currentMapInfo.value) return;
  try {
    await ElMessageBox.confirm('确定要清空当前地图上的所有标注吗？', '警告', {
      confirmButtonText: '确定清空',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const mapId = currentMapInfo.value.id;
    const res = await fetch(`${API_BASE}/markers/map/${mapId}`, { method: 'DELETE' });
    if (res.ok) {
      markersData.value = [];
      if (markersLayer) markersLayer.clearLayers();
      ElMessage.success('已清空所有标注');
    }
  } catch (e) {}
};

const initLeafletMap = (mapData) => {
  if (mapInstance) mapInstance.remove();

  const map = L.map('leaflet-map', {
    crs: L.CRS.Simple,
    minZoom: -2,
    zoomControl: false,
    attributionControl: false
  });

  const w = mapData.width;
  const h = mapData.height;
  const bounds = [[0, 0], [h, w]]; 
  const imageUrl = `/maps/${mapData.filename}`;

  L.imageOverlay(imageUrl, bounds).addTo(map);
  L.rectangle(bounds, { color: "transparent", fillColor: "#000", fillOpacity: 0.3, interactive: false }).addTo(map);

  map.fitBounds(bounds);
  mapInstance = map;
  markersLayer = L.layerGroup().addTo(map);

  filterMarkers();
};

const onDragStart = (evt, entity) => {
  if (isEntityMapped(entity.id)) {
    evt.preventDefault();
    return;
  }
  evt.dataTransfer.setData('entityStr', JSON.stringify(entity));
  evt.dataTransfer.effectAllowed = 'copy';
};

const onDrop = async (evt) => {
  const entityStr = evt.dataTransfer.getData('entityStr');
  if (!entityStr) return;
  const entity = JSON.parse(entityStr);

  if (isEntityMapped(entity.id)) return;

  const map = mapInstance;
  const containerPoint = map.mouseEventToContainerPoint(evt);
  const layerPoint = map.containerPointToLayerPoint(containerPoint);
  const latlng = map.layerPointToLatLng(layerPoint);

  const newMarker = {
    entityId: entity.id,
    entityName: entity.label || entity.name,
    category: entity.category, 
    mapId: currentMapInfo.value.id,
    x: latlng.lng,
    y: latlng.lat,
    year: parseInt(currentYear.value),
    description: "拖拽生成"
  };

  try {
    const res = await fetch(`${API_BASE}/markers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newMarker)
    });
    const savedMarker = await res.json();
    savedMarker.category = entity.category;
    markersData.value.push(savedMarker);
    renderMarker(savedMarker);
  } catch (e) {}
};

const renderMarker = (data) => {
  const originalEntity = props.availableEntities.find(e => e.id === data.entityId);
  const category = originalEntity ? originalEntity.category : (data.category || 'default');
  const color = getEntityColor(category);

  // 如果是多边形(范围)，暂时不支持拖拽点，只显示
  if (data.polygonPoints && data.polygonPoints !== "null") {
     try {
       const points = JSON.parse(data.polygonPoints);
       const polygon = L.polygon(points, { color: color, fillColor: color, fillOpacity: 0.4, weight: 2 });
       polygon.bindPopup(`<div style="text-align:center;"><strong>${data.entityName}</strong><br/><span style="font-size:12px;color:#666">范围</span></div>`);
       polygon.addTo(markersLayer);
       return; 
     } catch(e){}
  }
  
  const html = `
    <div style="background:${color}; width:12px; height:12px; border-radius:50%; border:2px solid #fff; box-shadow:0 2px 4px rgba(0,0,0,0.5); cursor: move;"></div>
    <div style="margin-left:15px; background:rgba(255,255,255,0.85); padding:2px 6px; border-radius:4px; font-size:12px; font-weight:bold; white-space:nowrap; border:1px solid #ddd; color: #333; cursor: move;">
      ${data.entityName}
    </div>
  `;
  
  const icon = L.divIcon({ 
    className: 'custom-marker', 
    html: html, 
    iconSize: [100, 20], 
    iconAnchor: [6, 6] 
  });

  // 1. 设置 draggable: true 开启拖拽
  const marker = L.marker([data.y, data.x], { 
    icon: icon,
    draggable: true, // 允许拖拽
    autoPan: true    // 拖拽到边缘时自动移动地图
  });

  // 2. 监听拖拽结束事件
  marker.on('dragend', async (event) => {
    const newLatLng = event.target.getLatLng(); // 获取新坐标 {lat: y, lng: x}
    
    // 准备更新的数据（保留原ID，这样后端就是更新而不是新增）
    const updatedMarker = {
      ...data, 
      x: newLatLng.lng,
      y: newLatLng.lat
    };

    try {
      await fetch(`${API_BASE}/markers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedMarker)
      });
      
      data.x = newLatLng.lng;
      data.y = newLatLng.lat;
      
      ElMessage.success({ message: '位置已更新', grouping: true, duration: 1500 });
    } catch (e) {
      console.error("更新位置失败", e);
      ElMessage.error("位置保存失败");
      event.target.setLatLng([data.y, data.x]);
    }
  });

  marker.addTo(markersLayer);
};

const filterMarkers = () => {
  if (!markersLayer) return;
  markersLayer.clearLayers(); // 清除当前画布

  // 获取左侧列表里所有的实体名字（白名单）
  // Set 结构查询速度更快
  const validNames = new Set(props.availableEntities.map(e => e.label || e.name));

  const activeMarkers = markersData.value.filter(d => {
    // 条件1：时间过滤 (原有逻辑)
    if (d.year && currentYear.value && d.year > parseInt(currentYear.value)) {
      return false;
    }

    // 条件2：类别过滤 (原有逻辑)
    const categories = Array.isArray(props.activeCategories) ? props.activeCategories.filter(Boolean) : [];
    if (categories.length > 0) {
      const markerCat = getMarkerCategory(d);
      if (!markerCat || !categories.includes(markerCat)) {
        return false;
      }
    }

    // 🔥 条件3：【核心修复】内容一致性过滤
    // 只有当这个标记的名字，存在于当前左侧的实体列表中时，才显示！
    // 这样就屏蔽掉了其他文章在同一张地图上留下的“脏数据”
    if (!validNames.has(d.entityName)) {
      return false; 
    }

    return true;
  });

  activeMarkers.forEach(renderMarker);
};

const getEntityColor = (category) => {
  const colors = { LOCATION: '#2ecc71', PERSON: '#e74c3c', ORGANIZATION: '#3498db', EVENT: '#f1c40f', default: '#95a5a6' };
  return colors[category] || colors.default;
};

const getMarkerCategory = (marker) => {
  if (marker?.category) return marker.category;
  const originalEntity = props.availableEntities.find(e => e.id === marker?.entityId);
  return originalEntity?.category || null;
};

watch(
  () => props.activeCategories,
  () => filterMarkers(),
  { deep: true }
);

// 供父组件使用的暴露接口
// - sidebarEntities: 过滤后的可用实体（computed）
// - onDragStart / isEntityMapped / getEntityColor: 用于外部拖拽列表复用逻辑
// 这样可以在父级右侧面板渲染“可用实体”列表，减少地图区域的占用

defineExpose({
  sidebarEntities,
  onDragStart,
  isEntityMapped,
  getEntityColor
});
</script>

<style scoped>
.history-map-container { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 8px; overflow: hidden; }
.map-toolbar { padding: 10px 20px; background: #fdfbf7; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
.toolbar-actions { display: flex; align-items: center; gap: 12px; }
.ancient-select { padding: 5px 10px; border: 1px solid #d4c4b0; border-radius: 4px; }
.show-all-btn { margin-left: 10px; font-size: 12px; cursor: pointer; background: none; border: 1px solid #ccc; border-radius: 4px; padding: 2px 8px; color: #666; }
.show-all-btn:hover { color: #8b4513; border-color: #8b4513; }
.auto-btn { background: #fff; border: 1px solid #409eff; color: #409eff; padding: 5px 12px; border-radius: 4px; cursor: pointer; font-size: 12px; }
.auto-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.clear-btn { background: #fff; border: 1px solid #e74c3c; color: #e74c3c; padding: 5px 10px; border-radius: 4px; cursor: pointer; font-size: 12px; display: flex; align-items: center; gap: 4px; }
.clear-btn:hover { background: #e74c3c; color: #fff; }
.map-workspace { flex: 1; display: flex; position: relative; min-height: 540px; }
.map-workspace.no-sidebar .map-view-wrapper { width: 100%; }
.entity-sidebar { width: 160px; background: #f9f9f9; border-right: 1px solid #eee; padding: 10px; overflow-y: auto; }
.entity-card { background: #fff; border: 1px solid #e0e0e0; padding: 8px; margin-bottom: 8px; border-radius: 4px; cursor: grab; display: flex; justify-content: space-between; align-items: center; font-size: 13px; transition: all 0.2s; }
.entity-card.disabled { background: #f0f0f0; border-color: #ddd; color: #999; cursor: not-allowed; opacity: 0.7; }
.entity-card:not(.disabled):hover { background: #f0f8ff; border-color: #b0c4de; transform: translateX(2px); }
.card-content { display: flex; align-items: center; }
.entity-icon { width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; }
.entity-card.disabled .entity-icon { background: #ccc !important; }
.status-tag { font-size: 10px; color: #67c23a; background: #e1f3d8; padding: 1px 4px; border-radius: 2px; }
.map-view-wrapper { flex: 1; position: relative; min-height: 520px; }
#leaflet-map { width: 100%; height: 100%; background: #e6e0d4; z-index: 1; }
.timeline-overlay { position: absolute; bottom: 20px; left: 50%; transform: translateX(-50%); width: 80%; background: rgba(255, 255, 255, 0.9); padding: 10px 20px; border-radius: 20px; box-shadow: 0 4px 10px rgba(0,0,0,0.2); display: flex; align-items: center; gap: 15px; z-index: 999; }
.ancient-slider { flex: 1; accent-color: #8b4513; }
</style>
