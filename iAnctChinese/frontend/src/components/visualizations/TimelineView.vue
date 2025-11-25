<template>
  <div class="timeline-container" :class="`timeline-${category}`">
    <!-- 装饰背景 -->
    <div class="timeline-decorations">
      <div class="decoration-circle deco-1"></div>
      <div class="decoration-circle deco-2"></div>
      <div class="decoration-circle deco-3"></div>
      <div class="decoration-line line-1"></div>
      <div class="decoration-line line-2"></div>
    </div>

    <!-- 空状态提示 -->
    <div v-if="!milestones || milestones.length === 0" class="empty-state">
      <el-icon :size="80" color="#ccc"><Calendar /></el-icon>
      <p class="empty-title">暂无时间轴数据</p>
      <p class="empty-desc">请先在"分析"页面运行文本分析，生成时间轴事件</p>
    </div>

    <!-- 时间轴主体 -->
    <div v-else class="timeline-main">
      <!-- 中央水平时间轴 -->
      <div class="timeline-axis">
        <div class="axis-line" :style="axisLineStyle"></div>

        <!-- 事件节点 -->
        <div
          v-for="(milestone, index) in milestones"
          :key="milestone.title + index"
          class="event-node"
          :style="getNodePosition(index)"
          @click="selectEvent(milestone, index)"
        >
          <!-- 节点图标 -->
          <div
            class="node-icon"
            :class="[`icon-${milestone.eventType || 'default'}`, { active: selectedIndex === index }]"
            :style="{
              borderColor: pickColor(index),
              background: `linear-gradient(135deg, ${pickColor(index)}15, ${pickColor(index)}30)`,
              color: pickColor(index)
            }"
          >
            <component :is="getIconComponent(milestone.eventType)" />
          </div>

          <!-- 节点标签（只显示一个） -->
          <div
            class="node-label"
            :style="{ color: pickColor(index) }"
          >
            <span v-if="milestone.dateLabel && milestone.dateLabel !== '未注明'">
              {{ milestone.dateLabel }}
            </span>
            <span v-else style="opacity: 0.6;">
              {{ `第${index + 1}节` }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 下方滑出的信息卡片 -->
    <transition name="slide-up">
      <div v-if="selectedMilestone" class="info-card" :style="{ borderTopColor: pickColor(selectedIndex) }">
        <div class="card-header">
          <div class="card-title">
            <component
              :is="getIconComponent(selectedMilestone.eventType)"
              class="title-icon"
              :style="{ color: pickColor(selectedIndex) }"
            />
            <h3>{{ selectedMilestone.title }}</h3>
          </div>
          <el-button type="text" @click="closeCard" class="close-btn">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <div class="card-body">
          <!-- 基本信息网格 -->
          <div class="info-grid">
            <!-- 主要标注（根据类型显示时间/地点/阶段） -->
            <div class="info-item" :style="{ borderLeftColor: pickColor(selectedIndex) }">
              <div class="info-icon" :style="{ color: pickColor(selectedIndex) }">
                <el-icon>
                  <Calendar v-if="category === 'biography'" />
                  <MapLocation v-else-if="category === 'travelogue'" />
                  <Flag v-else-if="category === 'warfare'" />
                  <Calendar v-else />
                </el-icon>
              </div>
              <div class="info-content">
                <div class="info-label">{{ getDateLabelText() }}</div>
                <div class="info-value">{{ selectedMilestone.dateLabel || '未注明' }}</div>
              </div>
            </div>

            <!-- 地点信息 -->
            <div class="info-item" :style="{ borderLeftColor: pickColor(selectedIndex) }">
              <div class="info-icon" :style="{ color: pickColor(selectedIndex) }">
                <el-icon><Location /></el-icon>
              </div>
              <div class="info-content">
                <div class="info-label">地点</div>
                <div class="info-value">{{ selectedMilestone.location || '未注明' }}</div>
              </div>
            </div>

            <!-- 重要程度 -->
            <div class="info-item full-width" :style="{ borderLeftColor: pickColor(selectedIndex) }">
              <div class="info-icon" :style="{ color: pickColor(selectedIndex) }">
                <el-icon><Star /></el-icon>
              </div>
              <div class="info-content">
                <div class="info-label">重要程度</div>
                <div class="info-value">
                  <el-rate
                    v-model="selectedMilestone.significance"
                    disabled
                    :max="10"
                    :colors="[pickColor(selectedIndex), pickColor(selectedIndex), pickColor(selectedIndex)]"
                    class="significance-rate"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- 原文摘要 -->
          <div class="section">
            <div class="section-header">
              <el-icon :style="{ color: pickColor(selectedIndex) }"><Document /></el-icon>
              <span>原文摘要</span>
            </div>
            <div class="section-content description">
              {{ selectedMilestone.description }}
            </div>
          </div>

          <!-- 相关人物 -->
          <div class="section">
            <div class="section-header">
              <el-icon :style="{ color: pickColor(selectedIndex) }"><User /></el-icon>
              <span>相关人物</span>
            </div>
            <div class="section-content">
              <div class="tags" v-if="selectedMilestone.participants && selectedMilestone.participants.length > 0">
                <el-tag
                  v-for="person in selectedMilestone.participants"
                  :key="person"
                  :style="{ background: pickColor(selectedIndex), borderColor: pickColor(selectedIndex) }"
                  class="person-tag"
                >
                  {{ person }}
                </el-tag>
              </div>
              <div v-else class="no-data">暂无相关人物信息</div>
            </div>
          </div>

          <!-- 历史影响 -->
          <div class="section">
            <div class="section-header">
              <el-icon :style="{ color: pickColor(selectedIndex) }"><TrendCharts /></el-icon>
              <span>历史影响</span>
            </div>
            <div class="section-content impact">
              {{ selectedMilestone.impact || '暂无影响说明' }}
            </div>
          </div>

          <!-- 导航和操作按钮 -->
          <div class="card-footer">
            <div class="footer-nav">
              <el-button
                @click="prevEvent"
                :disabled="selectedIndex === 0"
                :style="{ borderColor: pickColor(selectedIndex), color: pickColor(selectedIndex) }"
              >
                <el-icon><ArrowLeft /></el-icon>
                上一个事件
              </el-button>

              <div class="page-indicator">
                {{ selectedIndex + 1 }} / {{ milestones.length }}
              </div>

              <el-button
                @click="nextEvent"
                :disabled="selectedIndex === milestones.length - 1"
                :style="{ borderColor: pickColor(selectedIndex), color: pickColor(selectedIndex) }"
              >
                下一个事件
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>

            <el-button
              type="primary"
              @click="jumpToOriginalText"
              v-if="selectedMilestone.entityId || selectedMilestone.startOffset"
              class="view-original-btn"
            >
              <el-icon><Document /></el-icon>
              查看原文位置
            </el-button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 类型说明 -->
    <div v-if="milestones && milestones.length > 0" class="timeline-legend">
      <div class="legend-title">{{ getCategoryLabel() }}</div>
      <div class="legend-desc">{{ getCategoryDescription() }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import {
  ArrowLeft,
  ArrowRight,
  Close,
  Location,
  User,
  Calendar,
  Star,
  Document,
  TrendCharts,
  Flag,
  MapLocation,
  Trophy,
  Promotion,
  Picture,
  Finished,
  HomeFilled
} from '@element-plus/icons-vue';

const props = defineProps({
  milestones: {
    type: Array,
    default: () => []
  },
  category: {
    type: String,
    default: 'unknown'
  }
});

const emit = defineEmits(['jumpToText']);

const selectedMilestone = ref(null);
const selectedIndex = ref(-1);

// 颜色主题配置
const colorThemes = {
  biography: ['#c45850', '#d4736e', '#e68b86', '#f5a39d'],
  travelogue: ['#4a90a4', '#5fa3b5', '#75b5c5', '#8dc7d6'],
  warfare: ['#8b5a3c', '#a06f4d', '#b4845f', '#c89972'],
  unknown: ['#6b8e99', '#7d9fa9', '#8fb0b9', '#a1c1c9']
};

// 根据类别获取颜色
const pickColor = (index) => {
  const theme = colorThemes[props.category] || colorThemes.unknown;
  return theme[index % theme.length];
};

// 时间轴线样式
const axisLineStyle = computed(() => {
  const theme = colorThemes[props.category] || colorThemes.unknown;
  return {
    background: `linear-gradient(90deg, ${theme[0]}, ${theme[theme.length - 1]})`
  };
});

// 获取节点位置
const getNodePosition = (index) => {
  const total = props.milestones.length;
  const spacing = 100 / (total + 1);
  const left = spacing * (index + 1);
  return {
    left: `${left}%`
  };
};

// 获取图标组件
const getIconComponent = (eventType) => {
  const iconMap = {
    birth: HomeFilled,
    official: Promotion,
    death: Flag,
    achievement: Trophy,
    life: User,
    travel: MapLocation,
    scenery: Picture,
    battle: Finished,
    military: Flag,
    default: Star
  };
  return iconMap[eventType] || iconMap.default;
};

// 选择事件
const selectEvent = (milestone, index) => {
  selectedMilestone.value = milestone;
  selectedIndex.value = index;
};

// 跳转到原文
const jumpToOriginalText = () => {
  if (selectedMilestone.value) {
    const milestone = selectedMilestone.value;
    if (milestone.entityId || (milestone.startOffset !== undefined && milestone.startOffset !== null)) {
      emit('jumpToText', {
        entityId: milestone.entityId,
        startOffset: milestone.startOffset,
        endOffset: milestone.endOffset
      });
    }
  }
};

// 关闭卡片
const closeCard = () => {
  selectedMilestone.value = null;
  selectedIndex.value = -1;
};

// 上一个事件
const prevEvent = () => {
  if (selectedIndex.value > 0) {
    selectEvent(props.milestones[selectedIndex.value - 1], selectedIndex.value - 1);
  }
};

// 下一个事件
const nextEvent = () => {
  if (selectedIndex.value < props.milestones.length - 1) {
    selectEvent(props.milestones[selectedIndex.value + 1], selectedIndex.value + 1);
  }
};

// 获取类别标签
const getCategoryLabel = () => {
  const labels = {
    biography: '人物传记时间轴',
    travelogue: '游记地理时间轴',
    warfare: '战争纪实时间轴',
    unknown: '文献时间轴'
  };
  return labels[props.category] || labels.unknown;
};

// 获取类别描述
const getCategoryDescription = () => {
  const descriptions = {
    biography: '展示人物生平的重要节点，从出生、任官到成就，勾勒完整的人生轨迹',
    travelogue: '记录行旅路线的关键地点，从启程、途经到抵达，呈现完整的游历过程',
    warfare: '呈现战事发展的关键时刻，从集结、交战到结局，展现战争全貌',
    unknown: '展示文献中的重要事件节点，按时间顺序呈现历史脉络'
  };
  return descriptions[props.category] || descriptions.unknown;
};

// 获取时间标签文本
const getDateLabelText = () => {
  const labelTexts = {
    biography: '时间',
    travelogue: '地点标注',
    warfare: '战事阶段',
    unknown: '时间'
  };
  return labelTexts[props.category] || '时间';
};
</script>

<style scoped>
.timeline-container {
  position: relative;
  min-height: 500px;
  padding: 40px 20px;
  background:
    radial-gradient(circle at 20% 80%, rgba(209, 106, 93, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(165, 137, 56, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(74, 144, 164, 0.06) 0%, transparent 50%),
    linear-gradient(135deg, #fdfbf7 0%, #f8f6f2 50%, #f5f2ed 100%);
  border-radius: 20px;
  overflow: hidden;
  box-shadow:
    inset 0 0 100px rgba(0, 0, 0, 0.03),
    inset 0 0 40px rgba(165, 137, 56, 0.05),
    0 8px 32px rgba(0, 0, 0, 0.08);
}

/* 装饰元素 */
.timeline-decorations {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.3;
  overflow: hidden;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(50px);
  animation: float 20s ease-in-out infinite;
}

.deco-1 {
  width: 350px;
  height: 350px;
  top: -120px;
  left: -120px;
  background: radial-gradient(circle, rgba(209, 106, 93, 0.5), rgba(242, 183, 175, 0.3) 50%, transparent 70%);
  animation-delay: 0s;
}

.deco-2 {
  width: 240px;
  height: 240px;
  bottom: -60px;
  right: 20%;
  background: radial-gradient(circle, rgba(165, 137, 56, 0.5), rgba(219, 198, 130, 0.3) 50%, transparent 70%);
  animation-delay: 7s;
}

.deco-3 {
  width: 280px;
  height: 280px;
  top: 30%;
  right: -100px;
  background: radial-gradient(circle, rgba(74, 144, 164, 0.5), rgba(157, 204, 217, 0.3) 50%, transparent 70%);
  animation-delay: 14s;
}

.decoration-line {
  position: absolute;
  height: 3px;
  background: linear-gradient(90deg, transparent, rgba(165, 137, 56, 0.4), transparent);
  filter: blur(2px);
  box-shadow: 0 0 10px rgba(165, 137, 56, 0.3);
}

.line-1 {
  width: 60%;
  top: 20%;
  left: 0;
  transform: rotate(-5deg);
  animation: shimmer 8s ease-in-out infinite;
}

.line-2 {
  width: 50%;
  bottom: 30%;
  right: 0;
  transform: rotate(3deg);
  animation: shimmer 8s ease-in-out infinite 4s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(30px, -30px) scale(1.1);
  }
  66% {
    transform: translate(-20px, 20px) scale(0.9);
  }
}

@keyframes shimmer {
  0%, 100% {
    opacity: 0.2;
  }
  50% {
    opacity: 0.7;
  }
}

/* 类别主题样式 - 增强版 */
.timeline-biography {
  background:
    radial-gradient(circle at 15% 85%, rgba(209, 106, 93, 0.12) 0%, transparent 40%),
    radial-gradient(circle at 85% 15%, rgba(165, 137, 56, 0.08) 0%, transparent 40%),
    radial-gradient(circle at 50% 50%, rgba(242, 183, 175, 0.06) 0%, transparent 60%),
    linear-gradient(135deg, #fef5f4 0%, #fce9e7 40%, #f9dbd7 70%, #f5ccc6 100%);
  box-shadow:
    inset 0 0 100px rgba(209, 106, 93, 0.08),
    inset 0 0 40px rgba(165, 137, 56, 0.05),
    0 8px 32px rgba(209, 106, 93, 0.12);
}

.timeline-travelogue {
  background:
    radial-gradient(circle at 20% 80%, rgba(74, 144, 164, 0.12) 0%, transparent 40%),
    radial-gradient(circle at 80% 20%, rgba(157, 204, 217, 0.08) 0%, transparent 40%),
    radial-gradient(circle at 60% 60%, rgba(219, 198, 130, 0.06) 0%, transparent 60%),
    linear-gradient(135deg, #f4f9fb 0%, #e7f2f6 40%, #d9ebf1 70%, #cce4ec 100%);
  box-shadow:
    inset 0 0 100px rgba(74, 144, 164, 0.08),
    inset 0 0 40px rgba(157, 204, 217, 0.05),
    0 8px 32px rgba(74, 144, 164, 0.12);
}

.timeline-warfare {
  background:
    radial-gradient(circle at 25% 75%, rgba(165, 137, 56, 0.12) 0%, transparent 40%),
    radial-gradient(circle at 75% 25%, rgba(209, 106, 93, 0.08) 0%, transparent 40%),
    radial-gradient(circle at 50% 50%, rgba(139, 117, 48, 0.06) 0%, transparent 60%),
    linear-gradient(135deg, #faf7f4 0%, #f2ebe5 40%, #ebe0d6 70%, #e3d5c7 100%);
  box-shadow:
    inset 0 0 100px rgba(165, 137, 56, 0.08),
    inset 0 0 40px rgba(209, 106, 93, 0.05),
    0 8px 32px rgba(165, 137, 56, 0.12);
}

/* 时间轴主体 */
.timeline-main {
  position: relative;
  height: 280px;
  margin-bottom: 40px;
}

/* 中央水平线 */
.timeline-axis {
  position: relative;
  width: 100%;
  height: 100%;
}

.axis-line {
  position: absolute;
  top: 50%;
  left: 5%;
  right: 5%;
  height: 8px;
  border-radius: 999px;
  box-shadow:
    0 2px 12px rgba(0, 0, 0, 0.15),
    0 0 30px rgba(165, 137, 56, 0.4),
    inset 0 1px 3px rgba(255, 255, 255, 0.5);
  transform: translateY(-50%);
  position: relative;
  overflow: hidden;
}

.axis-line::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.8), transparent);
  animation: slide 3s ease-in-out infinite;
}

.axis-line::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg,
    rgba(255, 255, 255, 0.9),
    rgba(255, 255, 255, 0.5),
    rgba(255, 255, 255, 0.9));
  transform: translateY(-50%);
  filter: blur(1px);
}

@keyframes slide {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

/* 事件节点 */
.event-node {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1;
  animation: fadeInUp 0.6s ease-out backwards;
}

.event-node:nth-child(1) { animation-delay: 0.1s; }
.event-node:nth-child(2) { animation-delay: 0.2s; }
.event-node:nth-child(3) { animation-delay: 0.3s; }
.event-node:nth-child(4) { animation-delay: 0.4s; }
.event-node:nth-child(5) { animation-delay: 0.5s; }
.event-node:nth-child(6) { animation-delay: 0.6s; }
.event-node:nth-child(7) { animation-delay: 0.7s; }
.event-node:nth-child(8) { animation-delay: 0.8s; }

.event-node:hover {
  transform: translate(-50%, -60%);
  filter: drop-shadow(0 8px 16px rgba(0, 0, 0, 0.2));
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translate(-50%, -30%);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}

.node-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  /* 背景和颜色在行内样式中动态设置 */
  border: 4px solid;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.15),
    0 2px 8px currentColor,
    0 0 20px currentColor,
    inset 0 2px 4px rgba(255, 255, 255, 0.5);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 12px;
  position: relative;
  overflow: hidden;
}

.node-icon::before {
  content: '';
  position: absolute;
  inset: -10px;
  background: radial-gradient(circle, currentColor 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
  animation: glow 3s ease-in-out infinite;
}

@keyframes glow {
  0%, 100% {
    opacity: 0.1;
  }
  50% {
    opacity: 0.3;
  }
}

.node-icon:hover::before {
  opacity: 0.4;
  animation: none;
}

.node-icon.active {
  width: 72px;
  height: 72px;
  font-size: 28px;
  box-shadow:
    0 6px 20px rgba(0, 0, 0, 0.25),
    0 0 0 8px currentColor,
    0 0 20px currentColor,
    inset 0 2px 6px rgba(255, 255, 255, 0.6);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    filter: brightness(1);
  }
  50% {
    transform: scale(1.05);
    filter: brightness(1.15);
  }
}

.node-label {
  font-size: 13px;
  font-weight: 700;
  /* 颜色在行内样式中动态设置 */
  margin-bottom: 4px;
  white-space: nowrap;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 14px;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.1),
    0 1px 3px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(165, 137, 56, 0.15);
  transition: all 0.3s ease;
}

