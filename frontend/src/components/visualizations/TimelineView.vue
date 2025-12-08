<template>
  <div class="timeline-view" :class="`timeline-${category}`">
    <!-- 工具栏 -->
    <div class="timeline-toolbar" v-if="milestones && milestones.length > 0">
      <div class="toolbar-left">
        <h3 class="view-title">{{ getCategoryTitle(category) }}</h3>
        <span class="event-count">共 {{ filteredMilestones.length }} 个事件</span>
      </div>

      <div class="toolbar-right">
        <label>事件类型：</label>
        <select v-model="eventTypeFilter" class="filter-select">
          <option value="all">全部事件</option>
          <option v-for="type in eventTypes" :key="type" :value="type">
            {{ getEventTypeLabel(type) }}
          </option>
        </select>

        <label style="margin-left: 12px;">排序：</label>
        <select v-model="sortBy" class="filter-select">
          <option value="default">默认顺序</option>
          <option value="significance">按重要度</option>
          <option value="date">按时间</option>
        </select>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!filteredMilestones || filteredMilestones.length === 0" class="empty-state">
      <div class="empty-icon">📅</div>
      <p class="empty-title">暂无时间轴数据</p>
      <p class="empty-desc">
        {{ eventTypeFilter !== 'all' ? '当前筛选条件下暂无事件' : '请先在"分析"页面运行文本分析，生成时间轴事件' }}
      </p>
    </div>

    <!-- 时间轴主体 -->
    <div v-else class="timeline-content">
      <!-- 水平时间轴 -->
      <div class="horizontal-timeline">
        <div class="timeline-track"></div>

        <div
          v-for="(milestone, index) in filteredMilestones"
          :key="milestone.title + index"
          class="timeline-point"
          :class="{ active: selectedIndex === index }"
          :style="{ left: calculatePosition(index, filteredMilestones.length) }"
          @click="selectEvent(milestone, index)"
        >
          <div class="point-marker" :style="getMarkerStyle(milestone, index)">
            <span class="point-icon">{{ getEventIcon(milestone.eventType) }}</span>
          </div>
          <div class="point-label">{{ milestone.dateLabel || `事件${index + 1}` }}</div>
          <div class="point-title">{{ milestone.title }}</div>
        </div>
      </div>

      <!-- 信息卡片（下方滑出） -->
      <transition name="slide-up">
        <div v-if="selectedEvent" class="event-card" :class="`card-${category}`">
          <div class="card-header">
            <div class="card-title-group">
              <h4 class="card-title">{{ selectedEvent.title }}</h4>
              <span class="significance-badge" :style="getSignificanceBadgeStyle(selectedEvent.significance)">
                重要度: {{ selectedEvent.significance || 5 }}
              </span>
            </div>
            <button class="close-btn" @click="closeCard">✕</button>
          </div>

          <div class="card-body">
            <!-- 原文摘要 -->
            <div class="info-section" v-if="selectedEvent.description">
              <div class="section-label">📜 原文摘要</div>
              <div class="section-content">{{ selectedEvent.description }}</div>
            </div>

            <!-- 时间信息 -->
            <div class="info-section" v-if="selectedEvent.dateLabel">
              <div class="section-label">📅 时间</div>
              <div class="section-content">{{ selectedEvent.dateLabel }}</div>
            </div>

            <!-- 相关人物 - 始终显示 -->
            <div class="info-section">
              <div class="section-label">👤 相关人物</div>
              <div class="section-content">
                <template v-if="selectedEvent.participants && selectedEvent.participants.length > 0">
                  <span v-for="(person, idx) in selectedEvent.participants"
                        :key="idx"
                        class="tag person-tag">
                    {{ person }}
                  </span>
                </template>
                <span v-else class="no-data">无</span>
              </div>
            </div>

            <!-- 地点 -->
            <div class="info-section" v-if="selectedEvent.location">
              <div class="section-label">📍 地点</div>
              <div class="section-content">
                <span class="tag location-tag">{{ selectedEvent.location }}</span>
              </div>
            </div>

            <!-- 历史影响 - 始终显示 -->
            <div class="info-section">
              <div class="section-label">💡 历史影响</div>
              <div class="section-content">
                {{ selectedEvent.impact || '无' }}
              </div>
            </div>

            <!-- 跳转原文按钮 -->
            <button
              v-if="selectedEvent.entityId || selectedEvent.startOffset !== undefined"
              class="jump-to-text-btn"
              @click="jumpToOriginalText(selectedEvent)"
            >
              📖 查看原文位置
            </button>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  milestones: {
    type: Array,
    default: () => []
  },
  category: {
    type: String,
    default: 'unknown'
  },
  onJumpToText: {
    type: Function,
    default: null
  }
});

const eventTypeFilter = ref('all');
const sortBy = ref('default');
const selectedIndex = ref(-1);

