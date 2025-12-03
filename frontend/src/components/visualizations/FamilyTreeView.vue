<template>
  <div class="family-tree">
    <div v-if="!hasData" class="empty-state">
      <div class="empty-icon">🏛️</div>
      <div class="empty-text">暂无家谱数据</div>
      <div class="empty-hint">文本中未识别到明确的家族关系</div>
    </div>
    <div v-else class="tree-container">
      <div class="tree-header">
        <div class="header-decoration">
          <span class="decoration-line"></span>
          <span class="header-title">家族世系</span>
          <span class="decoration-line"></span>
        </div>
      </div>
      <div ref="chartRef" class="chart"></div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { TreeChart } from "echarts/charts";
import { TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([TreeChart, TooltipComponent, CanvasRenderer]);

const props = defineProps({
  nodes: {
    type: Array,
    default: () => []
  }
});

const chartRef = ref();
let chartInstance;

const hasData = computed(() => Array.isArray(props.nodes) && props.nodes.length > 0);

// 根据关系和名字判断性别和辈分，返回更精细的颜色
const getPersonStyle = (name = "", relation = "") => {
  // 女性关系
  if (/母|妻|女|妣|姑|姨|嫂|妹|婦/.test(name) || /母|妻|女|姑|姨|嫂|妹/.test(relation)) {
    return {
      color: "#fef0f5",
      borderColor: "#e88fa3",
      shadowColor: "rgba(232, 143, 163, 0.3)"
    };
  }
  // 男性关系
  if (/父|公|子|兄|叔|伯|弟|祖|先/.test(name) || /父|子|兄|弟|叔|伯/.test(relation)) {
    return {
      color: "#f0f5fe",
      borderColor: "#6b90d4",
      shadowColor: "rgba(107, 144, 212, 0.3)"
    };
  }
  // 默认（未知性别）
  return {
    color: "#fffbf3",
    borderColor: "#c9b37c",
    shadowColor: "rgba(201, 179, 124, 0.3)"
  };
};

// 根据层级获取节点大小
const getNodeSize = (depth = 0) => {
  if (depth === 0) return 24; // 根节点最大
  if (depth === 1) return 20;
  return 16;
};

const enhanceNodes = (nodes, depth = 0) =>
  nodes.map((n) => {
    const style = getPersonStyle(n.name, n.relation);
    return {
      ...n,
      itemStyle: {
        color: style.color,
        borderColor: style.borderColor,
        borderWidth: 2.5,
        shadowBlur: 8,
        shadowColor: style.shadowColor,
        shadowOffsetY: 2
      },
      label: {
        color: "#2f2b2a",
        fontSize: depth === 0 ? 14 : 12,
        fontWeight: depth === 0 ? "bold" : "normal",
        fontFamily: "'Source Han Serif CN', 'Noto Serif SC', serif",
        backgroundColor: "rgba(255, 250, 243, 0.92)",
        padding: [5, 10],
        borderRadius: 8,
        shadowBlur: 4,
        shadowColor: "rgba(0, 0, 0, 0.08)",
        shadowOffsetY: 1
      },
      lineStyle: {
        color: style.borderColor,
        width: 2,
        curveness: 0.3,
        shadowBlur: 4,
        shadowColor: "rgba(0, 0, 0, 0.1)"
      },
      symbolSize: getNodeSize(depth),
      children: n.children ? enhanceNodes(n.children, depth + 1) : []
    };
  });

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
  }
  const data = enhanceNodes(props.nodes);
  chartInstance.setOption({
    tooltip: {
      trigger: "item",
      triggerOn: "mousemove",
      backgroundColor: "rgba(255, 250, 243, 0.98)",
      borderColor: "#d1b17e",
      borderWidth: 1.5,
      textStyle: {
        color: "#2f2b2a",
        fontSize: 13,
        fontFamily: "'Source Han Serif CN', 'Noto Serif SC', serif"
      },
      padding: [10, 14],
      shadowBlur: 12,
      shadowColor: "rgba(0, 0, 0, 0.15)",
      formatter: (params) => {
        const d = params.data;
        let html = `<div style="font-weight: 600; font-size: 14px; margin-bottom: 4px;">${d.name || "未知"}</div>`;
        if (d.relation) {
          html += `<div style="color: #8a7a6a; font-size: 12px;">关系：${d.relation}</div>`;
        }
        return html;
      }
    },
    series: [
      {
        type: "tree",
        data,
        top: "12%",
        left: "8%",
        bottom: "8%",
        right: "8%",
        orient: "TB",
        edgeShape: "curve",
        edgeForkPosition: "50%",
        symbol: "circle",
        expandAndCollapse: true,
        initialTreeDepth: 5,
        animationDuration: 600,
        animationDurationUpdate: 800,
        animationEasing: "cubicOut"
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
  () => props.nodes,
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
.family-tree {
  width: 100%;
  height: 620px;
  position: relative;
  background:
    linear-gradient(135deg, rgba(255, 250, 243, 0.6) 0%, rgba(255, 255, 255, 0.4) 100%),
    radial-gradient(circle at 20% 20%, rgba(232, 143, 163, 0.08), transparent 45%),
    radial-gradient(circle at 80% 30%, rgba(107, 144, 212, 0.08), transparent 45%),
    radial-gradient(circle at 50% 80%, rgba(201, 179, 124, 0.06), transparent 50%),
    #fffefb;
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 8px 24px rgba(90, 67, 40, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(201, 179, 124, 0.2);
}

.tree-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tree-header {
  padding: 16px 20px 12px;
  background: linear-gradient(180deg, rgba(255, 250, 243, 0.95) 0%, transparent 100%);
  border-bottom: 1px solid rgba(201, 179, 124, 0.15);
}

.header-decoration {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.decoration-line {
  flex: 0 0 60px;
  height: 1px;
  background: linear-gradient(
    to right,
    transparent,
    rgba(201, 179, 124, 0.5),
    transparent
  );
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #5a4a3e;
  font-family: 'Source Han Serif CN', 'Noto Serif SC', serif;
  letter-spacing: 2px;
  position: relative;
}

.header-title::before,
.header-title::after {
  content: "◆";
  position: absolute;
  font-size: 8px;
  color: #c9b37c;
  opacity: 0.6;
}

.header-title::before {
  left: -16px;
}

.header-title::after {
  right: -16px;
}

.chart {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.empty-state {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
}

.empty-icon {
  font-size: 56px;
  opacity: 0.3;
  filter: grayscale(0.5);
  animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.3;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(1.05);
  }
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: #8a7a6a;
  font-family: 'Source Han Serif CN', 'Noto Serif SC', serif;
  letter-spacing: 1px;
}

.empty-hint {
  font-size: 13px;
  color: #a89d91;
  font-family: 'Source Han Serif CN', 'Noto Serif SC', serif;
  opacity: 0.8;
}
</style>
