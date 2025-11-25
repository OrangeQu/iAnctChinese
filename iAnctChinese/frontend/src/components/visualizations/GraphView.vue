<template>
  <div ref="chartRef" class="graph-view"></div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { GraphChart } from "echarts/charts";
import { TitleComponent, TooltipComponent, LegendComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([GraphChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const props = defineProps({
  entities: {
    type: Array,
    default: () => []
  },
  relations: {
    type: Array,
    default: () => []
  },
  highlightOnly: {
    type: Boolean,
    default: false
  },
  activeEntityCategories: {
    type: Array,
    default: () => []
  },
  activeRelationTypes: {
    type: Array,
    default: () => []
  }
});

const chartRef = ref();
let chartInstance;

const buildOption = () => {
  const selectedIds = new Set();
  if (props.highlightOnly) {
    props.relations.forEach((relation) => {
      const sourceId = relation.source?.id;
      const targetId = relation.target?.id;
      if (sourceId) {
        selectedIds.add(sourceId);
      }
      if (targetId) {
        selectedIds.add(targetId);
      }
    });
  }

  const allowedCategories = new Set(props.activeEntityCategories);
  const allowedRelationTypes = new Set(props.activeRelationTypes);

  const nodes = props.entities
    .filter((entity) => {
      const allowed = allowedCategories.size === 0 || allowedCategories.has(entity.category);
      if (!allowed) {
        return false;
      }
      if (!props.highlightOnly) {
        return true;
      }
      return selectedIds.has(entity.id);
    })
    .map((entity) => ({
      id: entity.id,
      name: entity.label,
      value: entity.category,
      category: entity.category,
      symbolSize: 30,
      itemStyle: {
        color: pickColor(entity.category)
      }
    }));

  const edges = props.relations
    .filter((relation) => {
      const relationAllowed =
        allowedRelationTypes.size === 0 || allowedRelationTypes.has(relation.relationType);
      if (!relationAllowed) {
        return false;
      }
      if (props.highlightOnly) {
        return (
          selectedIds.has(relation.source?.id) && selectedIds.has(relation.target?.id)
        );
      }
      return true;
    })
    .map((relation) => ({
      source: relation.source?.id,
      target: relation.target?.id,
      value: relation.relationType
    }))
    .filter((edge) => edge.source && edge.target);

  return {
    title: {
      text: "知识图谱",
      left: "center"
    },
    tooltip: {
      formatter: (params) => {
        if (params.dataType === "node") {
          return `${params.data.name}<br/>${params.data.value}`;
        }
        return `${params.data.value}`;
      }
    },
    series: [
      {
        type: "graph",
        layout: "force",
        roam: true,
        data: nodes,
        links: edges,
        label: {
          show: true
        },
        force: {
          repulsion: 120,
          edgeLength: 80
        },
        lineStyle: {
          color: "#8b8175"
        }
      }
    ]
  };
};

const pickColor = (category) => {
  const map = {
    PERSON: "#d16a5d",
    LOCATION: "#3a7a87",
    EVENT: "#a58938",
    ORGANIZATION: "#6564a5"
  };
  return map[category] || "#9ea0a8";
};

const renderChart = () => {
  if (!chartRef.value) {
    return;
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }
  chartInstance.setOption(buildOption());
};

onMounted(() => {
  renderChart();
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose();
  }
  window.removeEventListener("resize", handleResize);
});

watch(
  () => [props.entities, props.relations, props.highlightOnly, props.activeEntityCategories, props.activeRelationTypes],
  renderChart,
  { deep: true }
);

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize();
  }
};
</script>

<style scoped>
.graph-view {
  width: 100%;
  height: 520px;
}
</style>
