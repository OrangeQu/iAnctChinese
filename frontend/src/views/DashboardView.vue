<template>
  <div class="dashboard-shell" v-loading="store.loading">
    <div class="stage-actions">
      <TextUploadDrawer />
      <el-button @click="store.initDashboard()">刷新数据</el-button>
      <div class="user-menu">
        <el-dropdown>
          <span class="user-info">
            {{ authStore.user?.username || '用户' }}
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

    <div v-if="stage === 'structure'" class="stage-content">
      <TextWorkspace />
    </div>

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
          <h3 class="section-title">实体集</h3>
          <ul class="chip-list">
            <li v-for="entity in store.entities" :key="entity.id">
              <span>{{ entity.label }}</span>
              <small>{{ translateEntity(entity.category) }}</small>
            </li>
          </ul>
          <el-divider />
          <h3 class="section-title">关系集</h3>
          <ul class="chip-list">
            <li v-for="relation in store.relations" :key="relation.id">
              <span>{{ relation.source?.label }} → {{ relation.target?.label }}</span>
              <small>{{ translateRelation(relation.relationType) }}</small>
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

    <div v-else class="graph-stage">
      <div class="graph-grid">
        <FilterPanel
          :navigation-tree="store.navigationTree"
          :selected-id="store.selectedTextId"
          :filters="store.filters"
          :entity-options="store.entityOptions"
          :relation-options="store.relationOptions"
          @select:text="store.selectText"
          @update:filters="handleFilterChange"
        />
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
          <component :is="currentComponent" v-bind="viewProps" />
        </section>
        <section class="panel right-panel">
          <h3 class="section-title">属性面板</h3>
          <div class="property">
            <span>标题</span>
            <strong>{{ store.selectedText?.title || "请选择文献" }}</strong>
          </div>
          <div class="property">
            <span>作者</span>
            <strong>{{ store.selectedText?.author || "-" }}</strong>
          </div>
          <div class="property">
            <span>时代</span>
            <strong>{{ store.selectedText?.era || "-" }}</strong>
          </div>
          <div class="property">
            <span>类型</span>
            <strong>{{ labelMap[store.selectedText?.category] || "待识别" }}</strong>
          </div>
          <el-divider />
          <h3 class="section-title">统计信息</h3>
          <ul class="stat-list" v-if="insights">
            <li>实体数：{{ insights.stats?.entityCount || 0 }}</li>
            <li>关系数：{{ insights.stats?.relationCount || 0 }}</li>
            <li>句读完成度：{{ Math.round((insights.stats?.punctuationProgress || 0) * 100) }}%</li>
          </ul>
          <el-divider />
          <h3 class="section-title">分析结果</h3>
          <p class="analysis">{{ insights?.analysisSummary }}</p>
        </section>
      </div>
    </div>
  </div>

  <el-dialog v-model="searchDialogVisible" title="搜索结果" width="520px">
    <el-table :data="store.searchResults" v-loading="store.searchLoading" size="small">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="author" label="作者" width="120" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button size="small" type="primary" @click="selectFromSearch(scope.row.id)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="searchDialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useTextStore } from "@/store/textStore";
import { useAuthStore } from "@/store/authStore";
import { ArrowDown } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import FilterPanel from "@/components/filters/FilterPanel.vue";
import GraphView from "@/components/visualizations/GraphView.vue";
import TimelineView from "@/components/visualizations/TimelineView.vue";
import MapView from "@/components/visualizations/MapView.vue";
import StatsPanel from "@/components/visualizations/StatsPanel.vue";
import TextUploadDrawer from "@/components/layout/TextUploadDrawer.vue";
import ClassificationBanner from "@/components/layout/ClassificationBanner.vue";
import FamilyTreeView from "@/components/visualizations/FamilyTreeView.vue";
import BattleTimelineView from "@/components/visualizations/BattleTimelineView.vue";
import TextWorkspace from "./TextWorkspace.vue";

