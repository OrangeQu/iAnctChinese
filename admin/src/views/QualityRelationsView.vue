<template>
  <div class="section">
    <FilterBar>
      <input class="input" v-model="textIdInput" placeholder="Text ID" />
      <button class="button secondary" type="button" @click="loadIssues">Search</button>
    </FilterBar>
  </div>
  <div class="section">
    <DataTable>
      <template #head>
        <th>Relation ID</th>
        <th>Type</th>
        <th>Source</th>
        <th>Target</th>
        <th>Issue</th>
        <th>Actions</th>
      </template>
      <tr v-if="issues.length === 0">
        <td colspan="6">No issues.</td>
      </tr>
      <tr v-for="issue in issues" :key="`${issue.issueType}-${issue.relationId}`">
        <td>#{{ issue.relationId }}</td>
        <td>{{ issue.relationType || "-" }}</td>
        <td>{{ issue.sourceId || "-" }}</td>
        <td>{{ issue.targetId || "-" }}</td>
        <td>
          <div>{{ issue.issueType }}</div>
          <div class="muted">{{ issue.message }}</div>
          <div class="muted" v-if="issue.evidence">Evidence: {{ issue.evidence }}</div>
        </td>
        <td>
          <RouterLink
            v-if="issue.sourceSectionId || issue.targetSectionId"
            class="button ghost"
            :to="`/texts/${issue.textId}?sectionId=${issue.sourceSectionId || issue.targetSectionId}`"
          >
            Go
          </RouterLink>
        </td>
      </tr>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import { notify } from "@/services/toast";
import { listRelationIssues, type RelationQualityIssue } from "@/services/api/quality";

const textIdInput = ref("");
const issues = ref<RelationQualityIssue[]>([]);

const loadIssues = async () => {
  if (!textIdInput.value.trim()) {
    notify("Please input textId.", "error");
    return;
  }
  const textId = Number(textIdInput.value);
  try {
    issues.value = await listRelationIssues(textId);
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load issues.", "error");
  }
};
</script>

<style scoped>
.muted {
  color: var(--muted);
  font-size: 12px;
}
</style>
