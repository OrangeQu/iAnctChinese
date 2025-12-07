<template>
  <div class="dashboard-shell" v-loading="store.loading">
    <div class="stage-actions">
      <div class="user-menu">
        <el-dropdown>
          <span class="user-info">
            {{ authStore.user?.username || "用户" }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 阶段导航 -->
    <div class="stage-nav">
      <button
        v-for="option in stageOptions"
        :key="option.value"
        :class="['stage-btn', { active: stage === option.value }]"
        @click="stage = option.value"
      >
        <span class="icon">{{ option.icon }}</span>
        <span>{{ option.label }}</span>
      </button>
    </div>

    <!-- 1. 结构化阶段 -->
    <div v-if="stage === 'structure'" class="stage-content">
      <TextWorkspace />
    </div>

    <!-- 2. 统计分析阶段 -->
    <div v-else-if="stage === 'analysis'" class="analysis-stage">
      <ClassificationBanner
        :current-category="store.selectedText?.category || ''"
        :classification="store.classification"
        :loading="store.loading"
        @classify="store.classifySelectedText"
        @auto-annotate="store.triggerAutoAnnotation"
        @update-category="store.updateSelectedCategory"
      />
      <div class="analysis-body">
        <aside class="panel insight-panel">
          <h3 class="section-title">实体列表</h3>
          <ul class="chip-list">
            <li v-for="entity in store.entities" :key="entity.id">
              <span>{{ entity.label }}</span>
              <small>{{ translateEntity(entity.category) }}</small>
            </li>
          </ul>
        </aside>
        <StatsPanel
          class="analysis-panel"
          :words="insights?.wordCloud || []"
          :stats="insights?.stats || {}"
          :analysis-summary="insights?.analysisSummary || ''"
        />
      </div>
    </div>

    <!-- 3. 知识图谱与可视化阶段 -->
    <div v-else class="graph-stage">
      <div class="graph-grid">
        <section class="panel left-panel">
          <div class="property-block">
            <h3 class="section-title">属性面板</h3>
            <div class="property">
              <span>标题</span>
              <strong>{{ store.selectedText?.title || "请选择文献" }}</strong>
            </div>
            <div class="property">
              <span>类型</span>
              <strong>{{ labelMap[store.selectedText?.category] || "待识别" }}</strong>
            </div>
            <el-divider />
            <div class="stats-block" v-if="insights">
              <h4 class="sub-title">统计信息</h4>
              <ul class="stat-list">
                <li>实体数：{{ insights.stats?.entityCount || 0 }}</li>
                <li>关系数：{{ insights.stats?.relationCount || 0 }}</li>
              </ul>
            </div>
          </div>
          <el-divider />
          <FilterPanel
            :filters="store.filters"
            :show-relation-filters="viewType !== 'historyMap'"
            :entity-options="store.entityOptions"
            :relation-options="store.relationOptions"
            @update:filters="handleFilterChange"
          />
        </section>
        
        <!-- 中间主要视图 -->
        <section class="panel view-panel">
          <div class="view-toggle">
            <el-radio-group v-model="viewType">
              <el-radio-button
                v-for="option in viewOptions"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-radio-button>
            </el-radio-group>
            
            <div class="recommended" v-if="insights?.recommendedViews?.length">
              <span>推荐视图：</span>
              <el-tag v-for="view in insights.recommendedViews" :key="view" size="small">
                {{ view }}
              </el-tag>
            </div>
          </div>
          
          <!-- 动态组件渲染 -->
          <component 
            :is="currentComponent" 
            v-bind="viewProps" 
            :key="store.selectedTextId + '-' + viewType"
            ref="viewComponentRef"
          />
        </section>
        
        <!-- 右侧实体面板 -->
        <section class="panel right-panel">
          <h3 class="section-title">可用实体</h3>
          <div v-if="availableEntitiesPanel.length === 0" class="empty-tip">暂无实体</div>
          <div
            v-for="entity in availableEntitiesPanel"
            :key="entity.id"
            class="entity-item"
            :class="{ disabled: isEntityMapped(entity.id) }"
            :draggable="!isEntityMapped(entity.id)"
            @dragstart="(evt) => startEntityDrag(evt, entity)"
          >
            <div class="entity-meta">
              <span class="dot" :style="{ background: entityColor(entity.category) }"></span>
              <span class="name">{{ entity.label || entity.name }}</span>
            </div>
            <span v-if="isEntityMapped(entity.id)" class="status-tag">已标注</span>
          </div>
        </section>
      </div>
    </div>
    
    <!-- 搜索弹窗 -->
    <el-dialog v-model="searchDialogVisible" title="搜索结果" width="520px">
      <el-table :data="store.searchResults" v-loading="store.searchLoading" size="small">
        <el-table-column prop="title" label="标题" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" type="primary" @click="selectFromSearch(scope.row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useTextStore } from "@/store/textStore";
import { useAuthStore } from "@/store/authStore";
import { ArrowDown } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";

// 引入组件
import FilterPanel from "@/components/filters/FilterPanel.vue";
import GraphView from "@/components/visualizations/GraphView.vue";
import TimelineView from "@/components/visualizations/TimelineView.vue";
import MapView from "@/components/visualizations/MapView.vue";
import StatsPanel from "@/components/visualizations/StatsPanel.vue";
import ClassificationBanner from "@/components/layout/ClassificationBanner.vue";
import FamilyTreeView from "@/components/visualizations/FamilyTreeView.vue";
import BattleTimelineView from "@/components/visualizations/BattleTimelineView.vue";
import TextWorkspace from "./TextWorkspace.vue";
import WordCloudCanvas from "@/components/visualizations/WordCloudCanvas.vue";
import HistoryMap from "@/components/visualizations/MapView.vue";

const router = useRouter();
const route = useRoute();
const store = useTextStore();
const authStore = useAuthStore();

const STORAGE_STAGE_KEY = "dashboard-stage";
const STORAGE_VIEW_KEY = "dashboard-view";
const STORAGE_VIEW_PER_TEXT_KEY = "dashboard-view-per-text";

const stage = ref(localStorage.getItem(STORAGE_STAGE_KEY) || "structure");
const viewType = ref(localStorage.getItem(STORAGE_VIEW_KEY) || "graph");
const searchDialogVisible = ref(false);
const viewComponentRef = ref(null);

const handleLogout = () => {
  authStore.logout();
  router.push("/");
};

const stageOptions = [
  { value: "structure", label: "结构标注", icon: "S" },
  { value: "analysis", label: "词云统计", icon: "A" },
  { value: "graph", label: "知识图谱", icon: "G" }
];

const labelMap = {
  warfare: "战争纪实",
  travelogue: "游记地理",
  biography: "人物传记",
  unknown: "待识别"
};

onMounted(async () => {
  if (!store.texts.length || !store.navigationTree) {
    await store.initDashboard();
  }
  if (!store.selectedTextId && store.texts.length) {
    await store.selectText(store.texts[0].id);
  }
  if (route.params.id) {
    await store.selectText(route.params.id);
  }
});

watch(() => route.params.id, async (id) => {
  if (id) await store.selectText(id);
});

// 选中文本后，确保当前视图合法且不抢占已有选择
watch(() => store.selectedText?.id, async (newId) => {
  if (newId) {
    await nextTick();
    const opts = viewOptions.value;
    const perTextViews = JSON.parse(localStorage.getItem(STORAGE_VIEW_PER_TEXT_KEY) || "{}");
    const savedForText = perTextViews[String(newId)];
    if (savedForText && opts.some((o) => o.value === savedForText)) {
      viewType.value = savedForText;
      return;
    }
    if (opts.length && !opts.some((o) => o.value === viewType.value)) {
      viewType.value = opts[0].value;
    }
  }
});

const viewPresets = {
  travelogue: [
    { value: "map", label: "地图轨迹" },
    { value: "timeline", label: "行程时间轴" },
    { value: "graph", label: "知识图谱" }
  ],
  warfare: [
    { value: "historyMap", label: "战争地图" }, 
    { value: "graph", label: "知识图谱" }
  ],
  biography: [
    { value: "family", label: "亲情图" },
    { value: "timeline", label: "生平时间轴" },
    { value: "graph", label: "知识图谱" }
  ],
  default: [
    { value: "graph", label: "知识图谱" },
    { value: "timeline", label: "时间轴" },
    { value: "map", label: "地图" }
  ]
};

const viewOptions = computed(() => {
  const category = store.selectedText?.category;
  return (category && viewPresets[category]) ? viewPresets[category] : viewPresets.default;
});

// 如果存储的视图不在当前列表中，回退到第一个
watch(viewOptions, (opts) => {
  if (!opts.length) return;
  if (!opts.some((o) => o.value === viewType.value)) {
    viewType.value = opts[0].value;
  }
}, { immediate: true });

const componentMap = {
  graph: GraphView,
  timeline: TimelineView,
  map: MapView,
  historyMap: HistoryMap,
  family: FamilyTreeView,
  battle: BattleTimelineView,
  cloud: WordCloudCanvas
};

const currentComponent = computed(() => componentMap[viewType.value] || GraphView);
const insights = computed(() => store.insights);

const viewProps = computed(() => {
  switch (viewType.value) {
    case "historyMap":
      return { 
        locations: (store.entities || []).filter((e) => e.category === "LOCATION"),
        points: insights.value?.mapPoints || [],
        allEntities: store.entities || []
      };
    case "cloud": return { words: insights.value?.wordCloud || [] };
    case "timeline": return {
      milestones: insights.value?.timeline || [],
      category: store.selectedText?.category || 'unknown',
      onJumpToText: handleJumpToText
    };
    case "map": {
      const locs = (store.entities || []).filter((e) => e.category === "LOCATION");
      return { 
        locations: locs.length ? locs : (store.entities || []), // 若无LOCATION，则回退全部实体尝试定位
        points: insights.value?.mapPoints || [],
        allEntities: store.entities || []
      };
    }
    case "battle": return { events: insights.value?.battleTimeline || [] };
    case "family": return { nodes: insights.value?.familyTree || [] };
    case "graph":
    default:
      return {
        entities: store.entities,
        relations: store.relations,
        highlightOnly: store.filters.highlightOnly,
        activeEntityCategories: store.filters.entityCategories,
        activeRelationTypes: store.filters.relationTypes
      };
  }
});

const handleJumpToText = (data) => {
  // 跳转到结构化阶段并高亮显示原文位置
  stage.value = 'structure';
  nextTick(() => {
    // 触发高亮逻辑（如果需要的话）
    console.log('跳转到原文:', data);
    ElMessage.success('已跳转到原文位置');
  });
};

const handleFilterChange = (filters) => {
  store.setEntityFilters(filters.entityCategories || []);
  store.setRelationFilters(filters.relationTypes || []);
  store.setHighlightOnly(filters.highlightOnly);
};

const selectFromSearch = async (textId) => {
  searchDialogVisible.value = false;
  await store.selectText(textId);
};

const translateEntity = (cat) => cat === "PERSON" ? "人物" : cat;

// 刷新后保持在当前阶段/视图
watch(stage, (val) => localStorage.setItem(STORAGE_STAGE_KEY, val));
watch(viewType, (val) => {
  localStorage.setItem(STORAGE_VIEW_KEY, val);
  if (store.selectedTextId) {
    const perTextViews = JSON.parse(localStorage.getItem(STORAGE_VIEW_PER_TEXT_KEY) || "{}");
    perTextViews[String(store.selectedTextId)] = val;
    localStorage.setItem(STORAGE_VIEW_PER_TEXT_KEY, JSON.stringify(perTextViews));
  }
});

// 将可用实体列表移到右侧面板
const availableEntitiesPanel = computed(() => {
  if (viewType.value === "historyMap") {
    return store.entities || [];
  }
  if (viewType.value === "graph") {
    return store.entities || [];
  }
  if (viewType.value === "map") {
    // 地图视图：优先地点实体，若无则展示全部，便于拖拽/确认
    const locs = (store.entities || []).filter((e) => e.category === "LOCATION");
    if (locs.length) return locs;
    return store.entities || [];
  }
  return [];
});

const isEntityMapped = (id) => {
  if (viewType.value !== "historyMap") return false;
  return false;
};

const startEntityDrag = (evt, entity) => {
  if (viewType.value !== "historyMap") return;
  // 当前 historyMap 使用腾讯地图组件，不支持拖拽
  evt.preventDefault();
};

const entityColor = (category) => {
  if (viewType.value !== "historyMap") return "#95a5a6";
  return "#95a5a6";
};
</script>

<style scoped>
.dashboard-shell { display: flex; flex-direction: column; gap: 8px; }
.stage-actions { display: flex; justify-content: flex-end; gap: 12px; margin-bottom: 4px; }
.stage-nav { display: flex; gap: 12px; margin-bottom: 4px; }
.stage-btn { padding: 8px 18px; border-radius: 999px; border: 1px solid var(--border); background: white; cursor: pointer; display: flex; gap: 6px; align-items: center;}
.stage-btn.active { background: #3f3d56; color: white; }
.graph-stage .graph-grid { display: grid; grid-template-columns: 300px 1fr 300px; gap: 16px; min-height: 560px; }
.panel { background: var(--panel); border: 1px solid var(--border); border-radius: 16px; padding: 16px; }
.view-panel { min-height: 520px; }
.left-panel { display: flex; flex-direction: column; gap: 14px; padding: 0; border: none; background: transparent; box-shadow: none; }
.property-block { background: linear-gradient(180deg, #fffaf1 0%, #fff 80%); border: 1px solid #eadfce; border-radius: 16px; padding: 14px 16px; box-shadow: 0 8px 22px rgba(90, 67, 40, 0.08); }
.left-panel :deep(.filter-panel) { border: 1px solid #eadfce; background: linear-gradient(180deg, #fffaf1 0%, #fff 80%); box-shadow: 0 8px 22px rgba(90, 67, 40, 0.08); }
.view-toggle { display: flex; justify-content: space-between; margin-bottom: 12px; }
.analysis-stage .analysis-body { display: grid; grid-template-columns: 260px 1fr; gap: 16px; }
.chip-list { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.chip-list li { display: flex; justify-content: space-between; background: rgba(247,244,236,0.9); padding: 6px 12px; border-radius: 999px; font-size: 13px; }
.right-panel { display: flex; flex-direction: column; gap: 12px; }
.property { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; }
.property span { color: var(--muted); }
.stats-block { margin-top: 4px; }
.sub-title { margin: 0 0 8px; font-size: 14px; color: #8c7a6b; }
.stat-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; color: #4a443e; }
.entity-panel { display: flex; flex-direction: column; gap: 8px; }
.entity-item { border: 1px solid var(--border); border-radius: 10px; padding: 8px 10px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; cursor: grab; }
.entity-item.disabled { background: #f5f5f5; color: #999; cursor: not-allowed; }
.entity-meta { display: flex; align-items: center; gap: 8px; }
.dot { width: 10px; height: 10px; border-radius: 50%; }
.empty-tip { color: var(--muted); }
.status-tag { font-size: 12px; color: #67c23a; background: #e1f3d8; padding: 2px 6px; border-radius: 6px; }
</style>
