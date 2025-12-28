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
        <div class="form-card" v-if="selectedProject">
          <h2>{{ selectedProject.name }}</h2>
          <p>{{ selectedProject.description || "No description" }}</p>
          <div class="row" style="margin-top: 10px">
            <label>Add member</label>
            <div style="display: flex; gap: 10px">
              <input class="input" v-model="memberName" placeholder="username" />
              <button class="button secondary" type="button" @click="handleAddMember">
                Add
              </button>
            </div>
          </div>
          <div class="section">
            <table class="table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!selectedProject.members?.length">
                  <td colspan="4">No members.</td>
                </tr>
                <tr v-for="member in selectedProject.members" :key="member.userId">
                  <td>{{ member.username }}</td>
                  <td>{{ member.email || "-" }}</td>
                  <td>{{ member.role || "-" }}</td>
                  <td>
                    <button class="button ghost" type="button" @click="handleRemoveMember(member.username)">
                      Remove
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="form-card" v-else>
          <h2>Project Details</h2>
          <div class="banner">Select a project to see members.</div>
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
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="projects.length === 0">
            <td colspan="6">No projects yet.</td>
          </tr>
          <tr v-for="project in projects" :key="project.id">
            <td>#{{ project.id }}</td>
            <td>{{ project.name }}</td>
            <td>{{ project.description || "-" }}</td>
            <td>{{ project.ownerName || "-" }}</td>
            <td>{{ formatDate(project.updatedAt) }}</td>
            <td>
              <button class="button secondary" type="button" @click="selectProject(project.id)">
                Details
              </button>
              <button class="button primary" type="button" @click="handleDelete(project.id)">
                Delete
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
import { useAppStore } from "@/stores/app";
import {
  listProjects,
  createProject,
  getProject,
  deleteProject,
  addMember,
  removeMember,
  type ProjectResponse,
} from "@/services/api/projects";

const appStore = useAppStore();

const projects = ref<ProjectResponse[]>([]);
const selectedProject = ref<ProjectResponse | null>(null);
const loading = ref(true);
const error = ref("");
const memberName = ref("");

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
  projects.value = await listProjects();
};

const selectProject = async (id: number) => {
  selectedProject.value = await getProject(id);
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
    if (selectedProject.value?.id === id) {
      selectedProject.value = null;
    }
    await loadProjects();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to delete.";
  }
};

const handleAddMember = async () => {
  if (!selectedProject.value || !memberName.value.trim()) return;
  error.value = "";
  try {
    selectedProject.value = await addMember(
      selectedProject.value.id,
      memberName.value.trim()
    );
    memberName.value = "";
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to add member.";
  }
};

const handleRemoveMember = async (username: string) => {
  if (!selectedProject.value) return;
  error.value = "";
  try {
    selectedProject.value = await removeMember(selectedProject.value.id, username);
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to remove member.";
  }
};

watch(
  () => appStore.refreshKey,
  () => {
    loadProjects();
    if (selectedProject.value) {
      selectProject(selectedProject.value.id);
    }
  }
);

onMounted(async () => {
  loading.value = true;
  await loadProjects();
  loading.value = false;
});
</script>
