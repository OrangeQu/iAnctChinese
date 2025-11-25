<template>
  <section class="panel sentence-panel">
    <div class="panel-header">
      <h3 class="section-title">句读与结构</h3>
      <div class="actions">
        <el-button size="small" type="primary" @click="$emit('auto-segment')">自动句读</el-button>
      </div>
    </div>
    <el-table :data="localSections" height="320" border size="small">
      <el-table-column label="次序" width="60">
        <template #default="scope">
          {{ scope.row.sequenceIndex }}
        </template>
      </el-table-column>
      <el-table-column label="原文" min-width="200">
        <template #default="scope">
          <el-input
            v-model="scope.row.originalText"
            type="textarea"
            autosize
            placeholder="原文"
          />
        </template>
      </el-table-column>
      <el-table-column label="句读" min-width="200">
        <template #default="scope">
          <el-input
            v-model="scope.row.punctuatedText"
            type="textarea"
            autosize
            placeholder="句读结果"
          />
        </template>
      </el-table-column>
      <el-table-column label="摘要" min-width="160">
        <template #default="scope">
          <el-input v-model="scope.row.summary" placeholder="结构摘要" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="scope">
          <el-button
            size="small"
            type="success"
            @click="saveSection(scope.row)"
            :loading="saveLoading === scope.row.id"
          >
            保存
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  sections: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["auto-segment", "update-section"]);

const localSections = ref([]);
const saveLoading = ref(null);

watch(
  () => props.sections,
  (next) => {
    localSections.value = next.map((section) => ({ ...section }));
  },
  { immediate: true, deep: true }
);

const saveSection = async (section) => {
  saveLoading.value = section.id;
  await emit("update-section", section);
  saveLoading.value = null;
};
</script>

<style scoped>
.sentence-panel {
  margin-top: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
</style>