.event-node:hover .node-label {
  transform: translateY(-2px);
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.15),
    0 2px 6px rgba(0, 0, 0, 0.1);
}

/* 信息卡片 */
.info-card {
  position: relative;
  background: white;
  border-radius: 16px;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.1);
  border-top: 4px solid;
  overflow: hidden;
  max-height: 600px;
  overflow-y: auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e8e5e0;
  background: linear-gradient(135deg, #fdfbf7 0%, white 100%);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #2f2b2a;
}

.title-icon {
  font-size: 24px;
  /* 颜色在行内样式中动态设置 */
}

.close-btn {
  font-size: 20px;
  color: #8a8178;
  padding: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #d16a5d;
  transform: scale(1.1);
}

.card-body {
  padding: 24px;
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #fdfbf9 0%, #faf8f5 100%);
  border-radius: 12px;
  border-left: 4px solid; /* 颜色在行内样式中动态设置 */
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.info-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.info-icon {
  font-size: 24px;
  /* 颜色在行内样式中动态设置 */
}

.info-label {
  font-size: 12px;
  color: #8a8178;
  font-weight: 600;
  margin-bottom: 4px;
}

.info-value {
  font-size: 14px;
  color: #2f2b2a;
  font-weight: 600;
}

.significance-rate {
  --el-rate-icon-size: 14px;
}

/* 内容区块 */
.section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #2f2b2a;
  margin-bottom: 12px;
}

