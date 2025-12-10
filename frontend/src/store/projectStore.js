import { defineStore } from "pinia";
import { projectApi } from "@/api/projects";

export const useProjectStore = defineStore("projectStore", {
  state: () => ({
    projects: [],
    currentProject: null,
    loading: false,
    saving: false,
    error: null
  }),
  actions: {
    async fetchMyProjects() {
      this.loading = true;
      try {
        const { data } = await projectApi.listMine();
        this.projects = data || [];
        return data;
      } finally {
        this.loading = false;
      }
    },
    async selectProject(id) {
      this.loading = true;
      try {
        const { data } = await projectApi.getDetails(id);
        this.currentProject = data;
        return data;
      } finally {
        this.loading = false;
      }
    },
    async createProject(payload) {
      this.saving = true;
      try {
        const { data } = await projectApi.create(payload);
        this.projects.unshift(data);
        this.currentProject = data;
        return data;
      } finally {
        this.saving = false;
      }
    },
    async deleteProject(id) {
      await projectApi.delete(id);
      this.projects = this.projects.filter((p) => p.id !== id);
      if (this.currentProject?.id === id) {
        this.currentProject = null;
      }
    },
    async addMember(id, username) {
      const { data } = await projectApi.addMember(id, { username });
      this._updateProjectCache(data);
      return data;
    },
    async removeMember(id, username) {
      const { data } = await projectApi.removeMember(id, { username });
      this._updateProjectCache(data);
      return data;
    },
    _updateProjectCache(project) {
      if (!project) return;
      const idx = this.projects.findIndex((p) => p.id === project.id);
      if (idx >= 0) {
        this.projects[idx] = project;
      } else {
        this.projects.unshift(project);
      }
      if (this.currentProject?.id === project.id) {
        this.currentProject = project;
      }
    }
  }
});
