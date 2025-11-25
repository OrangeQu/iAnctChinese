<template>
  <div class="map-panel">
    <div class="map-header">
      <h3 class="section-title">游记轨迹</h3>
      <el-tag v-if="!hasToken" type="warning">未配置地图 Token，显示静态示意</el-tag>
    </div>
    <div v-if="hasToken" ref="mapContainer" class="map-canvas"></div>
    <div v-else class="placeholder">
      <p>请在 .env.local 中设置 VITE_MAPBOX_TOKEN 以启用地图。</p>
      <ul>
        <li v-for="point in points" :key="point.label">{{ point.sequence }}. {{ point.label }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import mapboxgl from "mapbox-gl";
import { onMounted, onUnmounted, ref, computed, watch } from "vue";

const props = defineProps({
  points: {
    type: Array,
    default: () => []
  }
});

const mapContainer = ref();
let map;
const hasToken = computed(() => !!import.meta.env.VITE_MAPBOX_TOKEN);

onMounted(() => {
  if (!hasToken.value) {
    return;
  }
  mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN;
  map = new mapboxgl.Map({
    container: mapContainer.value,
    style: "mapbox://styles/mapbox/light-v11",
    center: [108.9, 34.3],
    zoom: 3.5
  });
  map.on("load", () => {
    map.addSource("travel-route", {
      type: "geojson",
      data: buildGeoJson(props.points)
    });
    map.addLayer({
      id: "travel-route-line",
      type: "line",
      source: "travel-route",
      paint: {
        "line-color": "#c26c3d",
        "line-width": 4
      }
    });
  });
});

const buildGeoJson = (points) => ({
  type: "FeatureCollection",
  features: [
    {
      type: "Feature",
      geometry: {
        type: "LineString",
        coordinates: points.map((point) => [point.longitude, point.latitude])
      },
      properties: {}
    }
  ]
});

watch(
  () => props.points,
  (points) => {
    if (map && hasToken.value && map.getSource("travel-route")) {
      map.getSource("travel-route").setData(buildGeoJson(points));
    }
  },
  { deep: true }
);

onUnmounted(() => {
  if (map) {
    map.remove();
  }
});
</script>

<style scoped>
.map-panel {
  margin-top: 16px;
}

.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.map-canvas {
  width: 100%;
  height: 260px;
  border-radius: 12px;
  overflow: hidden;
}

.placeholder {
  padding: 12px;
  border: 1px dashed var(--border);
  border-radius: 8px;
  color: var(--muted);
}
</style>
