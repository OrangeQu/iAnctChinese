<template>
  <div class="section">
    <FilterBar>
      <input class="input" v-model="textIdInput" placeholder="Text ID" />
      <button class="button secondary" type="button" @click="loadIssues">Search</button>
      <button class="button ghost" type="button" :disabled="selectedIds.size === 0" @click="deleteSelected">
        Delete Selected
      </button>
    </FilterBar>
  </div>
  <div class="section">
    <DataTable>
      <template #head>
        <th></th>
        <th>Entity ID</th>
        <th>Label</th>
        <th>Category</th>
        <th>Section</th>
        <th>Span</th>
        <th>Issue</th>
        <th>Actions</th>
      </template>
      <tr v-if="issues.length === 0">
        <td colspan="8">No issues.</td>
      </tr>
      <tr v-for="issue in issues" :key="`${issue.issueType}-${issue.entityId}-${issue.sectionId}`">
        <td>
          <input type="checkbox" :value="issue.entityId" v-model="selectedList" />
        </td>
        <td>#{{ issue.entityId }}</td>
        <td>{{ issue.label || "-" }}</td>
        <td>{{ issue.category || "-" }}</td>
        <td>{{ issue.sectionIndex ?? issue.sectionId ?? "-" }}</td>
        <td>{{ issue.startOffset }} - {{ issue.endOffset }}</td>
        <td>{{ issue.issueType }}</td>
        <td>
          <RouterLink
            class="button ghost"
            v-if="issue.sectionId"
            :to="`/texts/${issue.textId}?sectionId=${issue.sectionId}`"
          >
            Go
          </RouterLink>
          <button class="button primary" type="button" @click="deleteEntity(issue.entityId)">
            Delete
          </button>
        </td>
      </tr>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink } from "vue-router";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import { notify } from "@/services/toast";
import { deleteEntity } from "@/services/api/annotations";
import { listEntityIssues, type EntityQualityIssue } from "@/services/api/quality";

const textIdInput = ref("");
const issues = ref<EntityQualityIssue[]>([]);
const selectedList = ref<number[]>([]);

const selectedIds = computed(() => new Set(selectedList.value));

const loadIssues = async () => {
  if (!textIdInput.value.trim()) {
    notify("Please input textId.", "error");
    return;
  }
  const textId = Number(textIdInput.value);
  try {
    issues.value = await listEntityIssues(textId);
    selectedList.value = [];
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load issues.", "error");
  }
};

const deleteEntityById = async (id: number) => {
  await deleteEntity(id);
  issues.value = issues.value.filter((item) => item.entityId !== id);
};

const deleteSelected = async () => {
  const ids = Array.from(selectedIds.value);
  if (ids.length === 0) return;
  try {
    await Promise.all(ids.map((id) => deleteEntityById(id)));
    notify("Deleted selected entities.", "success");
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to delete.", "error");
  }
};

const deleteEntity = async (id: number) => {
  try {
    await deleteEntityById(id);
    notify("Entity deleted.", "success");
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to delete.", "error");
  }
};
</script>
