<template>
  <section class="panel filter-panel">
    <h3 class="section-title">文献列表</h3>
    <el-tree
      v-if="treeData.length"
      :data="treeData"
      node-key="id"
      :current-node-key="currentNodeKey"
      class="nav-tree"
      default-expand-all
      highlight-current
      ref="treeRef"
      @node-click="handleNodeClick"
    />

    <h3 class="section-title">过滤器</h3>
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

    <div class="filter-block">
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
import { reactive, watch, computed, ref } from "vue";

const props = defineProps({
  navigationTree: {
    type: Object,
    default: () => ({ categories: [] })
  },
  selectedId: {
    type: Number,
    default: null
  },
  filters: {
    type: Object,
    default: () => ({
      entityCategories: [],
      relationTypes: [],
      highlightOnly: false
    })
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

const emit = defineEmits(["select:text", "update:filters"]);

const treeRef = ref();

const treeData = computed(() => {
  if (!props.navigationTree || !props.navigationTree.categories) {
    return [];
  }
  return props.navigationTree.categories.map((category) => ({
    id: `cat-${category.category}`,
    label: `${category.label} (${category.texts.length})`,
    type: "category",
    children: (category.texts || []).map((text) => ({
      id: `text-${text.id}`,
      label: text.title,
      type: "text",
      textId: text.id,
      children: (text.sections || []).map((section) => ({
        id: `section-${section.id}`,
        label: `${section.sequenceIndex}. ${section.summary || "未命名片段"}`,
        type: "section",
        textId: text.id,
        sectionId: section.id
      }))
    }))
  }));
});

const currentNodeKey = computed(() => {
  if (!props.selectedId) {
    return "";
  }
  return `text-${props.selectedId}`;
});

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

watch(
  () => props.selectedId,
  (next) => {
    if (!treeRef.value) {
      return;
    }
    const key = next ? `text-${next}` : null;
    treeRef.value.setCurrentKey(key);
  }
);

const handleNodeClick = (node) => {
  if (node.type === "text" || node.type === "section") {
    emit("select:text", Number(node.textId));
  }
};

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

const legendItems = [
  { label: "人物", color: "#d16a5d" },
  { label: "地点", color: "#3a7a87" },
  { label: "事件", color: "#a58938" }
];
</script>

<style scoped>
.nav-tree {
  max-height: 360px;
  min-height: 280px;
  overflow: auto;
  margin-bottom: 16px;
}

.filter-block {
  margin-bottom: 12px;
}

.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 120px);
  overflow: auto;
  padding-bottom: 12px;
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
</style>