const router = useRouter();
const route = useRoute();
const store = useTextStore();
const authStore = useAuthStore();
const viewType = ref("graph");
const stage = ref("structure");
const searchDialogVisible = ref(false);

const handleLogout = () => {
  authStore.logout();
  ElMessage.success('已退出登录');
  router.push('/');
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

watch(
  () => route.params.id,
  async (id) => {
    if (id) {
      await store.selectText(id);
    }
  }
);

watch(
  () => store.searchVersion,
  (value) => {
    if (value > 0) {
      searchDialogVisible.value = true;
    }
  }
);

const insights = computed(() => store.insights);

const viewPresets = {
  travelogue: [
    { value: "map", label: "地图轨迹" },
    { value: "timeline", label: "行程时间轴" },
    { value: "graph", label: "知识图谱" }
  ],
  warfare: [
    { value: "battle", label: "对抗视图" },
    { value: "timeline", label: "战事时间轴" },
    { value: "graph", label: "知识图谱" }
  ],
  biography: [
    { value: "family", label: "亲情树" },
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
  if (category && viewPresets[category]) {
    return viewPresets[category];
  }
  return viewPresets.default;
});

watch(
  () => store.selectedText?.category,
  () => {
    const options = viewOptions.value;
    if (!options.find((opt) => opt.value === viewType.value)) {
      viewType.value = options[0]?.value || "graph";
    }
  },
  { immediate: true }
);

const componentMap = {
  graph: GraphView,
  timeline: TimelineView,
  map: MapView,
  family: FamilyTreeView,
  battle: BattleTimelineView
};

const currentComponent = computed(() => componentMap[viewType.value] || GraphView);

const viewProps = computed(() => {
  switch (viewType.value) {
    case "timeline":
      return { milestones: insights.value?.timeline || [] };
    case "map":
      return { points: insights.value?.mapPoints || [] };
    case "battle":
      return { events: insights.value?.battleTimeline || [] };
    case "family":
      return { nodes: insights.value?.familyTree || [] };
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

const handleFilterChange = (filters) => {
  store.setEntityFilters(filters.entityCategories || []);
  store.setRelationFilters(filters.relationTypes || []);
  store.setHighlightOnly(filters.highlightOnly);
};

const selectFromSearch = async (textId) => {
  searchDialogVisible.value = false;
  await store.selectText(textId);
};

const translateEntity = (category) => {
  const map = {
    PERSON: "人物",
    LOCATION: "地点",
    EVENT: "事件",
    ORGANIZATION: "组织",
    OBJECT: "器物",
    CUSTOM: "自定义"
  };
  return map[category] || category;
};

const translateRelation = (type) => {
  const map = {
    CONFLICT: "对抗",
    SUPPORT: "结盟",
    TRAVEL: "行旅",
    FAMILY: "亲属",
    TEMPORAL: "时间",
    CUSTOM: "自定义"
  };
  return map[type] || type;
};
</script>

<style scoped>
.dashboard-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stage-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.stage-nav {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stage-btn {
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 8px 18px;
  background: white;
  color: var(--muted);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.stage-btn.active {
  background: #3f3d56;
  color: #fff;
}

.stage-btn .icon {
  font-size: 16px;
}

.analysis-stage .analysis-body {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
}

.analysis-panel {
  min-height: 360px;
}

.insight-panel {
  min-height: 360px;
}

.chip-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chip-list li {
  display: flex;
  justify-content: space-between;
  background: rgba(247, 244, 236, 0.9);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 13px;
  color: #5f5750;
}

.graph-stage .graph-grid {
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 16px;
  min-height: 560px;
}

.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 16px;
}

.view-panel {
  min-height: 520px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.property {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.analysis {
  color: var(--muted);
  line-height: 1.6;
}

.stat-list {
  list-style: none;
  padding-left: 0;
  color: var(--muted);
}

.view-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.recommended {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-menu {
  margin-left: auto;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 8px 16px;
  border-radius: 6px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: rgba(0, 0, 0, 0.05);
}
</style>