// 智能过滤和排序事件列表
const filteredMilestones = computed(() => {
  if (!props.milestones || props.milestones.length === 0) {
    return [];
  }

  let filtered = [...props.milestones];

  // 按事件类型过滤
  if (eventTypeFilter.value !== 'all') {
    filtered = filtered.filter(m => m.eventType === eventTypeFilter.value);
  }

  // 排序
  if (sortBy.value === 'significance') {
    filtered.sort((a, b) => (b.significance || 0) - (a.significance || 0));
  } else if (sortBy.value === 'date') {
    filtered.sort((a, b) => {
      const dateA = a.dateLabel || '';
      const dateB = b.dateLabel || '';
      return dateA.localeCompare(dateB, 'zh-CN');
    });
  }

  return filtered;
});

// 当前选中的事件
const selectedEvent = computed(() => {
  if (selectedIndex.value >= 0 && selectedIndex.value < filteredMilestones.value.length) {
    return filteredMilestones.value[selectedIndex.value];
  }
  return null;
});

// 获取所有事件类型
const eventTypes = computed(() => {
  if (!props.milestones) return [];
  const types = new Set(props.milestones.map(m => m.eventType).filter(Boolean));
  return Array.from(types);
});

// 选择事件
const selectEvent = (_milestone, index) => {
  selectedIndex.value = index;
};

// 关闭卡片
const closeCard = () => {
  selectedIndex.value = -1;
};

// 跳转到原文
const jumpToOriginalText = (milestone) => {
  if (props.onJumpToText) {
    props.onJumpToText({
      entityId: milestone.entityId,
      startOffset: milestone.startOffset,
      endOffset: milestone.endOffset
    });
  }
};

// 计算时间点位置（百分比）
const calculatePosition = (index, total) => {
  if (total === 1) return '50%';
  const padding = 8; // 两端留白百分比
  const usableWidth = 100 - 2 * padding;
  const position = padding + (usableWidth * index) / (total - 1);
  return `${position}%`;
};

// 获取标记样式
const getMarkerStyle = (_milestone, index) => {
  const colors = getCategoryColors(props.category);
  const colorIndex = index % colors.length;
  return {
    background: colors[colorIndex],
    boxShadow: `0 4px 12px ${colors[colorIndex]}40`
  };
};

// 获取重要度徽章样式
const getSignificanceBadgeStyle = (significance) => {
  const value = significance || 5;
  if (value >= 8) return { background: '#f56c6c', color: 'white' };
  if (value >= 5) return { background: '#e6a23c', color: 'white' };
  return { background: '#909399', color: 'white' };
};

// 获取分类颜色主题
const getCategoryColors = (category) => {
  const themes = {
    biography: ['#c45850', '#d4736e', '#e68b86', '#f5a39d'],
    travelogue: ['#4a90a4', '#5fa3b5', '#75b5c5', '#8dc7d6'],
    warfare: ['#8b5a3c', '#a06f4d', '#b4845f', '#c89972'],
    unknown: ['#6b8e99', '#7d9fa9', '#8fb0b9', '#a1c1c9']
  };
  return themes[category] || themes.unknown;
};

// 获取分类标题
const getCategoryTitle = (category) => {
  const titles = {
    biography: '人物生平时间轴',
    travelogue: '游记行程时间轴',
    warfare: '战争事件时间轴',
    unknown: '事件时间轴'
  };
  return titles[category] || titles.unknown;
};

// 获取事件类型标签
const getEventTypeLabel = (eventType) => {
  const labels = {
    birth: '出生',
    official: '任官',
    death: '逝世',
    achievement: '成就',
    life: '生平',
    travel: '行旅',
    scenery: '风景',
    battle: '战事',
    military: '军事',
    default: '其他'
  };
  return labels[eventType] || labels.default;
};

// 获取事件图标
const getEventIcon = (eventType) => {
  const icons = {
    birth: '🎂',
    official: '📜',
    death: '🕯️',
    achievement: '🏆',
    life: '📖',
    travel: '🚶',
    scenery: '🏔️',
    battle: '⚔️',
    military: '🛡️',
    default: '●'
  };
  return icons[eventType] || icons.default;
};
</script>

<style scoped>
.timeline-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 500px;
  background: linear-gradient(180deg, #fafafa 0%, #ffffff 100%);
  border-radius: 16px;
  overflow: hidden;
}

/* 工具栏 */
.timeline-toolbar {
  background: rgba(255, 255, 255, 0.95);
  padding: 16px 24px;
  border-bottom: 2px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
  backdrop-filter: blur(10px);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.view-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #2c3e50;
}