.section-header .el-icon {
  font-size: 18px;
  /* 颜色在行内样式中动态设置 */
}

.section-content {
  font-size: 14px;
  color: #6a645f;
  line-height: 1.8;
}

.section-content.description {
  padding: 16px;
  background: linear-gradient(135deg, #fef9f0 0%, #fdf5e8 100%);
  border-radius: 10px;
  border-left: 4px solid #d4a348;
  box-shadow:
    inset 0 1px 3px rgba(165, 137, 56, 0.1),
    0 2px 8px rgba(165, 137, 56, 0.05);
  position: relative;
  overflow: hidden;
}

.section-content.description::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  transform: translateX(-100%);
  animation: shimmer-content 3s ease-in-out infinite;
  animation-delay: 1s;
}

@keyframes shimmer-content {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

.section-content.impact {
  padding: 16px;
  background: linear-gradient(135deg, #f0f8ff 0%, #e6f3ff 100%);
  border-radius: 10px;
  border-left: 4px solid #4a90a4;
  box-shadow:
    inset 0 1px 3px rgba(74, 144, 164, 0.1),
    0 2px 8px rgba(74, 144, 164, 0.05);
  position: relative;
  overflow: hidden;
}

.section-content.impact::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  transform: translateX(-100%);
  animation: shimmer-content 3s ease-in-out infinite;
  animation-delay: 2s;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-tag {
  color: white;
  border: none;
  padding: 8px 16px;
  font-weight: 600;
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.person-tag::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.5s ease;
}

.person-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
}

.person-tag:hover::before {
  left: 100%;
}

.no-data {
  font-size: 13px;
  color: #8a8178;
  font-style: italic;
}

.no-data-inline {
  color: #8a8178;
  font-style: italic;
  opacity: 0.6;
}

/* 卡片底部 */
.card-footer {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e8e5e0;
}

.footer-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.page-indicator {
  font-size: 14px;
  color: #6a645f;
  font-weight: 600;
}

.view-original-btn {
  width: 100%;
  margin-top: 8px;
}

/* 图例说明 */
.timeline-legend {
  position: absolute;
  top: 20px;
  right: 20px;
  background: rgba(255, 255, 255, 0.95);
  padding: 16px 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  max-width: 300px;
}

.legend-title {
  font-size: 16px;
  font-weight: 700;
  color: #2f2b2a;
  margin-bottom: 8px;
}

.legend-desc {
  font-size: 13px;
  color: #6a645f;
  line-height: 1.6;
}

/* 滑入动画 */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(100%);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(50%);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
  padding: 40px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #666;
  margin: 20px 0 10px;
}

.empty-desc {
  font-size: 14px;
  color: #999;
  margin: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .timeline-main {
    height: 200px;
  }

  .node-icon {
    width: 50px;
    height: 50px;
    font-size: 20px;
  }

  .node-icon.active {
    width: 60px;
    height: 60px;
  }

  .node-title {
    font-size: 12px;
    max-width: 80px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .timeline-legend {
    position: static;
    margin-top: 20px;
    max-width: none;
  }
}
</style>
