<template>
  <div class="dashboard-container">
    <!-- Header Section -->
    <section class="welcome-section">
      <div class="welcome-text">
        <h1>早安, {{ authStore.user?.username || '研究员' }}</h1>
        <p>今天准备探索哪段历史？</p>
      </div>
      <div class="quick-actions">
        <el-button class="upload-btn" type="primary" size="large" @click="openUploadDrawer">
          <el-icon class="mr-2"><Plus /></el-icon>
          上传古籍
        </el-button>
      </div>
    </section>

    <!-- Stats Grid (Bento Style) -->
    <section class="stats-grid">
      <!-- Card 1: Total Texts (Highlight) -->
      <div class="bento-card highlight-card">
        <div class="card-content">
          <span class="label">收录典籍</span>
          <div class="value-row">
            <span class="value">{{ store.texts.length }}</span>
            <span class="unit">部</span>
          </div>
          <div class="decoration-circle"></div>
        </div>
      </div>

      <!-- Card 2: Entities (Dark) -->
      <div class="bento-card dark-card">
        <div class="card-content">
          <div class="icon-wrapper">
            <el-icon><Connection /></el-icon>
          </div>
          <span class="value">{{ totalEntities }}</span>
          <span class="label">识别实体总数</span>
        </div>
      </div>

      <!-- Card 3: Relations (Light) -->
      <div class="bento-card light-card">
        <div class="card-content">
          <div class="top-row">
            <span class="label">构建关系</span>
            <el-icon class="icon-small"><Share /></el-icon>
          </div>
          <span class="value">{{ totalRelations }}</span>
          <div class="progress-bar">
            <div class="fill" style="width: 75%"></div>
          </div>
        </div>
      </div>

      <!-- Card 4: Research Time (Light) -->
      <div class="bento-card light-card">
        <div class="card-content">
          <div class="top-row">
            <span class="label">研究时长</span>
            <el-icon class="icon-small"><Timer /></el-icon>
          </div>
          <span class="value">{{ workHours }}<small>h</small></span>
        </div>
      </div>
    </section>

    <!-- Main Content Grid -->
    <div class="main-grid">
      <!-- Recent Documents -->
      <section class="recent-docs card">
        <div class="section-header">
          <h3>最近编辑</h3>
          <el-button link type="primary" @click="router.push('/documents')">查看全部</el-button>
        </div>
        
        <div class="docs-list">
          <div 
            v-for="text in recentTexts" 
            :key="text.id" 
            class="doc-item"
            @click="openText(text.id)"
          >
            <div class="doc-icon-wrapper" :class="text.category || 'unknown'">
              <span class="doc-type-letter">{{ (text.category || '文').charAt(0).toUpperCase() }}</span>
            </div>
            <div class="doc-info">
              <h4>{{ text.title }}</h4>
              <p>{{ text.author || '佚名' }} · {{ text.era || '未知' }}</p>
            </div>
            <div class="doc-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
          
          <div v-if="recentTexts.length === 0" class="empty-state">
            <el-empty description="暂无编辑记录" :image-size="60" />
          </div>
        </div>
      </section>

      <!-- Analysis Status (Dark Panel) -->
      <section class="analysis-status card-dark">
        <div class="section-header">
          <h3>系统状态</h3>
          <div class="status-dot"></div>
        </div>
        <div class="progress-list">
          <div class="progress-item">
            <div class="progress-info">
              <span>NER 模型加载</span>
              <span class="percentage">100%</span>
            </div>
            <el-progress :percentage="100" :show-text="false" color="#06D6A0" />
          </div>
          <div class="progress-item">
            <div class="progress-info">
              <span>RE 任务队列</span>
              <span class="percentage">空闲</span>
            </div>
            <el-progress :percentage="0" :show-text="false" color="#FFD166" />
          </div>
        </div>
        
        <div class="daily-quote">
          <p>"温故而知新，可以为师矣。"</p>
        </div>
      </section>
    </div>

    <TextUploadDrawer ref="uploadDrawer" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useTextStore } from "@/store/textStore";
import { useAuthStore } from "@/store/authStore";
import { Document, Connection, Share, Timer, Plus, ArrowRight } from "@element-plus/icons-vue";
import TextUploadDrawer from "@/components/layout/TextUploadDrawer.vue";

