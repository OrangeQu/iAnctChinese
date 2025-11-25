import { defineStore } from "pinia";
import { fetchTexts, fetchTextById, uploadText, updateTextCategory, exportText, deleteText as deleteTextApi, updateText as updateTextApi } from "@/api/texts";
import { fetchEntities, fetchRelations, createEntity, createRelation } from "@/api/annotations";
import { classifyText, fetchInsights, autoAnnotate, runFullAnalysis as runFullAnalysisApi } from "@/api/analysis";
import { fetchSections, autoSegment, updateSection as updateSectionApi } from "@/api/sections";
import { fetchNavigationTree } from "@/api/navigation";
import { searchTexts } from "@/api/search";

export const useTextStore = defineStore("textStore", {
  state: () => ({
    texts: [],
    selectedTextId: null,
    selectedText: null,
    entities: [],
    relations: [],
    insights: null,
    classification: null,
    sections: [],
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
    async loadTexts(category) {
      this.loading = true;
      try {
        const { data } = await fetchTexts(category);
        this.texts = data;
      } finally {
        this.loading = false;
      }
    },
    async selectText(id) {
      if (!id) {
        return;
      }
      this.selectedTextId = id;
      this.loading = true;
      try {
        const { data: text } = await fetchTextById(id);
        this.selectedText = text;
        const results = await Promise.allSettled([
          fetchEntities(id),
          fetchRelations(id),
          fetchInsights(id),
          fetchSections(id)
        ]);
        if (results[0].status === "fulfilled") {
          this.entities = results[0].value.data;
        } else {
          this.entities = [];
        }
        if (results[1].status === "fulfilled") {
          this.relations = results[1].value.data;
        } else {
          this.relations = [];
        }
        if (results[2].status === "fulfilled") {
          this.insights = results[2].value.data;
        } else {
          this.insights = null;
        }
        if (results[3].status === "fulfilled") {
          this.sections = results[3].value.data;
        } else {
          this.sections = [];
        }
        this.filters.entityCategories = [...this.entityOptions];
        this.filters.relationTypes = [...this.relationOptions];
      } finally {
        this.loading = false;
      }
    },
    async uploadNewText(payload) {
      this.saving = true;
      try {
        const { data } = await uploadText(payload);
        // 先本地落状态，保证界面立即显示原文
        this.selectedTextId = data.id;
        this.selectedText = data;
        this.entities = [];
        this.relations = [];
        this.sections = [];
        this.texts.unshift(data);
        await this.loadNavigationTree();
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
        await this.selectText(this.selectedTextId);
      } finally {
        this.loading = false;
      }
    },
    async createEntityAnnotation(payload) {
      await createEntity(payload);
      await this.selectText(payload.textId);
    },
    async createRelationAnnotation(payload) {
      await createRelation(payload);
      await this.selectText(payload.textId);
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
    async triggerAutoAnnotation() {
      if (!this.selectedTextId) {
        return;
      }
      await autoAnnotate(this.selectedTextId);
      await this.selectText(this.selectedTextId);
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
    async autoSegmentSections() {
      if (!this.selectedTextId) {
        return;
      }
      const { data } = await autoSegment(this.selectedTextId);
      this.sections = data;
    },
    async updateSection(sectionId, payload) {
      const { data } = await updateSectionApi(sectionId, payload);
      const index = this.sections.findIndex((section) => section.id === sectionId);
      if (index >= 0) {
        this.sections[index] = data;
      }
    },
    async loadNavigationTree() {
      const { data } = await fetchNavigationTree();
      this.navigationTree = data;
    },
    async performSearch(keyword) {
      if (!keyword) {
        this.searchResults = [];
        return;
      }
      this.searchLoading = true;
      try {
        const { data } = await searchTexts(keyword);
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
      await this.loadTexts();
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
      await this.loadTexts();
      if (this.selectedTextId === id) {
        this.selectedText = data;
      }
      return data;
    }
  }
});
