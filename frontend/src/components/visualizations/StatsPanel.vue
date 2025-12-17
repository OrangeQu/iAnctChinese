<template>
  <section class="panel stats-panel">
    <div class="section-header">
      <h3 class="section-title">词频词云</h3>
      <el-button size="small" type="primary" plain :loading="wordLoading" @click="$emit('fetch-word-cloud')">
        生成词云
      </el-button>
    </div>
    <WordCloudCanvas :words="words" />
    <el-divider />
    <h3 class="section-title">统计信息</h3>
    <ul>
      <li>实体数：{{ stats?.entityCount ?? 0 }}</li>
      <li>关系数：{{ stats?.relationCount ?? 0 }}</li>
      <li>句读完成度：{{ Math.round(((stats?.punctuationProgress) ?? 0) * 100) }}%</li>
    </ul>
    <p class="analysis">{{ analysisSummary }}</p>
  </section>
</template>

<script setup>
import WordCloudCanvas from "./WordCloudCanvas.vue";

defineEmits(["fetch-word-cloud"]);

const props = defineProps({
  words: {
    type: Array,
    default: () => []
  },
  wordLoading: {
    type: Boolean,
    default: false
  },
  stats: {
    type: Object,
    default: () => ({
      entityCount: 0,
      relationCount: 0,
      punctuationProgress: 0
    })
  },
  analysisSummary: {
    type: String,
    default: ""
  }
});

</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
ul {
  list-style: none;
  padding-left: 0;
  margin: 0;
  color: var(--muted);
}
</style>
.analysis {
  margin-top: 12px;
  color: var(--muted);
  line-height: 1.6;
}
