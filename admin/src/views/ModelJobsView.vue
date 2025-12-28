<template>
  <div class="section" v-if="loading">Loading model jobs...</div>
  <template v-else>
    <div class="section" v-if="texts.length === 0">
      <div class="banner">No texts available for model jobs.</div>
    </div>
    <template v-else>
      <div class="section">
        <div class="grid-two">
          <div class="form-card">
            <TextSelector label="Text" :texts="texts" v-model="selectedTextId" />
          </div>
          <div class="form-card">
            <h2>Enqueue Job</h2>
            <form @submit.prevent="handleEnqueue">
              <div class="row">
                <label>Job Type</label>
                <select class="select" v-model="jobForm.jobType">
                  <option value="TYPE_CLASSIFICATION">TYPE_CLASSIFICATION</option>
                  <option value="ENTITY_EXTRACTION">ENTITY_EXTRACTION</option>
                  <option value="RELATION_EXTRACTION">RELATION_EXTRACTION</option>
                  <option value="PUNCTUATION">PUNCTUATION</option>
                  <option value="SUMMARY">SUMMARY</option>
                </select>
              </div>
              <div class="row">
                <label>Payload (optional)</label>
                <textarea v-model="jobForm.payload" rows="3"></textarea>
              </div>
              <button class="button secondary" type="submit">Enqueue</button>
              <div class="toast" v-if="error">{{ error }}</div>
            </form>
          </div>
        </div>
      </div>
      <div class="section">
        <h2>Jobs</h2>
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Type</th>
              <th>Status</th>
              <th>Created</th>
              <th>Completed</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="jobs.length === 0">
              <td colspan="5">No jobs yet.</td>
            </tr>
            <tr v-for="job in jobs" :key="job.id">
              <td>#{{ job.id }}</td>
              <td>{{ job.jobType }}</td>
              <td>{{ job.status }}</td>
              <td>{{ formatDate(job.createdAt) }}</td>
              <td>{{ formatDate(job.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import TextSelector from "@/components/TextSelector.vue";
import { useAppStore } from "@/stores/app";
import { listTexts, type TextDocument } from "@/services/api/texts";
import {
  listJobs,
  enqueueJob,
  type JobType,
  type ModelJob,
} from "@/services/api/modelJobs";

const appStore = useAppStore();

const texts = ref<TextDocument[]>([]);
const jobs = ref<ModelJob[]>([]);
const loading = ref(true);
const error = ref("");

const selectedTextId = computed({
  get: () => appStore.selectedTextId,
  set: (value) => appStore.setSelectedTextId(value),
});

const jobForm = reactive({
  jobType: "TYPE_CLASSIFICATION" as JobType,
  payload: "",
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
};

const loadJobs = async () => {
  if (!selectedTextId.value) {
    jobs.value = [];
    return;
  }
  jobs.value = await listJobs(selectedTextId.value);
};

const loadAll = async () => {
  loading.value = true;
  await loadTexts();
  await loadJobs();
  loading.value = false;
};

const handleEnqueue = async () => {
  if (!selectedTextId.value) return;
  error.value = "";
  try {
    await enqueueJob({
      textId: selectedTextId.value,
      jobType: jobForm.jobType,
      payload: jobForm.payload || undefined,
    });
    jobForm.payload = "";
    await loadJobs();
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || "Failed to enqueue.";
  }
};

watch(
  () => selectedTextId.value,
  () => {
    loadJobs();
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
