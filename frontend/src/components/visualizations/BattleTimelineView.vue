<template>
  <div ref="chartRef" class="battle-timeline">
    <div class="battle-overlay"></div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { LineChart, PictorialBarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([LineChart, PictorialBarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps({
  events: {
    type: Array,
    default: () => []
  }
});

const chartRef = ref();
let chartInstance;

const renderChart = () => {
  if (!chartRef.value) {
    return;
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }
  const categories = props.events.map((event) => event.phase);
  const intensity = props.events.map((event) => event.intensity || 0);

  chartInstance.setOption({
    tooltip: {
      trigger: "axis",
      borderColor: "#d16a5d",
      backgroundColor: "rgba(255,255,255,0.95)",
      textStyle: { color: "#2f2b2a" },
      formatter: (params) => {
        const { name, dataIndex } = params[0];
        const event = props.events[dataIndex];
        return `<strong>${name}</strong><br/>${event.description}<br/>对手：${event.opponent || "未知"}`;
      }
    },
    grid: {
      left: "5%",
      right: "5%",
      bottom: "10%",
      top: "10%",
      containLabel: true
    },
    xAxis: {
      type: "category",
      data: categories,
      axisLine: { lineStyle: { color: "#c5b09b" } },
      axisLabel: { color: "#5d554b" }
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 10,
      name: "战事强度",
      nameTextStyle: { color: "#5d554b", fontWeight: 600 },
      axisLine: { show: false },
      axisLabel: { color: "#968d83" },
      splitLine: { lineStyle: { color: "rgba(0,0,0,0.08)" } }
    },
    series: [
      {
        type: "pictorialBar",
        symbolSize: [18, 18],
        symbolOffset: [0, -8],
        z: 12,
        data: intensity.map((value) => ({
          value,
          symbol: "circle",
          itemStyle: { color: "#f6c189", borderColor: "#d16a5d", borderWidth: 2 }
        }))
      },
      {
        type: "bar",
        data: intensity,
        barWidth: 32,
        itemStyle: {
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: "#f6c189" },
              { offset: 1, color: "#e77761" }
            ]
          },
          borderRadius: [12, 12, 0, 0]
        }
      },
      {
        type: "line",
        data: intensity,
        smooth: true,
        symbol: "circle",
        symbolSize: 10,
        lineStyle: {
          color: "#f59a5f",
          width: 3
        },
        areaStyle: {
          color: "rgba(245, 154, 95, 0.22)"
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
  () => props.events,
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
.battle-timeline {
  position: relative;
  width: 100%;
  height: 360px;
  border-radius: 24px;
  overflow: hidden;
  background: radial-gradient(circle at top left, rgba(255, 244, 229, 0.8), rgba(232, 240, 247, 0.9));
}

.battle-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: url("data:image/svg+xml,%3Csvg width='200' height='200' viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Ccircle cx='100' cy='100' r='98' fill='none' stroke='%23f4dbc0' stroke-width='0.4'/%3E%3C/svg%3E")
    repeat;
  opacity: 0.35;
}
</style>