const router = useRouter();
const store = useTextStore();
const authStore = useAuthStore();
const uploadDrawer = ref(null);

const openUploadDrawer = () => {
  uploadDrawer.value?.open();
};

onMounted(async () => {
  if (store.texts.length === 0) {
    await store.initDashboard();
  }
});

const recentTexts = computed(() => {
  return [...store.texts]
    .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0))
    .slice(0, 4);
});

const totalEntities = computed(() => 1240);
const totalRelations = computed(() => 856);
const workHours = ref(12.5);

const openText = (id) => {
  router.push(`/texts/${id}`);
};
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 8px;
}

.welcome-text h1 {
  font-size: 36px;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.welcome-text p {
  color: var(--text-secondary);
  font-size: 16px;
}

.upload-btn {
  border-radius: 50px;
  padding: 20px 32px;
  font-size: 16px;
  box-shadow: 0 8px 20px rgba(31, 33, 37, 0.2);
}

/* Bento Grid Stats */
.stats-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1fr;
  gap: 20px;
  height: 180px;
}

.bento-card {
  border-radius: 24px;
  padding: 24px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: transform 0.2s ease;
}

.bento-card:hover {
  transform: translateY(-4px);
}

.highlight-card {
  background: linear-gradient(135deg, #FFD166 0%, #FFC045 100%);
  color: #1A1A1A;
}

.highlight-card .label {
  font-size: 16px;
  font-weight: 600;
  opacity: 0.8;
}

.highlight-card .value {
  font-size: 48px;
  font-weight: 800;
  line-height: 1;
}

.highlight-card .unit {
  font-size: 18px;
  font-weight: 600;
  margin-left: 4px;
  opacity: 0.6;
}

.decoration-circle {
  position: absolute;
  width: 120px;
  height: 120px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  top: -20px;
  right: -20px;
  filter: blur(20px);
}

.dark-card {
  background: #1F2125;
  color: #FFFFFF;
}

.dark-card .icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 16px;
}

.dark-card .value {
  font-size: 32px;
  font-weight: 700;
}

.dark-card .label {
  font-size: 13px;
  color: #888;
}

.light-card {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.03);
  box-shadow: var(--shadow-sm);
}

.light-card .top-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.light-card .label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.light-card .value {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-primary);
}

.light-card .value small {
  font-size: 16px;
  color: var(--text-tertiary);
  margin-left: 4px;
}

.progress-bar {
  height: 6px;
  background: #F0F0F0;
  border-radius: 3px;
  margin-top: 16px;
  overflow: hidden;
}

.progress-bar .fill {
  height: 100%;
  background: var(--text-primary);
  border-radius: 3px;
}

/* Main Grid */
.main-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
}

.recent-docs {
  min-height: 400px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-header h3 {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
}

.docs-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px;
  background: #F9F9F9;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.doc-item:hover {
  background: #FFFFFF;
  box-shadow: 0 8px 24px rgba(0,0,0,0.05);
  transform: scale(1.01);
}

.doc-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 20px;
}

.doc-icon-wrapper.warfare { background: #FFE8E8; color: #FF6B6B; }
.doc-icon-wrapper.travelogue { background: #E8FDF5; color: #06D6A0; }
.doc-icon-wrapper.biography { background: #E8F4FF; color: #118AB2; }
.doc-icon-wrapper.unknown { background: #F0F0F0; color: #999; }

.doc-info h4 {
  font-size: 16px;
  margin: 0 0 4px 0;
}

.doc-info p {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}

.doc-arrow {
  margin-left: auto;
  color: var(--text-tertiary);
}

/* Analysis Status */
.analysis-status {
  border-radius: 24px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.card-dark {
  background: #1F2125;
  color: #FFF;
}

.card-dark .section-header h3 {
  color: #FFF;
}

.status-dot {
  width: 10px;
  height: 10px;
  background: #06D6A0;
  border-radius: 50%;
  box-shadow: 0 0 10px #06D6A0;
}

.progress-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #AAA;
}

.daily-quote {
  margin-top: auto;
  padding-top: 32px;
  border-top: 1px solid rgba(255,255,255,0.1);
  font-style: italic;
  color: #888;
  text-align: center;
}
</style>
