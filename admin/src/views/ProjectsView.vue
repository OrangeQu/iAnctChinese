<template>
  <div class="section" v-if="loading">Loading projects...</div>
  <template v-else>
    <div class="section">
      <div class="grid-two">
        <div class="form-card">
          <h2>Create Project</h2>
          <form @submit.prevent="handleCreate">
            <div class="row">
              <label>Name</label>
              <input class="input" v-model="createForm.name" required />
            </div>
            <div class="row">
              <label>Description</label>
              <textarea v-model="createForm.description" rows="3"></textarea>
            </div>
            <button class="button primary" type="submit">Create</button>
            <div class="toast" v-if="error">{{ error }}</div>
          </form>
        </div>
        <div class="form-card">
          <h2>Filters</h2>
          <div class="row">
            <label>Search</label>
            <input class="input" v-model="query" placeholder="Project name" />
          </div>
          <div class="row">
            <label>Status</label>
            <select class="input" v-model="deletedFilter">
              <option value="active">Active</option>
              <option value="deleted">Recycle Bin</option>
            </select>
          </div>
          <button class="button secondary" type="button" @click="loadProjects">Apply</button>
        </div>
      </div>
    </div>
    <div class="section">
      <h2>Projects</h2>
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Description</th>
            <th>Owner</th>
            <th>Updated</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="projects.length === 0">
            <td colspan="7">No projects found.</td>
          </tr>
          <tr v-for="project in projects" :key="project.id">
            <td>#{{ project.id }}</td>
            <td>{{ project.name }}</td>
            <td>{{ project.description || "-" }}</td>
            <td>{{ project.ownerName || "-" }}</td>
            <td>{{ formatDate(project.updatedAt) }}</td>
            <td>
              <span class="pill" v-if="!project.deleted">Active</span>
              <span class="pill" v-else>Deleted</span>
            </td>
            <td>
              <RouterLink class="button secondary" :to="`/projects/${project.id}`">
                Details
              </RouterLink>
              <button
                v-if="!project.deleted"
                class="button primary"
                type="button"
                @click="handleDelete(project.id)"
              >
                Delete
              </button>
              <button
                v-else
                class="button ghost"
                type="button"
                @click="handleRestore(project.id)"
              >
                Restore
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { RouterLink } from "vue-router";
import { useAppStore } from "@/stores/app";
import {
  listProjects,
  createProject,
  deleteProject,
  restoreProject,
  type ProjectResponse,
} from "@/services/api/projects";

const appStore = useAppStore();

const projects = ref<ProjectResponse[]>([]);
const loading = ref(true);
const error = ref("");
const query = ref("");
const deletedFilter = ref("active");

const createForm = reactive({
  name: "",
  description: "",
});

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const loadProjects = async () => {
  const deleted = deletedFilter.value === "deleted";
  projects.value = await listProjects({
    query: query.value.trim() || undefined,
    deleted,
  });
};

const handleCreate = async () => {
  error.value = "";
  try {
    await createProject({
      name: createForm.name,
      description: createForm.description,
    });
    createForm.name = "";
    createForm.description = "";
    await loadProjects();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to create.";
  }
};

const handleDelete = async (id: number) => {
  error.value = "";
  try {
    await deleteProject(id);
    await loadProjects();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to delete.";
  }
};

const handleRestore = async (id: number) => {
  error.value = "";
  try {
    await restoreProject(id);
    await loadProjects();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to restore.";
  }
};

watch(
  () => appStore.refreshKey,
  () => {
    loadProjects();
  }
);

onMounted(async () => {
  loading.value = true;
  await loadProjects();
  loading.value = false;
});
</script>
