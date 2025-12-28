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
              <th>Name</th>
              <th>Type</th>
              <th>Context</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="entities.length === 0">
              <td colspan="5">No entities.</td>
            </tr>
            <tr v-for="entity in entities" :key="entity.id">
              <td>#{{ entity.id }}</td>
              <td>{{ entity.entityName || "-" }}</td>
              <td>{{ entity.entityType || "-" }}</td>
              <td>{{ entity.context || "-" }}</td>
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
              <th>Subject</th>
              <th>Relation</th>
              <th>Object</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="relations.length === 0">
              <td colspan="5">No relations.</td>
            </tr>
            <tr v-for="relation in relations" :key="relation.id">
              <td>#{{ relation.id }}</td>
              <td>{{ relation.subject || "-" }}</td>
              <td>{{ relation.relation || "-" }}</td>
              <td>{{ relation.object || "-" }}</td>
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
