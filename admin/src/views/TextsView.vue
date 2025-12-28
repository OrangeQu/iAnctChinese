<template>
  <div class="section" v-if="loading">Loading texts...</div>
  <template v-else>
    <div class="section">
      <FilterBar>
        <input class="input" v-model="filters.projectId" placeholder="Project id" />
        <input class="input" v-model="filters.category" placeholder="Category" />
        <input class="input" v-model="filters.era" placeholder="Era" />
        <input class="input" v-model="filters.author" placeholder="Author" />
        <select class="input" v-model="filters.deleted">
          <option value="active">Active</option>
          <option value="deleted">Deleted</option>
        </select>
        <button class="button secondary" type="button" @click="applyFilters">Search</button>
      </FilterBar>
    </div>
    <div class="section">
      <h2>Texts</h2>
      <DataTable>
        <template #head>
          <th>Title</th>
          <th>Author</th>
          <th>Category</th>
          <th>Era</th>
          <th>Project</th>
          <th>Updated</th>
          <th>Actions</th>
        </template>
        <tr v-if="texts.length === 0">
          <td colspan="7">No texts found.</td>
        </tr>
        <tr v-for="text in texts" :key="text.id">
          <td>{{ text.title }}</td>
          <td>{{ text.author || "-" }}</td>
          <td>{{ text.category || "-" }}</td>
          <td>{{ text.era || "-" }}</td>
          <td>{{ text.projectId ? `#${text.projectId}` : "-" }}</td>
          <td>{{ formatDate(text.updatedAt) }}</td>
          <td>
            <RouterLink class="button secondary" :to="`/texts/${text.id}`">Details</RouterLink>
          </td>
        </tr>
      </DataTable>
      <PaginationBar v-model:page="page" :total-pages="totalPages" />
    </div>
  </template>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { RouterLink } from "vue-router";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import PaginationBar from "@/components/PaginationBar.vue";
import { listTextsPage, type TextDocument } from "@/services/api/texts";
import { notify } from "@/services/toast";

const loading = ref(true);
const texts = ref<TextDocument[]>([]);
const page = ref(1);
const totalPages = ref(1);

const filters = reactive({
  projectId: "",
  category: "",
  era: "",
  author: "",
  deleted: "active",
});

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const loadTexts = async () => {
  loading.value = true;
  try {
    const data = await listTextsPage({
      projectId: filters.projectId ? Number(filters.projectId) : undefined,
      category: filters.category || undefined,
      era: filters.era || undefined,
      author: filters.author || undefined,
      deleted: filters.deleted === "deleted",
      page: page.value - 1,
      size: 10,
    });
    texts.value = data.content || [];
    totalPages.value = data.totalPages || 1;
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load texts.", "error");
  } finally {
    loading.value = false;
  }
};

const applyFilters = () => {
  page.value = 1;
  loadTexts();
};

watch(page, () => {
  loadTexts();
});

onMounted(async () => {
  await loadTexts();
});
</script>
