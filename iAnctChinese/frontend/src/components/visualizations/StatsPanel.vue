<template>
  <section class="panel stats-panel">
    <h3 class="section-title">词频词云</h3>
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

const props = defineProps({
  words: {
    type: Array,
    default: () => []
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
