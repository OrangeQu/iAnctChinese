<template>
  <div class="process-cycle-view">
    <div v-if="!hasData" class="empty-state">
      <div class="empty-icon">⚙️</div>
      <div class="empty-text">暂无流程数据</div>
      <div class="empty-hint">文本中未识别到明确的工艺流程或农业步骤</div>
    </div>
    <div v-else class="cycle-container">
      <!-- 工具栏 -->
      <div class="cycle-toolbar">
        <div class="toolbar-left">
          <h3 class="view-title">{{ isAgriculture ? '农事流程图' : '工艺周期图' }}</h3>
          <span class="step-count">共 {{ props.steps.length }} 个步骤</span>
        </div>
      </div>

      <!-- 主要内容区域 -->
      <div class="content-wrapper">
        <!-- 左侧流程图 -->
        <div class="chart-section" :class="{ 'has-detail': selectedStep }">
          <div ref="chartRef" class="chart"></div>
        </div>

        <!-- 右侧详情卡片 -->
        <transition name="slide-in">
          <div v-if="selectedStep" class="detail-card">
            <div class="card-header">
              <div class="step-title-group">
                <h4 class="step-name">{{ selectedStep.name }}</h4>
                <span class="category-badge" :style="getCategoryBadgeStyle(selectedStep.category)">
                  {{ selectedStep.category || '其他' }}
                </span>
              </div>
              <button class="card-close-btn" @click="closeDetail" title="关闭详情">
                ✕
              </button>
            </div>

            <div class="card-body">
              <!-- 步骤序号 -->
              <div class="info-section">
                <div class="section-label">🔢 步骤序号</div>
                <div class="section-content step-sequence">
                  第 {{ selectedStep.sequence || '?' }} 步
                </div>
              </div>

              <!-- 步骤说明 -->
              <div class="info-section" v-if="selectedStep.description">
                <div class="section-label">📝 步骤说明</div>
                <div class="section-content">{{ selectedStep.description }}</div>
              </div>

              <!-- 所需工具 -->
              <div class="info-section" v-if="selectedStep.tools && selectedStep.tools.length > 0">
                <div class="section-label">🔧 所需工具</div>
                <div class="section-content">
                  <div class="tag-list">
                    <span v-for="(tool, idx) in selectedStep.tools" :key="idx" class="tag tool-tag">
                      {{ tool }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- 所需材料 -->
              <div class="info-section" v-if="selectedStep.materials && selectedStep.materials.length > 0">
                <div class="section-label">📦 所需材料</div>
                <div class="section-content">
                  <div class="tag-list">
                    <span v-for="(material, idx) in selectedStep.materials" :key="idx" class="tag material-tag">
                      {{ material }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- 预期产出 -->
              <div class="info-section" v-if="selectedStep.output">
                <div class="section-label">✨ 预期产出</div>
                <div class="section-content output-content">{{ selectedStep.output }}</div>
              </div>

              <!-- 耗时 -->
              <div class="info-section" v-if="selectedStep.duration">
                <div class="section-label">⏱️ 耗时</div>
                <div class="section-content">
                  <span class="duration-badge">约 {{ selectedStep.duration }} 天</span>
                </div>
              </div>

              <!-- 操作要点 -->
              <div class="info-section">
                <div class="section-label">💡 操作要点</div>
                <div class="section-content">
                  {{ getStepTips(selectedStep.category, selectedStep.name) }}
                </div>
              </div>

              <!-- 点击提示 -->
              <div class="click-hint">💡 再次点击流程节点可关闭详情</div>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { GraphChart } from "echarts/charts";
import { TitleComponent, TooltipComponent, LegendComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([GraphChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const props = defineProps({
  steps: {
    type: Array,
    default: () => []
  },
  category: {
    type: String,
    default: 'crafts'
  }
});

const chartRef = ref();
const selectedStep = ref(null);
let chartInstance;

const hasData = computed(() => Array.isArray(props.steps) && props.steps.length > 0);
const isAgriculture = computed(() => props.category === 'agriculture');

// 选择步骤
const selectStep = (step) => {
  // 如果点击的是同一个步骤，则关闭详情卡片
  if (selectedStep.value && selectedStep.value.name === step.name) {
    selectedStep.value = null;
  } else {
    selectedStep.value = step;
  }
};

// 关闭详情
const closeDetail = () => {
  selectedStep.value = null;
};

// 获取类别徽章样式
const getCategoryBadgeStyle = (category) => {
  const colorMap = {
    // 农业步骤
    '整地': { background: '#8B4513', color: '#fff' },
    '播种': { background: '#228B22', color: '#fff' },
    '灌溉': { background: '#1E90FF', color: '#fff' },
    '施肥': { background: '#DAA520', color: '#fff' },
    '田间管理': { background: '#32CD32', color: '#fff' },
    '收获': { background: '#FF8C00', color: '#fff' },
    // 工艺步骤
    '选材': { background: '#8B4513', color: '#fff' },
    '加工': { background: '#4682B4', color: '#fff' },
    '组装': { background: '#9370DB', color: '#fff' },
    '修整': { background: '#20B2AA', color: '#fff' },
    '装饰': { background: '#FF69B4', color: '#fff' },
    '热处理': { background: '#DC143C', color: '#fff' },
    '制作': { background: '#708090', color: '#fff' },
    // 默认
    '其他': { background: '#95a5a6', color: '#fff' }
  };
  return colorMap[category] || colorMap['其他'];
};

// 根据步骤类别获取颜色
const getCategoryColor = (category) => {
  const style = getCategoryBadgeStyle(category);
  return style.background;
};

// 获取步骤要点
const getStepTips = (category, name) => {
  const tipsMap = {
    // 农业操作要点
    '整地': '需充分松土，清除杂草和石块，保证土地平整，有利于后续播种和灌溉。',
    '播种': '选择优良种子，注意播种深度和间距，确保出苗率。播种后需覆土压实。',
    '灌溉': '根据作物需水情况合理灌溉，避免积水或干旱。宜在早晚时分进行。',
    '施肥': '按照作物生长阶段施用适量肥料，注意有机肥与化肥的配合使用。',
    '田间管理': '及时除草、间苗、防治病虫害，保持田间通风透光。',
    '收获': '把握最佳收获时机，避免过早或过晚影响产量和品质。',

    // 工艺操作要点
    '选材': '根据制作要求选择合适的原材料，注意材质、规格和质量。',
    '加工': '按照工艺要求进行切割、打磨等加工，确保尺寸和形状准确。',
    '组装': '按照顺序进行组装，注意各部件的配合和固定方式。',
    '修整': '对成品进行细致修整，去除毛刺和瑕疵，使表面光滑平整。',
    '装饰': '添加装饰元素时注意整体协调，避免过度装饰影响功能。',
    '热处理': '控制好温度和时间，避免过热或不足影响材料性能。',
    '制作': '严格按照工艺流程操作，注意安全，确保产品质量。'
  };

  // 精确匹配
  if (tipsMap[category]) {
    return tipsMap[category];
  }

  // 模糊匹配
  for (const [key, value] of Object.entries(tipsMap)) {
    if (name.includes(key) || category.includes(key)) {
      return value;
    }
  }

  // 默认提示
  if (props.category === 'agriculture') {
    return '遵循农时节令，因地制宜，注意天气变化对农事活动的影响。';
  } else {
    return '遵循工艺规范，注意操作安全，确保每个步骤的质量标准。';
  }
};

// 构建图表数据
const buildChartData = () => {
  if (!hasData.value) return { nodes: [], links: [] };

  const sortedSteps = [...props.steps].sort((a, b) => (a.sequence || 0) - (b.sequence || 0));

  const nodes = sortedSteps.map((step, index) => ({
    id: String(index),
    name: step.name,
    symbolSize: 85,
    value: step.sequence || index + 1,
    category: step.category || '其他',
    itemStyle: {
      color: getCategoryColor(step.category || '其他'),
      borderColor: '#fff',
      borderWidth: 3,
      shadowBlur: 12,
      shadowColor: 'rgba(0, 0, 0, 0.25)'
    },
    label: {
      show: true,
      fontSize: 13,
      fontWeight: 'bold',
      color: '#fff',
      formatter: '{b}'
    },
    stepData: step
  }));

  const links = [];
  for (let i = 0; i < nodes.length - 1; i++) {
    links.push({
      source: String(i),
      target: String(i + 1),
      lineStyle: {
        color: '#999',
        width: 3,
        curveness: 0.2
      },
      label: {
        show: sortedSteps[i].duration ? true : false,
        formatter: sortedSteps[i].duration ? `${sortedSteps[i].duration}天` : '',
        fontSize: 11,
        color: '#666',
        backgroundColor: 'rgba(255, 255, 255, 0.9)',
        padding: [2, 6],
        borderRadius: 4
      }
    });
  }

  // 添加循环箭头（从最后一步回到第一步）
  if (nodes.length > 1) {
    links.push({
      source: String(nodes.length - 1),
      target: '0',
      lineStyle: {
        color: '#ccc',
        width: 2,
        type: 'dashed',
        curveness: 0.5
      },
      label: {
        show: true,
        formatter: '循环',
        fontSize: 11,
        color: '#999',
        backgroundColor: 'rgba(255, 255, 255, 0.9)',
        padding: [2, 6],
        borderRadius: 4
      }
    });
  }

  return { nodes, links };
};

const renderChart = () => {
  if (!hasData.value) {
    if (chartInstance) {
      chartInstance.dispose();
      chartInstance = null;
    }
    return;
  }

  if (!chartRef.value) return;

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);

    // 添加点击事件
    chartInstance.on('click', (params) => {
      if (params.componentType === 'series' && params.dataType === 'node') {
        const step = params.data.stepData;
        if (step) {
          selectStep(step);
        }
      }
    });
  }

  const { nodes, links } = buildChartData();

  // 计算布局：圆形排列
  const angleStep = (2 * Math.PI) / nodes.length;
  const radius = 180;
  const centerX = 0;
  const centerY = 0;

  nodes.forEach((node, index) => {
    const angle = angleStep * index - Math.PI / 2;
    node.x = centerX + radius * Math.cos(angle);
    node.y = centerY + radius * Math.sin(angle);
  });

  chartInstance.setOption({
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      backgroundColor: 'rgba(255, 250, 243, 0.98)',
      borderColor: '#d1b17e',
      borderWidth: 1.5,
      textStyle: {
        color: '#2f2b2a',
        fontSize: 13
      },
      padding: [12, 16],
      shadowBlur: 12,
      shadowColor: 'rgba(0, 0, 0, 0.15)',
      formatter: (params) => {
        if (params.dataType === 'node') {
          const step = params.data.stepData;
          let html = `<div style="font-weight: 600; font-size: 15px; margin-bottom: 8px;">${step.name}</div>`;
          html += `<div style="margin-bottom: 4px;"><strong>类别：</strong>${step.category || '未分类'}</div>`;
          html += `<div style="color: #8a7a6a; font-size: 12px; margin-top: 6px;">点击查看详细信息</div>`;
          return html;
        }
        return params.name;
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        data: nodes,
        links: links,
        roam: true,
        label: {
          show: true,
          position: 'inside'
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [0, 12],
        emphasis: {
          focus: 'adjacency',
          itemStyle: {
            borderWidth: 4,
            shadowBlur: 20,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          },
          lineStyle: {
            width: 4
          }
        },
        lineStyle: {
          opacity: 0.9,
          curveness: 0.2
        }
      }
    ]
  });
};

onMounted(() => {
  renderChart();
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
  if (chartInstance) {
    chartInstance.dispose();
  }
});

watch(
  () => props.steps,
  () => renderChart(),
  { deep: true }
);

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize();
  }
};
</script>

<style scoped>
.process-cycle-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 560px;
  background: linear-gradient(180deg, #fafafa 0%, #ffffff 100%);
  border-radius: 16px;
  overflow: hidden;
}

/* 工具栏 */
.cycle-toolbar {
  background: rgba(255, 255, 255, 0.95);
  padding: 16px 24px;
  border-bottom: 2px solid #eee;
  display: flex;
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

.step-count {
  font-size: 13px;
  color: #6a645f;
  font-weight: 600;
  padding: 4px 12px;
  background: linear-gradient(135deg, #fef9f0 0%, #fdf5e8 100%);
  border-radius: 999px;
  border: 1px solid #e8e5e0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 400px;
  text-align: center;
  padding: 40px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.3;
  animation: rotate 3s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.empty-text {
  font-size: 18px;
  font-weight: 600;
  color: #666;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 14px;
  color: #999;
  max-width: 400px;
}

/* 容器 */
.cycle-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.content-wrapper {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

.chart-section {
  flex: 1;
  min-width: 0;
  transition: all 0.3s ease;
  position: relative;
  z-index: 1;
}

.chart-section.has-detail {
  flex: 0 0 55%;
}

.chart {
  width: 100%;
  height: 100%;
  min-height: 480px;
}

/* 详情卡片 */
.detail-card {
  flex: 0 0 42%;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  border: 2px solid #e8e5e0;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 10;
}

.card-header {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f7f4ec 0%, #ffffff 100%);
  border-bottom: 2px solid #e8e5e0;
  position: relative;
}

.step-title-group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 40px;
}

.step-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #2c3e50;
  flex: 1;
}

.category-badge {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.card-close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
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
  z-index: 10;
}

.card-close-btn:hover {
  background: #e0e0e0;
  color: #333;
  transform: rotate(90deg);
}

.card-body {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
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

.step-sequence {
  font-size: 16px;
  font-weight: 600;
  color: #5470c6;
}

.output-content {
  font-weight: 600;
  color: #52c41a;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
}

.tool-tag {
  background: #fff3e0;
  color: #f57c00;
  border: 1px solid #ffe0b2;
}

.material-tag {
  background: #e8f5e9;
  color: #388e3c;
  border: 1px solid #c8e6c9;
}

.duration-badge {
  display: inline-block;
  padding: 6px 14px;
  background: #e6f7ff;
  color: #1890ff;
  border-radius: 999px;
  font-weight: 600;
  border: 1px solid #91d5ff;
}

/* 滑入动画 */
.slide-in-enter-active {
  animation: slideIn 0.3s ease-out;
}

.slide-in-leave-active {
  animation: slideIn 0.25s ease-in reverse;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.click-hint {
  text-align: center;
  padding: 12px;
  margin-top: 8px;
  color: #a08968;
  font-size: 12px;
  font-style: italic;
  background: linear-gradient(135deg, #fffbf0 0%, #fff8e6 100%);
  border-radius: 8px;
  border: 1px dashed #d4af37;
  animation: pulseHint 3s ease-in-out infinite;
}

@keyframes pulseHint {
  0%, 100% {
    opacity: 0.7;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.02);
  }
}

/* 响应式 */
@media (max-width: 1024px) {
  .content-wrapper {
    flex-direction: column;
  }

  .chart-section {
    flex: 1 !important;
  }

  .detail-card {
    flex: 0 0 auto;
    max-height: 400px;
  }
}
</style>
