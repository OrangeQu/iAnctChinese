<template>
  <div class="section" v-if="loading">Loading annotations...</div>
  <template v-else>
    <div class="section" v-if="texts.length === 0">
      <div class="banner">No texts available for annotations.</div>
    </div>
    <template v-else>
      <div class="section">
        <div class="form-card">
          <TextSelector
            label="Text context"
            :texts="texts"
            v-model="selectedTextId"
          />
        </div>
      </div>
      <div class="section">
        <h2>Entities</h2>
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Label</th>
              <th>Category</th>
              <th>Span</th>
              <th>Context</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="entities.length === 0">
              <td colspan="6">No entities.</td>
            </tr>
            <tr v-for="entity in entities" :key="entity.id">
              <td>#{{ entity.id }}</td>
              <td>{{ entity.label || "-" }}</td>
              <td>{{ entity.category || "-" }}</td>
              <td>{{ entity.startOffset ?? "-" }} - {{ entity.endOffset ?? "-" }}</td>
              <td>{{ entityContext(entity) || "-" }}</td>
              <td>
                <button class="button ghost" type="button" @click="handleDeleteEntity(entity.id)">
                  Delete
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="section">
        <h2>Relations</h2>
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Source</th>
              <th>Relation</th>
              <th>Target</th>
              <th>Evidence</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="relations.length === 0">
              <td colspan="6">No relations.</td>
            </tr>
            <tr v-for="relation in relations" :key="relation.id">
              <td>#{{ relation.id }}</td>
              <td>{{ relation.source?.label || "-" }}</td>
              <td>{{ relation.relationType || "-" }}</td>
              <td>{{ relation.target?.label || "-" }}</td>
              <td>{{ relation.evidence || "-" }}</td>
              <td>
                <button class="button ghost" type="button" @click="handleDeleteRelation(relation.id)">
                  Delete
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="toast" v-if="error">{{ error }}</div>
    </template>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import TextSelector from "@/components/TextSelector.vue";
import { useAppStore } from "@/stores/app";
import { listTexts, type TextDocument } from "@/services/api/texts";
import {
  listEntities,
  listRelations,
  deleteEntity,
  deleteRelation,
  type EntityAnnotation,
  type RelationAnnotation,
} from "@/services/api/annotations";

const appStore = useAppStore();

const texts = ref<TextDocument[]>([]);
const entities = ref<EntityAnnotation[]>([]);
const relations = ref<RelationAnnotation[]>([]);
const loading = ref(true);
const error = ref("");

const selectedTextId = computed({
  get: () => appStore.selectedTextId,
  set: (value) => appStore.setSelectedTextId(value),
});

const loadTexts = async () => {
  texts.value = await listTexts();
  if (!selectedTextId.value && texts.value.length > 0) {
    selectedTextId.value = texts.value[0].id;
  }
};

const loadAnnotations = async () => {
  if (!selectedTextId.value) {
    entities.value = [];
    relations.value = [];
    return;
  }
  [entities.value, relations.value] = await Promise.all([
    listEntities(selectedTextId.value),
    listRelations(selectedTextId.value),
  ]);
};

const loadAll = async () => {
  loading.value = true;
  await loadTexts();
  await loadAnnotations();
  loading.value = false;
};

const entityContext = (entity: EntityAnnotation) => {
  const original = entity.section?.originalText;
  if (!original) return "";
  if (entity.startOffset == null || entity.endOffset == null) return "";
  const start = Math.max(0, Math.min(entity.startOffset, original.length));
  const end = Math.max(start, Math.min(entity.endOffset, original.length));
  return original.slice(start, end);
};

const handleDeleteEntity = async (id: number) => {
  error.value = "";
  try {
    await deleteEntity(id);
    await loadAnnotations();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to delete entity.";
  }
};

const handleDeleteRelation = async (id: number) => {
  error.value = "";
  try {
    await deleteRelation(id);
    await loadAnnotations();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to delete relation.";
  }
};

watch(
  () => selectedTextId.value,
  () => {
    loadAnnotations();
  }
);

watch(
  () => appStore.refreshKey,
  () => {
    loadAll();
  }
);

onMounted(loadAll);
</script>
