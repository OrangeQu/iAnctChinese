<template>
  <div class="analysis-panel">
    <!-- 智能分类卡片 -->
    <div class="metric-card classification-card" :class="classification?.suggestedCategory || 'unknown'">
      <div class="card-icon">
        <el-icon><CollectionTag /></el-icon>
      </div>
      <div class="card-content">
        <span class="label">智能归类</span>
        <div class="value-group">
          <span class="value">{{ translateCategory(classification?.suggestedCategory) }}</span>
          <el-tag size="small" effect="dark" round v-if="classification?.confidence">
            {{ Math.round(classification.confidence * 100) }}% 置信度
          </el-tag>
        </div>
        <p class="reason" v-if="classification?.reason">{{ classification.reason }}</p>
      </div>
      <el-button 
        v-if="classification?.suggestedCategory && classification.suggestedCategory !== currentCategory"
        type="primary" 
        link 
        size="small"
        @click="$emit('update-category', classification.suggestedCategory)"
      >
        采纳建议
      </el-button>
    </div>

    <!-- 核心指标 -->
    <div class="metrics-grid">
      <div class="metric-card">
        <span class="label">识别实体</span>
        <strong class="value">{{ stats?.entityCount || 0 }}</strong>
        <div class="mini-chart entity-bg"></div>
      </div>
      <div class="metric-card">
        <span class="label">构建关系</span>
        <strong class="value">{{ stats?.relationCount || 0 }}</strong>
        <div class="mini-chart relation-bg"></div>
      </div>
      <div class="metric-card">
        <span class="label">句读进度</span>
        <strong class="value">{{ Math.round((stats?.punctuationProgress || 0) * 100) }}%</strong>
        <el-progress :percentage="Math.round((stats?.punctuationProgress || 0) * 100)" :show-text="false" stroke-width="4" color="#6B8E23" />
      </div>
    </div>

    <!-- 词云区域 -->
    <div class="visualization-card" v-if="words && words.length">
       <h4 class="viz-title">高频词云</h4>
       <!-- 简单的词云展示，实际项目中可替换为 Canvas -->
       <div class="word-cloud-simple">
         <span 
            v-for="(word, index) in displayWords" 
            :key="index"
            :style="{ 
              fontSize: `${Math.max(12, Math.min(24, word.value / 2))}px`,
              opacity: 0.6 + (word.value / 100),
              color: getColor(index)
            }"
          >
            {{ word.name }}
         </span>
       </div>
    </div>

    <!-- AI 分析摘要 -->
    <div class="analysis-summary" v-if="analysisSummary">
      <h4 class="viz-title">AI 深度解读</h4>
      <p>{{ analysisSummary }}</p>
    </div>
    <div v-else class="empty-state">
      <el-empty description="暂无分析数据，请点击“全量分析”" image-size="80" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { CollectionTag } from '@element-plus/icons-vue';

const props = defineProps({
  classification: Object,
  currentCategory: String,
  stats: Object,
  words: Array,
  analysisSummary: String
});

defineEmits(['update-category']);

const displayWords = computed(() => {
  return (props.words || []).slice(0, 20);
});

const translateCategory = (c) => {
  const map = { warfare: "战争纪实", travelogue: "游记地理", biography: "人物传记", unknown: "待识别" };
  return map[c] || "未知类型";
};

const colors = ['#3E4E5E', '#C04851', '#6B8E23', '#D4A017', '#5A6D7E'];
const getColor = (i) => colors[i % colors.length];

</script>

<style scoped>
.analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-card {
  background: rgba(255, 255, 255, 0.6);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid rgba(0,0,0,0.05);
  position: relative;
  overflow: hidden;
}

.classification-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(135deg, #fff 0%, #f3f4f6 100%);
}

.classification-card.warfare { border-left: 4px solid #C04851; }
.classification-card.travelogue { border-left: 4px solid #6B8E23; }
.classification-card.biography { border-left: 4px solid #3E4E5E; }

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0,0,0,0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--primary-color);
}

.card-content {
  flex: 1;
}

.label {
  font-size: 12px;
  color: var(--text-tertiary);
  display: block;
  margin-bottom: 4px;
}

.value-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.reason {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.4;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metrics-grid .metric-card {
  text-align: center;
  padding: 12px;
}

.metrics-grid .value {
  font-size: 24px;
  margin: 8px 0;
  display: block;
}

.visualization-card, .analysis-summary {
  background: rgba(255, 255, 255, 0.6);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid rgba(0,0,0,0.05);
}

.viz-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-secondary);
}

.word-cloud-simple {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  padding: 10px;
}

.analysis-summary p {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
  text-align: justify;
}
</style>

