<template>
  <div class="section" v-if="loading">Loading text...</div>
  <template v-else>
    <div class="section">
      <div class="cards">
        <div class="card">
          <h3>Title</h3>
          <p>{{ text?.title }}</p>
        </div>
        <div class="card alt">
          <h3>Author</h3>
          <p>{{ text?.author || "-" }}</p>
        </div>
        <div class="card">
          <h3>Category</h3>
          <p>{{ text?.category || "-" }}</p>
        </div>
        <div class="card alt">
          <h3>Era</h3>
          <p>{{ text?.era || "-" }}</p>
        </div>
      </div>
    </div>

    <div class="section text-workspace">
      <div class="panel">
        <h2>Sections</h2>
        <table class="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Summary</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="sections.length === 0">
              <td colspan="2">No sections.</td>
            </tr>
            <tr
              v-for="section in sections"
              :key="section.id"
              :class="{ active: section.id === selectedSection?.id }"
              @click="selectSection(section)"
            >
              <td>{{ section.sequenceIndex }}</td>
              <td>{{ section.summary || "-" }}</td>
            </tr>
          </tbody>
        </table>
        <PaginationBar v-model:page="sectionPage" :total-pages="sectionTotalPages" />
      </div>

      <div class="panel">
        <h2>Section</h2>
        <div v-if="!selectedSection" class="banner">Select a section to view details.</div>
        <template v-else>
          <div class="row">
            <label>Original text</label>
            <div class="text-block">
              <span
                v-for="(chunk, index) in highlightedOriginal"
                :key="index"
                :class="{ highlight: chunk.highlight }"
              >{{ chunk.text }}</span>
            </div>
          </div>
          <div class="row">
            <label>Punctuated text</label>
            <textarea v-model="editForm.punctuatedText" rows="6"></textarea>
          </div>
          <div class="row">
            <label>Summary</label>
            <input class="input" v-model="editForm.summary" />
          </div>
          <button class="button primary" type="button" @click="saveSection">
            Save
          </button>
        </template>
      </div>

      <div class="panel">
        <h2>Annotations</h2>
        <Tabs v-model="activeTab" :tabs="['Entities', 'Relations', 'Markers']" />
        <div v-if="activeTab === 'Entities'">
          <table class="table">
            <thead>
              <tr>
                <th>Label</th>
                <th>Category</th>
                <th>Span</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="entities.length === 0">
                <td colspan="3">No entities.</td>
              </tr>
              <tr v-for="entity in entities" :key="entity.id" @click="highlightEntity(entity)">
                <td>{{ entity.label }}</td>
                <td>{{ entity.category || "-" }}</td>
                <td>{{ entity.startOffset }} - {{ entity.endOffset }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="activeTab === 'Relations'">
          <table class="table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Source</th>
                <th>Target</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="relations.length === 0">
                <td colspan="3">No relations.</td>
              </tr>
              <tr v-for="relation in relations" :key="relation.id" @click="selectRelation(relation)">
                <td>{{ relation.relationType || "-" }}</td>
                <td>{{ relation.source?.label || "-" }}</td>
                <td>{{ relation.target?.label || "-" }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="selectedRelation" class="banner" style="margin-top: 12px;">
            <div><strong>Evidence:</strong> {{ selectedRelation.evidence || "-" }}</div>
          </div>
        </div>
        <div v-else>
          <table class="table">
            <thead>
              <tr>
                <th>Label</th>
                <th>Category</th>
                <th>Lat</th>
                <th>Lng</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="markers.length === 0">
                <td colspan="4">No markers.</td>
              </tr>
              <tr v-for="marker in markers" :key="marker.id">
                <td>{{ marker.entityLabel || marker.entityId || "-" }}</td>
                <td>{{ marker.category || "-" }}</td>
                <td>{{ marker.latitude ?? "-" }}</td>
                <td>{{ marker.longitude ?? "-" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PaginationBar from "@/components/PaginationBar.vue";
import Tabs from "@/components/Tabs.vue";
import { notify } from "@/services/toast";
import { getText, type TextDocument } from "@/services/api/texts";
import {
  listSections,
  updateSection,
  listSectionEntities,
  listSectionRelations,
  listSectionMarkers,
  type TextSection,
  type SectionEntity,
  type SectionRelation,
  type SectionMarker,
} from "@/services/api/sections";

const route = useRoute();
const textId = Number(route.params.id);

const loading = ref(true);
const text = ref<TextDocument | null>(null);
const sections = ref<TextSection[]>([]);
const sectionPage = ref(1);
const sectionTotalPages = ref(1);
const selectedSection = ref<TextSection | null>(null);

const entities = ref<SectionEntity[]>([]);
const relations = ref<SectionRelation[]>([]);
const markers = ref<SectionMarker[]>([]);
const activeTab = ref("Entities");
const selectedRelation = ref<SectionRelation | null>(null);

const highlightRange = ref<{ start: number; end: number } | null>(null);

const editForm = reactive({
  punctuatedText: "",
  summary: "",
});

const highlightedOriginal = computed(() => {
  const textValue = selectedSection.value?.originalText || "";
  const range = highlightRange.value;
  if (!range || range.end <= range.start) {
    return [{ text: textValue, highlight: false }];
  }
  const start = Math.max(0, Math.min(range.start, textValue.length));
  const end = Math.max(start, Math.min(range.end, textValue.length));
  return [
    { text: textValue.slice(0, start), highlight: false },
    { text: textValue.slice(start, end), highlight: true },
    { text: textValue.slice(end), highlight: false },
  ];
});

const loadText = async () => {
  text.value = await getText(textId);
};

const loadSections = async () => {
  const data = await listSections(textId, { page: sectionPage.value - 1, size: 12 });
  sections.value = data.content || [];
  sectionTotalPages.value = data.totalPages || 1;
  if (!selectedSection.value && sections.value.length > 0) {
    selectSection(sections.value[0]);
  }
};

const selectSection = async (section: TextSection) => {
  selectedSection.value = section;
  editForm.punctuatedText = section.punctuatedText || "";
  editForm.summary = section.summary || "";
  highlightRange.value = null;
  selectedRelation.value = null;
  await loadSectionDetails(section.id);
};

const loadSectionDetails = async (sectionId: number) => {
  const [entityData, relationData, markerData] = await Promise.all([
    listSectionEntities(sectionId),
    listSectionRelations(sectionId),
    listSectionMarkers(sectionId),
  ]);
  entities.value = entityData || [];
  relations.value = relationData || [];
  markers.value = markerData || [];
};

const saveSection = async () => {
  if (!selectedSection.value) return;
  try {
    const updated = await updateSection(selectedSection.value.id, {
      originalText: selectedSection.value.originalText,
      punctuatedText: editForm.punctuatedText,
      summary: editForm.summary,
    });
    selectedSection.value = updated;
    await loadSections();
    notify("Section updated.", "success");
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to update section.", "error");
  }
};

const highlightEntity = (entity: SectionEntity) => {
  highlightRange.value = { start: entity.startOffset, end: entity.endOffset };
  selectedRelation.value = null;
};

const selectRelation = (relation: SectionRelation) => {
  selectedRelation.value = relation;
  const evidence = relation.evidence || "";
  const original = selectedSection.value?.originalText || "";
  const index = evidence ? original.indexOf(evidence) : -1;
  if (index >= 0) {
    highlightRange.value = { start: index, end: index + evidence.length };
  } else {
    highlightRange.value = null;
  }
};

watch(sectionPage, () => {
  loadSections();
});

onMounted(async () => {
  loading.value = true;
  await loadText();
  await loadSections();
  loading.value = false;
});
</script>

<style scoped>
.text-workspace {
  display: grid;
  grid-template-columns: 1.1fr 1.8fr 1.3fr;
  gap: 18px;
}

.panel {
  background: rgba(255, 255, 255, 0.88);
  border-radius: var(--radius-lg);
  padding: 16px;
  border: 1px solid var(--stroke);
  box-shadow: var(--shadow);
}

.panel h2 {
  margin-top: 0;
}

.text-block {
  background: #fff;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-sm);
  padding: 12px;
  line-height: 1.6;
  font-size: 14px;
}

.highlight {
  background: rgba(207, 75, 47, 0.2);
  border-radius: 6px;
  padding: 0 2px;
}

.table tr.active {
  background: rgba(207, 75, 47, 0.08);
}

.table tr {
  cursor: pointer;
}

@media (max-width: 1100px) {
  .text-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
