import { defineStore } from "pinia";
import { fetchTexts, fetchTextById, uploadText, updateTextCategory, exportText, deleteText as deleteTextApi, updateText as updateTextApi } from "@/api/texts";
import { fetchEntities, fetchRelations, createEntity, createRelation, deleteEntity as deleteEntityApi, deleteRelation as deleteRelationApi } from "@/api/annotations";
import { classifyText, fetchInsights, autoAnnotate, runFullAnalysis as runFullAnalysisApi, extractRelations, segmentText } from "@/api/analysis";
import { fetchSections, updateSection as updateSectionApi } from "@/api/sections";
import { fetchNavigationTree } from "@/api/navigation";
import { searchTexts } from "@/api/search";

export const useTextStore = defineStore("textStore", {
  state: () => ({
    texts: [],
    selectedTextId: null,
    selectedText: null,
    currentProjectId: null,
    entities: [],
    relations: [],
    insights: null,
    classification: null,
    sections: [],
    detailLoading: false,
    detailProgress: 0,
    currentRequestToken: null,
    navigationTree: null,
    searchResults: [],
    searchVersion: 0,
    searchLoading: false,
    exporting: false,
    loading: false,
    saving: false,
    analysisRunning: false,
    classifyRunning: false,
    filters: {
      entityCategories: [],
      relationTypes: [],
      highlightOnly: false
    }
  }),
  getters: {
    entityOptions(state) {
      const options = new Set(state.entities.map((entity) => entity.category));
      return Array.from(options);
    },
    relationOptions(state) {
      const options = new Set(state.relations.map((relation) => relation.relationType));
      return Array.from(options);
    }
  },
  actions: {
    async initDashboard() {
      await this.loadTexts();
      await this.loadNavigationTree();
      if (this.texts.length > 0) {
        await this.selectText(this.texts[0].id);
      }
    },
    async loadTexts(category, projectId) {
      this.loading = true;
      try {
        const effectiveProjectId = projectId !== undefined ? projectId : this.currentProjectId;
        this.currentProjectId = effectiveProjectId ?? null;
        const { data } = await fetchTexts(category, effectiveProjectId);
        this.texts = data;
      } finally {
        this.loading = false;
      }
    },
    async selectText(id, { force = false } = {}) {
      if (!id) {
        return;
      }
      if (!force && id === this.selectedTextId && this.selectedText && !this.detailLoading) {
        // 已有当前文档的数据，避免重复请求导致卡顿
        return;
      }
      const requestToken = Symbol("textRequest");
      this.currentRequestToken = requestToken;
      this.selectedTextId = id;
      this.loading = true;
      this.detailLoading = true;
      this.detailProgress = 0;
      try {
        // 先拿正文，尽快渲染基础页面，再异步拉其他数据，避免整页长时间 loading
        const { data: text } = await fetchTextById(id);
        if (this.currentRequestToken !== requestToken) {
          return;
        }
        this.selectedText = text;
        this.detailProgress = 20;

        let entities = [];
        try {
          const { data } = await fetchEntities(id);
          entities = data;
        } catch (error) {
          console.error("fetch entities failed", error);
        }
        if (this.currentRequestToken !== requestToken) {
          return;
        }
        this.entities = entities;
        this.detailProgress = 40;

        let relations = [];
        try {
          const { data } = await fetchRelations(id);
          relations = data;
        } catch (error) {
          console.error("fetch relations failed", error);
        }
        if (this.currentRequestToken !== requestToken) {
          return;
        }
        this.relations = relations;
        this.detailProgress = 60;

        let insights = null;
        try {
          const { data } = await fetchInsights(id, { light: true });
          insights = data;
        } catch (error) {
          console.error("fetch insights failed", error);
        }
        if (this.currentRequestToken !== requestToken) {
          return;
        }
        this.insights = insights;
        this.detailProgress = 80;

        let sections = [];
        try {
          const { data } = await fetchSections(id);
          sections = data;
        } catch (error) {
          console.error("fetch sections failed", error);
        }
        if (this.currentRequestToken !== requestToken) {
          return;
        }
        this.sections = sections;
        this.detailProgress = 100;
        this.filters.entityCategories = [...this.entityOptions];
        this.filters.relationTypes = [...this.relationOptions];
      } finally {
        if (this.currentRequestToken === requestToken) {
          this.loading = false;
          this.detailLoading = false;
          if (this.detailProgress < 100) {
            this.detailProgress = 100;
          }
        }
      }
    },
    async uploadNewText(payload) {
      this.saving = true;
      try {
        if (payload?.projectId !== undefined) {
          this.currentProjectId = payload.projectId;
        }
        const { data } = await uploadText(payload);
        // 先本地落状态，保证界面立即显示原文
        this.selectedTextId = data.id;
        this.selectedText = data;
        this.entities = [];
        this.relations = [];
        this.sections = [];
        this.texts.unshift(data);
        this.detailLoading = false;
        this.detailProgress = 0;
        this.currentRequestToken = null;
        await this.loadNavigationTree();
        return data;
      } finally {
        this.saving = false;
      }
    },
    async refreshCurrentText() {
      if (!this.selectedTextId) {
        return;
      }
      this.loading = true;
      try {
        await this.selectText(this.selectedTextId, { force: true });
      } finally {
        this.loading = false;
      }
    },
    async createEntityAnnotation(payload) {
      const { data: created } = await createEntity(payload);
      // 乐观更新：立即注入本地实体列表，保证高亮立刻出现
      const exists = this.entities.some((e) => e.id === created.id);
      this.entities = exists ? this.entities : [...this.entities, created];
      // 可选：如需刷新关系/洞察可在进入图谱视图时再触发
      this.filters.entityCategories = [...this.entityOptions];
      this.filters.relationTypes = [...this.relationOptions];
    },
    async createRelationAnnotation(payload) {
      const { data: created } = await createRelation(payload);
      // 乐观更新：立即注入本地关系列表
      const exists = this.relations.some((r) => r.id === created.id);
      this.relations = exists ? this.relations : [...this.relations, created];
      this.filters.entityCategories = [...this.entityOptions];
      this.filters.relationTypes = [...this.relationOptions];
    },
    async deleteEntityAnnotation(entityId) {
      if (!entityId) {
        return;
      }
      await deleteEntityApi(entityId);
      const targetId = String(entityId);
      this.entities = this.entities.filter((entity) => String(entity.id) !== targetId);
      this.relations = this.relations.filter((relation) => {
        const sourceId = relation.source?.id || relation.sourceEntityId;
        const target = relation.target?.id || relation.targetEntityId;
        return String(sourceId) !== targetId && String(target) !== targetId;
      });
      this.filters.entityCategories = [...this.entityOptions];
      this.filters.relationTypes = [...this.relationOptions];
    },
    async deleteRelationAnnotation(relationId) {
      if (!relationId) {
        return;
      }
      await deleteRelationApi(relationId);
      const targetId = String(relationId);
      this.relations = this.relations.filter((relation) => String(relation.id) !== targetId);
      this.filters.relationTypes = [...this.relationOptions];
    },
    async classifySelectedText(model) {
      if (!this.selectedTextId) {
        return;
      }
      this.classifyRunning = true;
      try {
        const { data } = await classifyText(this.selectedTextId, model);
        this.classification = data;
        if (data?.suggestedCategory) {
          if (this.selectedText) {
            this.selectedText.category = data.suggestedCategory;
          }
          const idx = this.texts.findIndex((item) => item.id === this.selectedTextId);
          if (idx >= 0) {
            this.texts[idx].category = data.suggestedCategory;
          }
          await this.loadNavigationTree();
        }
      } finally {
        this.classifyRunning = false;
      }
    },
    async triggerAutoAnnotation(model) {
      if (!this.selectedTextId) {
        return;
      }
      await autoAnnotate(this.selectedTextId, model);
      await this.selectText(this.selectedTextId, { force: true });
    },
    async triggerRelationExtraction(model) {
      if (!this.selectedTextId) {
        return;
      }
      await extractRelations(this.selectedTextId, model);
      await this.selectText(this.selectedTextId, { force: true });
    },
    async runSentenceSegmentation(model) {
      if (!this.selectedTextId) {
        return;
      }
      await segmentText(this.selectedTextId, model);
      await this.selectText(this.selectedTextId, { force: true });
    },
    async runFullAnalysis(model) {
      if (!this.selectedTextId) {
        return;
      }
      this.analysisRunning = true;
      try {
        const { data } = await runFullAnalysisApi(this.selectedTextId, model);
        this.classification = data.classification;
        this.insights = data.insights;
        this.sections = data.sections || [];

        if (data.classification?.suggestedCategory) {
          if (this.selectedText) {
            this.selectedText.category = data.classification.suggestedCategory;
          }
          const idx = this.texts.findIndex((item) => item.id === this.selectedTextId);
          if (idx >= 0) {
            this.texts[idx].category = data.classification.suggestedCategory;
          }
        }

        const results = await Promise.allSettled([
          fetchEntities(this.selectedTextId),
          fetchRelations(this.selectedTextId)
        ]);
        this.entities = results[0].status === "fulfilled" ? results[0].value.data : [];
        this.relations = results[1].status === "fulfilled" ? results[1].value.data : [];
        this.filters.entityCategories = [...this.entityOptions];
        this.filters.relationTypes = [...this.relationOptions];

        // 导航树受分类变更影响，重新加载
        await this.loadNavigationTree();
      } finally {
        this.analysisRunning = false;
      }
    },
    async updateSelectedCategory(category) {
      if (!this.selectedTextId) {
        return;
      }
      await updateTextCategory(this.selectedTextId, category);
      if (this.selectedText) {
        this.selectedText.category = category;
      }
      const textIndex = this.texts.findIndex((item) => item.id === this.selectedTextId);
      if (textIndex >= 0) {
        this.texts[textIndex].category = category;
      }
    },
    toggleHighlightOnly() {
      this.filters.highlightOnly = !this.filters.highlightOnly;
    },
    setHighlightOnly(value) {
      this.filters.highlightOnly = value;
    },
    setEntityFilters(values) {
      this.filters.entityCategories = values;
    },
    setRelationFilters(values) {
      this.filters.relationTypes = values;
    },
    async updateSection(sectionId, payload) {
      const { data } = await updateSectionApi(sectionId, payload);
      const index = this.sections.findIndex((section) => section.id === sectionId);
      if (index >= 0) {
        this.sections[index] = data;
      }
    },
    async loadInsights({ textId = this.selectedTextId, light = false } = {}) {
      if (!textId) return;
      const params = light ? { light: true } : {};
      const { data } = await fetchInsights(textId, params);
      this.insights = data;
    },
    async loadNavigationTree(projectId = this.currentProjectId) {
      const { data } = await fetchNavigationTree(projectId);
      this.navigationTree = data;
    },
    async performSearch(keyword) {
      if (!keyword) {
        this.searchResults = [];
        return;
      }
      this.searchLoading = true;
      try {
        const { data } = await searchTexts(keyword, this.currentProjectId);
        this.searchResults = data;
        this.searchVersion += 1;
      } finally {
        this.searchLoading = false;
      }
    },
    async exportSelectedText() {
      if (!this.selectedTextId) {
        return;
      }
      this.exporting = true;
      try {
        const response = await exportText(this.selectedTextId);
        const blob = new Blob([response.data], { type: "application/json" });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `text-${this.selectedTextId}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } finally {
        this.exporting = false;
      }
    },
    async deleteText(id) {
      if (!id) {
        return;
      }
      // 乐观更新：先从前端列表剔除
      this.texts = this.texts.filter((item) => item.id !== id);
      await deleteTextApi(id);
      // 再刷新服务器数据，确保一致
      await this.loadTexts(undefined, this.currentProjectId);
      if (this.selectedTextId === id) {
        this.selectedTextId = null;
        this.selectedText = null;
        this.entities = [];
        this.relations = [];
        this.sections = [];
        this.insights = null;
        this.classification = null;
      }
      await this.loadNavigationTree();
    },
    async updateText(id, payload) {
      const { data } = await updateTextApi(id, payload);
      await this.loadTexts(undefined, this.currentProjectId);
      if (this.selectedTextId === id) {
        this.selectedText = data;
      }
      return data;
    }
  }
});
