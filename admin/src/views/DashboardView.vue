<template>
  <div class="section" v-if="loading">Loading overview...</div>
  <template v-else>
    <div class="section" v-if="texts.length === 0">
      <div class="banner">No texts yet. Create a text record first.</div>
    </div>
    <template v-else>
      <div class="section">
        <div class="grid-two">
          <div class="form-card">
            <TextSelector
              label="Current text"
              :texts="texts"
              v-model="selectedTextId"
            />
          </div>
          <div class="form-card">
            <div class="row">
              <label>Status</label>
              <div class="pill">{{ overview?.status || "Unknown" }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="cards stagger">
        <div class="card">
          <h3>{{ overview?.text?.title || "-" }}</h3>
          <p>Selected text title</p>
        </div>
        <div class="card alt">
          <h3>{{ overview?.entityCount ?? 0 }}</h3>
          <p>Entity annotations</p>
        </div>
        <div class="card">
          <h3>{{ overview?.relationCount ?? 0 }}</h3>
          <p>Relation annotations</p>
        </div>
        <div class="card alt">
          <h3>{{ overview?.text?.category || "-" }}</h3>
          <p>Category</p>
        </div>
      </div>
    </template>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useAppStore } from "@/stores/app";
import TextSelector from "@/components/TextSelector.vue";
import { listTexts, type TextDocument } from "@/services/api/texts";
import { getOverview, type DashboardOverview } from "@/services/api/dashboard";

const appStore = useAppStore();

const texts = ref<TextDocument[]>([]);
const overview = ref<DashboardOverview | null>(null);
const loading = ref(true);

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

const loadOverview = async () => {
  if (!selectedTextId.value) {
    overview.value = null;
    return;
  }
  overview.value = await getOverview(selectedTextId.value);
};

const loadAll = async () => {
  loading.value = true;
  await loadTexts();
  await loadOverview();
  loading.value = false;
};

watch(
  () => selectedTextId.value,
  () => {
    loadOverview();
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
