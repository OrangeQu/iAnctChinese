<template>
  <div class="section">
    <FilterBar>
      <input class="input" v-model="categoryFilter" placeholder="Text category" />
      <button class="button secondary" type="button" @click="loadPresets">Search</button>
    </FilterBar>
  </div>

  <div class="section grid-two">
    <div class="form-card">
      <h2>{{ editingId ? "Edit Preset" : "Create Preset" }}</h2>
      <div class="row">
        <label>Label</label>
        <input class="input" v-model="form.label" />
      </div>
      <div class="row">
        <label>Text Category</label>
        <input class="input" v-model="form.textCategory" />
      </div>
      <div class="row">
        <label class="checkbox">
          <input type="checkbox" v-model="form.isDefault" />
          Set as default for category
        </label>
      </div>
      <div class="row">
        <label>Config JSON</label>
        <textarea class="textarea" v-model="configText" rows="10" />
        <div class="json-actions">
          <button class="button secondary" type="button" @click="formatJson">Format</button>
          <span class="json-error" v-if="jsonError">{{ jsonError }}</span>
        </div>
      </div>
      <div class="actions">
        <button class="button primary" type="button" @click="savePreset">
          {{ editingId ? "Update" : "Create" }}
        </button>
        <button v-if="editingId" class="button ghost" type="button" @click="resetForm">
          Cancel
        </button>
      </div>
    </div>

    <div class="form-card">
      <h2>Preview</h2>
      <JsonViewer v-if="configPreview" :value="configPreview" />
      <div v-else class="banner">Provide valid JSON to preview.</div>
    </div>
  </div>

  <div class="section">
    <h2>Presets</h2>
    <DataTable>
      <template #head>
        <th>Label</th>
        <th>Category</th>
        <th>Default</th>
        <th>Config</th>
        <th>Actions</th>
      </template>
      <tr v-if="presets.length === 0">
        <td colspan="5">No presets.</td>
      </tr>
      <tr v-for="preset in presets" :key="preset.id">
        <td>{{ preset.label }}</td>
        <td>{{ preset.textCategory }}</td>
        <td>{{ preset.isDefault ? "Yes" : "No" }}</td>
        <td>
          <JsonViewer :value="safeParse(preset.configJson)" />
        </td>
        <td>
          <button class="button secondary" type="button" @click="editPreset(preset)">Edit</button>
          <button class="button ghost" type="button" @click="copyPreset(preset)">Copy</button>
          <button class="button ghost" type="button" @click="toggleDefault(preset)">Set Default</button>
          <button class="button primary" type="button" @click="deletePresetItem(preset.id)">
            Delete
          </button>
        </td>
      </tr>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import JsonViewer from "@/components/JsonViewer.vue";
import { notify } from "@/services/toast";
import {
  createPreset,
  deletePreset,
  listPresets,
  updatePreset,
  type VisualizationPreset,
} from "@/services/api/presets";

const presets = ref<VisualizationPreset[]>([]);
const categoryFilter = ref("");
const editingId = ref<number | null>(null);
const configText = ref("{\n  \n}");
const jsonError = ref("");

const form = reactive({
  label: "",
  textCategory: "",
  isDefault: false,
});

const configPreview = computed(() => {
  try {
    if (!configText.value.trim()) return null;
    return JSON.parse(configText.value);
  } catch (err) {
    return null;
  }
});

const safeParse = (value?: string | null) => {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch (err) {
    return value;
  }
};

const formatJson = () => {
  try {
    const parsed = JSON.parse(configText.value || "{}");
    configText.value = JSON.stringify(parsed, null, 2);
    jsonError.value = "";
  } catch (err: any) {
    jsonError.value = "Invalid JSON";
  }
};

const resetForm = () => {
  editingId.value = null;
  form.label = "";
  form.textCategory = "";
  form.isDefault = false;
  configText.value = "{\n  \n}";
  jsonError.value = "";
};

const loadPresets = async () => {
  try {
    presets.value = await listPresets(categoryFilter.value || undefined);
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to load presets.", "error");
  }
};

const savePreset = async () => {
  if (!form.label.trim() || !form.textCategory.trim()) {
    notify("Label and category are required.", "error");
    return;
  }
  try {
    const parsed = JSON.parse(configText.value || "{}");
    const payload = {
      label: form.label.trim(),
      textCategory: form.textCategory.trim(),
      configJson: JSON.stringify(parsed),
      isDefault: form.isDefault,
    };
    if (editingId.value) {
      await updatePreset(editingId.value, payload);
      notify("Preset updated.", "success");
    } else {
      await createPreset(payload);
      notify("Preset created.", "success");
    }
    resetForm();
    await loadPresets();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to save preset.", "error");
  }
};

const editPreset = (preset: VisualizationPreset) => {
  editingId.value = preset.id;
  form.label = preset.label;
  form.textCategory = preset.textCategory;
  form.isDefault = Boolean(preset.isDefault);
  configText.value = preset.configJson || "{\n  \n}";
  jsonError.value = "";
};

const copyPreset = async (preset: VisualizationPreset) => {
  try {
    await createPreset({
      label: `${preset.label} (copy)`,
      textCategory: preset.textCategory,
      configJson: preset.configJson || "{}",
      isDefault: false,
    });
    await loadPresets();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to copy preset.", "error");
  }
};

const toggleDefault = async (preset: VisualizationPreset) => {
  try {
    await updatePreset(preset.id, {
      label: preset.label,
      textCategory: preset.textCategory,
      configJson: preset.configJson || "{}",
      isDefault: true,
    });
    await loadPresets();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to set default.", "error");
  }
};

const deletePresetItem = async (id: number) => {
  try {
    await deletePreset(id);
    await loadPresets();
  } catch (err: any) {
    notify(err?.response?.data?.message || err?.message || "Failed to delete preset.", "error");
  }
};

onMounted(loadPresets);
</script>

<style scoped>
.grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.textarea {
  width: 100%;
  border-radius: 12px;
  padding: 10px;
  border: 1px solid var(--stroke);
  background: rgba(255, 255, 255, 0.8);
  resize: vertical;
}

.json-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.json-error {
  color: #d64545;
  font-size: 12px;
}

.actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 1080px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
