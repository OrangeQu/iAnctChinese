<template>
  <div class="page-container">
    <div class="header-section">
      <div>
        <h1>知识图谱</h1>
        <p class="subtitle">全览古籍人物关系与事件脉络</p>
      </div>
    </div>

    <div class="graph-layout">
       <aside class="filter-sidebar card">
          <FilterPanel
            :navigation-tree="store.navigationTree"
            :selected-id="store.selectedTextId"
            :filters="store.filters"
            :entity-options="store.entityOptions"
            :relation-options="store.relationOptions"
            @select:text="store.selectText"
            @update:filters="handleFilterChange"
          />
       </aside>
       <main class="graph-main card">
          <GraphView
            :entities="store.entities"
            :relations="store.relations"
            :highlight-only="store.filters.highlightOnly"
            :active-entity-categories="store.filters.entityCategories"
            :active-relation-types="store.filters.relationTypes"
          />
       </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { useTextStore } from "@/store/textStore";
import GraphView from "@/components/visualizations/GraphView.vue";
import FilterPanel from "@/components/filters/FilterPanel.vue";

const store = useTextStore();

const handleFilterChange = (filters) => {
  store.setEntityFilters(filters.entityCategories || []);
  store.setRelationFilters(filters.relationTypes || []);
  store.setHighlightOnly(filters.highlightOnly);
};

onMounted(async () => {
  if (!store.texts.length || !store.navigationTree) {
    await store.initDashboard();
  }
  if (!store.selectedTextId && store.texts.length) {
    await store.selectText(store.texts[0].id);
  }
});
</script>

<style scoped>
.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

h1 {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.subtitle {
  color: var(--text-secondary);
  font-size: 16px;
}

.graph-layout {
  display: flex;
  gap: 24px;
  height: calc(100vh - 200px);
}

.filter-sidebar {
  width: 320px; /* Widened from 260px */
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: #FFFFFF;
  border: none;
  padding: 24px;
}

.graph-main {
  flex: 1;
  overflow: hidden;
  padding: 0; /* Full bleed graph */
  position: relative;
  border: none;
}
</style>
