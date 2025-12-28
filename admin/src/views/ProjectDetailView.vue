<template>
  <div class="section" v-if="loading">Loading project...</div>
  <template v-else>
    <div class="section">
      <div class="cards">
        <div class="card">
          <h3>Project</h3>
          <p>{{ project?.name }}</p>
        </div>
        <div class="card alt">
          <h3>Owner</h3>
          <p>{{ project?.ownerName || "-" }}</p>
        </div>
        <div class="card">
          <h3>Created</h3>
          <p>{{ formatDate(project?.createdAt) }}</p>
        </div>
        <div class="card alt">
          <h3>Updated</h3>
          <p>{{ formatDate(project?.updatedAt) }}</p>
        </div>
      </div>
      <div class="banner" v-if="project?.description">
        {{ project.description }}
      </div>
    </div>

    <div class="section">
      <h2>Statistics</h2>
      <div class="cards">
        <div class="card" v-for="item in statCards" :key="item.label">
          <h3>{{ item.label }}</h3>
          <p>{{ item.value }}</p>
        </div>
      </div>
    </div>

    <div class="section grid-two">
      <div class="form-card">
        <h2>Members</h2>
        <div class="row" style="display: flex; gap: 10px">
          <input class="input" v-model="memberName" placeholder="username" />
          <button class="button secondary" type="button" @click="handleAddMember">
            Add
          </button>
        </div>
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
            <tr v-if="!project?.members?.length">
              <td colspan="4">No members.</td>
            </tr>
            <tr v-for="member in project?.members" :key="member.userId">
              <td>{{ member.username }}</td>
              <td>{{ member.email || "-" }}</td>
              <td>
                <select class="input" v-model="memberRoles[member.userId]" @change="updateRole(member)">
                  <option value="OWNER">OWNER</option>
                  <option value="MEMBER">MEMBER</option>
                </select>
              </td>
              <td>
                <button class="button ghost" type="button" @click="handleRemoveMember(member.username)">
                  Remove
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="form-card">
        <h2>Texts</h2>
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Title</th>
              <th>Author</th>
              <th>Category</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="texts.length === 0">
              <td colspan="5">No texts.</td>
            </tr>
            <tr v-for="text in texts" :key="text.id">
              <td>#{{ text.id }}</td>
              <td>{{ text.title }}</td>
              <td>{{ text.author || "-" }}</td>
              <td>{{ text.category || "-" }}</td>
              <td>{{ formatDate(text.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { notify } from "@/services/toast";
import {
  addMember,
  getProject,
  getProjectStats,
  removeMember,
  updateMemberRole,
  type ProjectResponse,
  type ProjectStatsResponse,
} from "@/services/api/projects";
import { listTexts, type TextDocument } from "@/services/api/texts";

const route = useRoute();
const projectId = Number(route.params.id);

const loading = ref(true);
const project = ref<ProjectResponse | null>(null);
const texts = ref<TextDocument[]>([]);
const stats = ref<ProjectStatsResponse | null>(null);
const memberName = ref("");
const memberRoles = ref<Record<number, string>>({});

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const statCards = computed(() => [
  { label: "Texts", value: stats.value?.textCount ?? 0 },
  { label: "Sections", value: stats.value?.sectionCount ?? 0 },
  { label: "Entities", value: stats.value?.entityCount ?? 0 },
  { label: "Relations", value: stats.value?.relationCount ?? 0 },
  { label: "Markers", value: stats.value?.markerCount ?? 0 },
  { label: "Hidden", value: stats.value?.hiddenCount ?? 0 },
  { label: "Jobs", value: stats.value?.jobCount ?? 0 },
]);

const syncMemberRoles = () => {
  const roles: Record<number, string> = {};
  project.value?.members?.forEach((member) => {
    roles[member.userId] = member.role || "MEMBER";
  });
  memberRoles.value = roles;
};

const loadProject = async () => {
  project.value = await getProject(projectId);
  syncMemberRoles();
};

const loadStats = async () => {
  stats.value = await getProjectStats(projectId);
};

const loadTexts = async () => {
  texts.value = await listTexts({ projectId });
};

const handleAddMember = async () => {
  if (!memberName.value.trim()) return;
  try {
    project.value = await addMember(projectId, memberName.value.trim());
    memberName.value = "";
    syncMemberRoles();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to add member.", "error");
  }
};

const handleRemoveMember = async (username: string) => {
  try {
    project.value = await removeMember(projectId, username);
    syncMemberRoles();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to remove member.", "error");
  }
};

const updateRole = async (member: { username: string; userId: number }) => {
  try {
    const nextRole = memberRoles.value[member.userId];
    project.value = await updateMemberRole(projectId, member.username, nextRole);
    syncMemberRoles();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to update role.", "error");
  }
};

onMounted(async () => {
  loading.value = true;
  await Promise.all([loadProject(), loadStats(), loadTexts()]);
  loading.value = false;
});
</script>
