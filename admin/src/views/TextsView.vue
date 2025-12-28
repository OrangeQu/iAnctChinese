<template>
  <div class="section" v-if="loading">Loading texts...</div>
  <template v-else>
    <div class="section">
      <div class="grid-two">
        <div class="form-card">
          <h2>New Text</h2>
          <form @submit.prevent="handleCreate">
            <div class="row">
              <label>Title</label>
              <input class="input" v-model="createForm.title" required />
            </div>
            <div class="row">
              <label>Category</label>
              <input class="input" v-model="createForm.category" />
            </div>
            <div class="row">
              <label>Author</label>
              <input class="input" v-model="createForm.author" />
            </div>
            <div class="row">
              <label>Era</label>
              <input class="input" v-model="createForm.era" />
            </div>
            <div class="row">
              <label>Project Id (optional)</label>
              <input class="input" v-model.number="createForm.projectId" type="number" />
            </div>
            <div class="row">
              <label>Description</label>
              <textarea v-model="createForm.description" rows="2"></textarea>
            </div>
            <div class="row">
              <label>Content</label>
              <textarea v-model="createForm.content" rows="5"></textarea>
            </div>
            <button class="button primary" type="submit">Create</button>
            <div class="toast" v-if="error">{{ error }}</div>
          </form>
        </div>
        <div class="form-card">
          <h2>Selected Text</h2>
          <div class="row">
            <TextSelector
              label="Pick text to edit"
              :texts="texts"
              v-model="selectedTextId"
            />
          </div>
          <form @submit.prevent="handleUpdate">
            <div class="row">
              <label>Title</label>
              <input class="input" v-model="updateForm.title" required />
            </div>
            <div class="row">
              <label>Category</label>
              <input class="input" v-model="updateForm.category" />
            </div>
            <div class="row">
              <label>Author</label>
              <input class="input" v-model="updateForm.author" />
            </div>
            <div class="row">
              <label>Era</label>
              <input class="input" v-model="updateForm.era" />
            </div>
            <div class="row">
              <label>Content</label>
              <textarea v-model="updateForm.content" rows="5"></textarea>
            </div>
            <button class="button secondary" type="submit">Save Changes</button>
          </form>
        </div>
      </div>
    </div>
    <div class="section">
      <h2>Texts</h2>
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Category</th>
            <th>Author</th>
            <th>Era</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="texts.length === 0">
            <td colspan="7">No texts yet.</td>
          </tr>
          <tr v-for="text in texts" :key="text.id">
            <td>#{{ text.id }}</td>
            <td>{{ text.title }}</td>
            <td>{{ text.category || "-" }}</td>
            <td>{{ text.author || "-" }}</td>
            <td>{{ text.era || "-" }}</td>
            <td>{{ formatDate(text.createdAt) }}</td>
            <td>
              <button class="button ghost" type="button" @click="handleExport(text.id)">Export</button>
              <button class="button secondary" type="button" @click="selectForEdit(text.id)">Edit</button>
              <button class="button primary" type="button" @click="handleDelete(text.id)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import TextSelector from "@/components/TextSelector.vue";
import { useAppStore } from "@/stores/app";
import {
  listTexts,
  createText,
  updateText,
  deleteText,
  exportText,
  type TextDocument,
} from "@/services/api/texts";

const appStore = useAppStore();

const texts = ref<TextDocument[]>([]);
const loading = ref(true);
const error = ref("");

const createForm = reactive({
  title: "",
  content: "",
  description: "",
  projectId: null as number | null,
  category: "",
  author: "",
  era: "",
});

const updateForm = reactive({
  title: "",
  content: "",
  category: "",
  author: "",
  era: "",
});

const selectedTextId = computed({
  get: () => appStore.selectedTextId,
  set: (value) => appStore.setSelectedTextId(value),
});

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const loadTexts = async () => {
  texts.value = await listTexts();
  if (!selectedTextId.value && texts.value.length > 0) {
    selectedTextId.value = texts.value[0].id;
  }
  syncUpdateForm();
};

const syncUpdateForm = () => {
  const selected = texts.value.find((text) => text.id === selectedTextId.value);
  if (!selected) return;
  updateForm.title = selected.title || "";
  updateForm.category = selected.category || "";
  updateForm.author = selected.author || "";
  updateForm.era = selected.era || "";
  updateForm.content = selected.content || "";
};

const handleCreate = async () => {
  error.value = "";
  try {
    await createText({
      title: createForm.title,
      content: createForm.content,
      description: createForm.description,
      projectId: createForm.projectId || undefined,
      category: createForm.category,
      author: createForm.author,
      era: createForm.era,
    });
    Object.assign(createForm, {
      title: "",
      content: "",
      description: "",
      projectId: null,
      category: "",
      author: "",
      era: "",
    });
    await loadTexts();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to create.";
  }
};

const handleUpdate = async () => {
  if (!selectedTextId.value) return;
  error.value = "";
  try {
    await updateText(selectedTextId.value, {
      title: updateForm.title,
      content: updateForm.content,
      category: updateForm.category,
      author: updateForm.author,
      era: updateForm.era,
    });
    await loadTexts();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to update.";
  }
};

const handleDelete = async (id: number) => {
  error.value = "";
  try {
    await deleteText(id);
    await loadTexts();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to delete.";
  }
};

const handleExport = async (id: number) => {
  error.value = "";
  try {
    const blob = await exportText(id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `text-${id}.json`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to export.";
  }
};

const selectForEdit = (id: number) => {
  selectedTextId.value = id;
};

watch(
  () => selectedTextId.value,
  () => {
    syncUpdateForm();
  }
);

watch(
  () => appStore.refreshKey,
  () => {
    loadTexts();
  }
);

onMounted(async () => {
  loading.value = true;
  await loadTexts();
  loading.value = false;
});
</script>