.event-count {
  font-size: 13px;
  color: #6a645f;
  font-weight: 600;
  padding: 4px 12px;
  background: linear-gradient(135deg, #fef9f0 0%, #fdf5e8 100%);
  border-radius: 999px;
  border: 1px solid #e8e5e0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right label {
  font-size: 13px;
  font-weight: 600;
  color: #2f2b2a;
}

.filter-select {
  padding: 6px 12px;
  border: 1px solid #d4c4b0;
  border-radius: 6px;
  background: white;
  font-size: 13px;
  color: #2f2b2a;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-select:hover {
  border-color: #a5895b;
}

.filter-select:focus {
  outline: none;
  border-color: #a5895b;
  box-shadow: 0 0 0 2px rgba(165, 137, 56, 0.1);
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

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.3;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #666;
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 14px;
  color: #999;
  margin: 0;
  max-width: 400px;
}

/* 时间轴内容 */
.timeline-content {
  flex: 1;
  padding: 60px 40px 40px;
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 水平时间轴 */
.horizontal-timeline {
  position: relative;
  height: 180px;
  margin-bottom: 40px;
}

.timeline-track {
  position: absolute;
  top: 60px;
  left: 5%;
  right: 5%;
  height: 4px;
  background: linear-gradient(90deg, transparent, #d4c4b0 10%, #d4c4b0 90%, transparent);
  border-radius: 2px;
  box-shadow: 0 2px 8px rgba(165, 137, 56, 0.15);
}

.timeline-point {
  position: absolute;
  top: 20px;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.3s ease;
  z-index: 1;
}

.timeline-point:hover {
  transform: translateX(-50%) translateY(-8px);
  z-index: 10;
}

.timeline-point.active {
  z-index: 20;
}

.point-marker {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid white;
  transition: all 0.3s ease;
  font-size: 24px;
}

.timeline-point:hover .point-marker {
  transform: scale(1.2);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
}

.timeline-point.active .point-marker {
  transform: scale(1.3);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.point-label {
  margin-top: 68px;
  font-size: 12px;
  font-weight: 600;
  color: #8a8178;
  white-space: nowrap;
  background: white;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #e8e5e0;
}

.point-title {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 700;
  color: #2f2b2a;
  white-space: nowrap;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}

/* 事件卡片 */
.event-card {
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  border: 2px solid #e8e5e0;
}

.card-header {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f7f4ec 0%, #ffffff 100%);
  border-bottom: 2px solid #e8e5e0;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card-title-group {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #2c3e50;
}

.significance-badge {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.close-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: #f5f5f5;
  color: #666;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #e0e0e0;
  color: #333;
}

.card-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 13px;
  font-weight: 700;
  color: #8a8178;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-content {
  font-size: 14px;
  color: #2f2b2a;
  line-height: 1.6;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #e8e5e0;
}

.tag {
  display: inline-block;
  padding: 4px 12px;
  margin-right: 8px;
  margin-bottom: 8px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
}

.person-tag {
  background: #e3f2fd;
  color: #1976d2;
  border: 1px solid #bbdefb;
}

.location-tag {
  background: #fff3e0;
  color: #f57c00;
  border: 1px solid #ffe0b2;
}

.no-data {
  color: #999;
  font-style: italic;
  font-size: 13px;
}

.jump-to-text-btn {
  margin-top: 8px;
  width: 100%;
  padding: 12px 20px;
  background: linear-gradient(135deg, #4a90a4, #5fa3b5);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(74, 144, 164, 0.3);
}

.jump-to-text-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(74, 144, 164, 0.4);
}

.jump-to-text-btn:active {
  transform: translateY(0);
}

/* 卡片滑入动画 */
.slide-up-enter-active {
  animation: slideUp 0.4s ease-out;
}

.slide-up-leave-active {
  animation: slideUp 0.3s ease-in reverse;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(40px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 不同类型的主题色 */
.timeline-biography {
  --theme-primary: #c45850;
  --theme-bg: linear-gradient(180deg, rgba(196, 88, 80, 0.05), rgba(242, 183, 175, 0.05));
}

.timeline-travelogue {
  --theme-primary: #4a90a4;
  --theme-bg: linear-gradient(180deg, rgba(74, 144, 164, 0.05), rgba(157, 204, 217, 0.05));
}

.timeline-warfare {
  --theme-primary: #8b5a3c;
  --theme-bg: linear-gradient(180deg, rgba(139, 90, 60, 0.05), rgba(165, 137, 56, 0.05));
}

.timeline-unknown {
  --theme-primary: #6b8e99;
  --theme-bg: linear-gradient(180deg, rgba(107, 142, 153, 0.05), rgba(161, 193, 201, 0.05));
}

.card-biography {
  border-color: var(--theme-primary);
}

.card-travelogue {
  border-color: var(--theme-primary);
}

.card-warfare {
  border-color: var(--theme-primary);
}

/* 响应式 */
@media (max-width: 768px) {
  .timeline-content {
    padding: 40px 20px 20px;
  }

  .point-marker {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .point-label {
    margin-top: 56px;
  }

  .card-body {
    padding: 16px;
  }
}
</style>
