<template>
  <div ref="chartRef" class="graph-view"></div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { GraphChart } from "echarts/charts";
import { TitleComponent, TooltipComponent, LegendComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

// 注册 ECharts 必须的组件
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

// --- 构建 ECharts 配置项 ---
const buildOption = () => {
  // 1. 处理高亮过滤逻辑
  const selectedIds = new Set();
  if (props.highlightOnly) {
    props.relations.forEach((relation) => {
      // 兼容性获取 ID
      const sId = relation.source?.id || relation.sourceEntityId || relation.source;
      const tId = relation.target?.id || relation.targetEntityId || relation.target;
      if (sId) selectedIds.add(String(sId));
      if (tId) selectedIds.add(String(tId));
    });
  }

  const allowedCategories = new Set(props.activeEntityCategories);
  const allowedRelationTypes = new Set(props.activeRelationTypes);

  // 2. 构建节点 (Nodes)
  const nodes = props.entities
    .filter((entity) => {
      // 类别过滤
      const allowed = allowedCategories.size === 0 || allowedCategories.has(entity.category);
      if (!allowed) return false;
      // 高亮过滤
      if (props.highlightOnly) return selectedIds.has(String(entity.id));
      return true;
    })
    .map((entity) => ({
      id: String(entity.id), // 统一转字符串，防止类型不匹配
      name: entity.label,
      value: entity.category,
      category: entity.category,
      symbolSize: 40,        // 节点大小
      draggable: true,       // ✅ 关键：允许单独拖拽这个节点
      itemStyle: {
        color: pickColor(entity.category),
        borderColor: '#fff',
        borderWidth: 2,
        shadowBlur: 10,
        shadowColor: 'rgba(0, 0, 0, 0.1)'
      },
      label: {
        show: true,
        position: 'inside',  // 文字显示在节点内部
        fontSize: 12,
        formatter: (p) => {
          // 名字太长则截断，例如 "李白" -> "李白", "很长的名字" -> "很长.."
          return p.name.length > 4 ? p.name.substring(0, 3) + '..' : p.name;
        }
      }
    }));

  // 3. 构建连线 (Edges/Links)
  const edges = props.relations
    .map((relation) => {
      // ✅ 核心修复：兼容多种后端返回格式
      // 无论后端给的是对象 source: {id: 1} 还是扁平字段 sourceEntityId: 1
      const sourceId = relation.source?.id || relation.sourceEntityId || relation.source;
      const targetId = relation.target?.id || relation.targetEntityId || relation.target;
      
      return {
        source: String(sourceId),
        target: String(targetId),
        value: relation.relationType || "关联" // 关系名称
      };
    })
    .filter((edge) => {
      // 过滤无效边
      if (!edge.source || !edge.target || edge.source === "undefined" || edge.target === "undefined") {
        return false;
      }

      // 类型过滤
      const relationAllowed = allowedRelationTypes.size === 0 || allowedRelationTypes.has(edge.value);
      if (!relationAllowed) return false;

      // 高亮过滤
      if (props.highlightOnly) {
        return selectedIds.has(edge.source) && selectedIds.has(edge.target);
      }
      return true;
    });

  // 4. 返回 ECharts 配置对象
  return {
    title: { show: false },
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === "node") {
          return `<strong>${params.data.name}</strong><br/>类型: ${params.data.value}`;
        }
        // 连线 Tooltip
        return `${params.name}<br/>关系: <strong>${params.data.value}</strong>`;
      }
    },
    series: [
      {
        type: "graph",
        layout: "force",
        roam: true, // ✅ 允许缩放和平移整个画布
        data: nodes,
        links: edges,
        
        // --- 样式配置 ---
        edgeSymbol: ['none', 'arrow'], // 箭头
        edgeSymbolSize: [4, 10],
        
        edgeLabel: {
          show: true,
          fontSize: 10,
          formatter: "{c}", // 显示关系名
          color: "#666",
          backgroundColor: '#fff', // 白底，防遮挡
          padding: [2, 4],
          borderRadius: 4
        },

        force: {
          repulsion: 400,    // 斥力：节点间距
          edgeLength: 150,   // 边长
          gravity: 0.1       // 重力
        },

        lineStyle: {
          color: "#a0a0a0",
          width: 1.5,
          curveness: 0.2,    // ✅ 曲线，防止重叠
          opacity: 0.8
        },

        emphasis: {
          focus: 'adjacency', // 悬停高亮相邻
          lineStyle: { width: 3, color: "#000" }
        }
      }
    ]
  };
};

// 颜色映射函数
const pickColor = (category) => {
  const map = {
    PERSON: "#e74c3c",       // 红
    LOCATION: "#2ecc71",     // 绿
    EVENT: "#f1c40f",        // 黄
    ORGANIZATION: "#3498db", // 蓝
    DYNASTY: "#9b59b6",      // 紫
    TIME: "#95a5a6"          // 灰
  };
  return map[category] || "#95a5a6";
};

// 渲染图表
const renderChart = () => {
  if (!chartRef.value) return;
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }
  chartInstance.clear(); // 清除旧数据，防止ID冲突
  chartInstance.setOption(buildOption());
};

// 生命周期与监听
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
  height: 100%;
  min-height: 560px;
  background: #fff;
  border-radius: 8px;
}
</style>