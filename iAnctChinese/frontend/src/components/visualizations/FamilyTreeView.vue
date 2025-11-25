<template>
  <div class="family-tree">
    <div v-if="!hasData" class="empty">暂无家谱数据</div>
    <div v-else ref="chartRef" class="chart"></div>
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

const genderColor = (name = "") => {
  // 粗略按字形判断父/母/女/妻等
  if (/母|妻|女|妣|姑|姨|嫂|妹/.test(name)) return "#e88fa3";
  if (/父|公|子|兄|叔|伯|弟|祖|先/.test(name)) return "#6b90d4";
  return "#c9b37c";
};

const enhanceNodes = (nodes) =>
  nodes.map((n) => ({
    ...n,
    itemStyle: {
      color: "#fffaf3",
      borderColor: genderColor(n.name),
      borderWidth: 2
    },
    label: {
      color: "#2f2b2a",
      fontSize: 13,
      backgroundColor: "rgba(255,255,255,0.8)",
      padding: [4, 8],
      borderRadius: 6
    },
    lineStyle: {
      color: "#c9b37c",
      width: 2.6,
      curveness: 0.25
    },
    children: n.children ? enhanceNodes(n.children) : []
  }));

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
      backgroundColor: "rgba(255,255,255,0.95)",
      borderColor: "#d1b17e",
      textStyle: { color: "#2f2b2a" },
      formatter: (params) => {
        const d = params.data;
        return `<strong>${d.name || ""}</strong>${d.relation ? "（" + d.relation + "）" : ""}`;
      }
    },
    series: [
      {
        type: "tree",
        data,
        top: "6%",
        left: "10%",
        bottom: "6%",
        right: "10%",
        orient: "TB",
        edgeShape: "curve",
        edgeForkPosition: "50%",
        symbol: "circle",
        symbolSize: 18,
        expandAndCollapse: true,
        initialTreeDepth: 5,
        animationDuration: 450,
        animationDurationUpdate: 650
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
  background: radial-gradient(circle at 25% 15%, rgba(220, 229, 243, 0.24), transparent 50%),
    radial-gradient(circle at 75% 30%, rgba(250, 225, 210, 0.24), transparent 50%),
    white;
  border-radius: 16px;
  overflow: hidden;
}

.chart {
  width: 100%;
  height: 100%;
}

.empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a8178;
  font-size: 14px;
}
</style>
