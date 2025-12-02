<template>
  <section class="panel filter-panel">
    <div class="filter-header">
      <h3 class="section-title">过滤器</h3>
      <p class="hint">
        {{ showRelationFilters ? "勾选想要展示的实体与关系" : "勾选想要展示的实体" }}
      </p>
    </div>

    <div class="filter-block">
      <strong>实体类别</strong>
      <el-checkbox-group
        v-model="localFilters.entityCategories"
        @change="emitFilters"
      >
        <el-checkbox v-for="option in entityOptions" :key="option" :label="option">
          {{ translateEntity(option) }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <div v-if="showRelationFilters" class="filter-block">
      <strong>关系类型</strong>
      <el-checkbox-group
        v-model="localFilters.relationTypes"
        @change="emitFilters"
      >
        <el-checkbox v-for="option in relationOptions" :key="option" :label="option">
          {{ translateRelation(option) }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <el-switch
      v-if="showRelationFilters"
      v-model="localFilters.highlightOnly"
      inline-prompt
      active-text="只显示高亮实体"
      inactive-text="显示全部"
      @change="emitFilters"
    />

    <div class="filter-block">
      <strong>图例</strong>
      <div class="legend">
        <div v-for="item in legendItems" :key="item.label" class="legend-item">
          <span class="legend-dot" :style="{ background: item.color }"></span>
          <span>{{ item.label }}</span>
        </div>
      </div>
    </div>

  </section>
</template>

<script setup>
import { reactive, watch } from "vue";

const props = defineProps({
  filters: {
    type: Object,
    default: () => ({
      entityCategories: [],
      relationTypes: [],
      highlightOnly: false
    })
  },
  showRelationFilters: {
    type: Boolean,
    default: true
  },
  entityOptions: {
    type: Array,
    default: () => []
  },
  relationOptions: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["update:filters"]);

const localFilters = reactive({
  entityCategories: [],
  relationTypes: [],
  highlightOnly: false
});

watch(
  () => props.filters,
  (next) => {
    localFilters.entityCategories = [...(next.entityCategories || [])];
    localFilters.relationTypes = [...(next.relationTypes || [])];
    localFilters.highlightOnly = next.highlightOnly;
  },
  { immediate: true, deep: true }
);

const emitFilters = () => {
  emit("update:filters", { ...localFilters });
};

const labelMap = {
  warfare: "战争纪实",
  travelogue: "游记地理",
  biography: "人物传记"
};

const entityMap = {
  PERSON: "人物",
  LOCATION: "地点",
  EVENT: "事件",
  ORGANIZATION: "组织",
  OBJECT: "器物",
  CUSTOM: "自定义"
};

const relationMap = {
  CONFLICT: "对抗",
  SUPPORT: "结盟",
  TRAVEL: "行旅",
  FAMILY: "亲属",
  TEMPORAL: "时间",
  CUSTOM: "自定义"
};

const translateEntity = (key) => entityMap[key] || key;
const translateRelation = (key) => relationMap[key] || key;

// 与地图标注颜色保持一致
const legendItems = [
  { label: "人物", color: "#e74c3c" },
  { label: "地点", color: "#2ecc71" },
  { label: "事件", color: "#f1c40f" }
];
</script>

<style scoped>
.filter-block {
  margin-bottom: 12px;
}

.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #eadfce;
  background: linear-gradient(180deg, #fffaf1 0%, #fff 80%);
  box-shadow: 0 8px 22px rgba(90, 67, 40, 0.08);
  border-radius: 16px;
}

.legend {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.stats-block {
  padding-top: 6px;
  border-top: 1px solid var(--border);
}

.filter-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hint {
  margin: 0;
  color: #8c7a6b;
  font-size: 12px;
}

.sub-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: #8c7a6b;
}

.stat-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #4a443e;
}
</style>
