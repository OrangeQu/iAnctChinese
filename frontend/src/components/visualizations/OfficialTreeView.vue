<template>
  <div class="official-tree-view">
    <div v-if="!hasData" class="empty-state">
      <div class="empty-icon">🏛️</div>
      <div class="empty-text">暂无官职数据</div>
      <div class="empty-hint">文本中未识别到明确的官职体系</div>
    </div>
    <div v-else class="tree-container">
      <!-- 工具栏 -->
      <div class="tree-toolbar">
        <div class="toolbar-left">
          <h3 class="view-title">官职体系图</h3>
          <span class="official-count">共 {{ totalOfficials }} 位官员</span>
        </div>
      </div>

      <!-- 主要内容区域 -->
      <div class="content-wrapper">
        <!-- 左侧树图 -->
        <div class="chart-section" :class="{ 'has-detail': selectedOfficial }">
          <div ref="chartRef" class="chart"></div>
        </div>

        <!-- 右侧详情卡片 -->
        <transition name="slide-in">
          <div v-if="selectedOfficial" class="detail-card">
            <div class="card-header" :style="getHeaderStyle(selectedOfficial.department)">
              <div class="header-decoration"></div>
              <div class="official-title-group">
                <h4 class="official-name">{{ selectedOfficial.name }}</h4>
                <span class="level-badge" :style="getLevelBadgeStyle(selectedOfficial.level)">
                  {{ selectedOfficial.level }}
                </span>
              </div>
              <button class="card-close-btn" @click="closeDetail" title="关闭详情">
                ✕
              </button>
            </div>

            <div class="card-body">
              <!-- 官职信息 -->
              <div class="info-section">
                <div class="section-label">
                  <span class="label-icon">📜</span>
                  <span>官职</span>
                </div>
                <div class="section-content official-position" :style="getPositionStyle(selectedOfficial.department)">
                  {{ selectedOfficial.position }}
                </div>
              </div>

              <!-- 部门信息 -->
              <div class="info-section">
                <div class="section-label">
                  <span class="label-icon">🏢</span>
                  <span>所属部门</span>
                </div>
                <div class="section-content">
                  <span class="tag department-tag" :style="getDepartmentTagStyle(selectedOfficial.department)">
                    {{ selectedOfficial.department }}
                  </span>
                </div>
              </div>

              <!-- 职责说明 -->
              <div class="info-section">
                <div class="section-label">
                  <span class="label-icon">💼</span>
                  <span>职责说明</span>
                </div>
                <div class="section-content duties-content">
                  {{ getOfficialDuties(selectedOfficial.position, selectedOfficial.department) }}
                </div>
              </div>

              <!-- 描述 -->
              <div class="info-section" v-if="selectedOfficial.description">
                <div class="section-label">
                  <span class="label-icon">📖</span>
                  <span>详细描述</span>
                </div>
                <div class="section-content">{{ selectedOfficial.description }}</div>
              </div>

              <!-- 下属列表 -->
              <div class="info-section" v-if="selectedOfficial.subordinates && selectedOfficial.subordinates.length > 0">
                <div class="section-label">
                  <span class="label-icon">👥</span>
                  <span>下属官员（{{ selectedOfficial.subordinates.length }}人）</span>
                </div>
                <div class="section-content">
                  <div class="subordinate-list">
                    <span
                      v-for="(sub, idx) in selectedOfficial.subordinates"
                      :key="sub.name"
                      class="subordinate-item"
                      :style="getSubordinateStyle(idx)"
                      @click="selectOfficial(sub)"
                    >
                      <span class="subordinate-rank">{{ idx + 1 }}</span>
                      {{ sub.name }} - {{ sub.position }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- 点击提示 -->
              <div class="click-hint">💡 再次点击树节点可关闭详情</div>
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
import { TreeChart } from "echarts/charts";
import { TitleComponent, TooltipComponent, LegendComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([TreeChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const props = defineProps({
  nodes: {
    type: Array,
    default: () => []
  }
});

const chartRef = ref();
const selectedOfficial = ref(null);
let chartInstance;

const hasData = computed(() => Array.isArray(props.nodes) && props.nodes.length > 0);

const totalOfficials = computed(() => {
  if (!props.nodes || props.nodes.length === 0) return 0;

  const countNodes = (nodes) => {
    let count = 0;
    for (const node of nodes) {
      count++;
      if (node.subordinates && node.subordinates.length > 0) {
        count += countNodes(node.subordinates);
      }
    }
    return count;
  };

  return countNodes(props.nodes);
});

// 选择官员
const selectOfficial = (official) => {
  // 如果点击的是同一个官员，则关闭详情卡片
  if (selectedOfficial.value && selectedOfficial.value.name === official.name) {
    selectedOfficial.value = null;
  } else {
    selectedOfficial.value = official;
  }
};

// 关闭详情
const closeDetail = () => {
  selectedOfficial.value = null;
};

// 获取品级徽章样式
const getLevelBadgeStyle = (level) => {
  const colorMap = {
    '一品': { background: 'linear-gradient(135deg, #d4380d 0%, #ff4d4f 100%)', color: '#fff' },
    '二品': { background: 'linear-gradient(135deg, #fa541c 0%, #ff7a45 100%)', color: '#fff' },
    '三品': { background: 'linear-gradient(135deg, #fa8c16 0%, #ffa940 100%)', color: '#fff' },
    '四品': { background: 'linear-gradient(135deg, #faad14 0%, #ffc53d 100%)', color: '#fff' },
    '五品': { background: 'linear-gradient(135deg, #fadb14 0%, #ffec3d 100%)', color: '#333' },
    '六品': { background: 'linear-gradient(135deg, #a0d911 0%, #bae637 100%)', color: '#333' },
    '七品': { background: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)', color: '#fff' },
    '八品': { background: 'linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%)', color: '#fff' },
    '九品': { background: 'linear-gradient(135deg, #1890ff 0%, #40a9ff 100%)', color: '#fff' },
    '未定品': { background: 'linear-gradient(135deg, #95a5a6 0%, #bdc3c7 100%)', color: '#fff' }
  };
  return colorMap[level] || colorMap['未定品'];
};

// 根据部门获取卡片头部样式
const getHeaderStyle = (department) => {
  const styleMap = {
    '六部': {
      background: 'linear-gradient(135deg, #fff1f0 0%, #ffe7e6 50%, #ffffff 100%)',
      borderBottom: '3px solid #ff7875'
    },
    '都察院': {
      background: 'linear-gradient(135deg, #e6f7ff 0%, #d6efff 50%, #ffffff 100%)',
      borderBottom: '3px solid #40a9ff'
    },
    '翰林院': {
      background: 'linear-gradient(135deg, #f9f0ff 0%, #efdbff 50%, #ffffff 100%)',
      borderBottom: '3px solid #b37feb'
    },
    '地方政府': {
      background: 'linear-gradient(135deg, #fcffe6 0%, #f4ffb8 50%, #ffffff 100%)',
      borderBottom: '3px solid #bae637'
    },
    '军事系统': {
      background: 'linear-gradient(135deg, #fff7e6 0%, #ffe7ba 50%, #ffffff 100%)',
      borderBottom: '3px solid #ffa940'
    },
    '中央机构': {
      background: 'linear-gradient(135deg, #f9f6ee 0%, #fdfbf7 50%, #ffffff 100%)',
      borderBottom: '3px solid #d4af37'
    }
  };
  return styleMap[department] || styleMap['中央机构'];
};

// 根据部门获取官职内容样式
const getPositionStyle = (department) => {
  const styleMap = {
    '六部': {
      background: 'linear-gradient(135deg, #fff1f0 0%, #ffe7e6 100%)',
      borderLeft: '4px solid #ff4d4f'
    },
    '都察院': {
      background: 'linear-gradient(135deg, #e6f7ff 0%, #d6efff 100%)',
      borderLeft: '4px solid #1890ff'
    },
    '翰林院': {
      background: 'linear-gradient(135deg, #f9f0ff 0%, #efdbff 100%)',
      borderLeft: '4px solid #722ed1'
    },
    '地方政府': {
      background: 'linear-gradient(135deg, #fcffe6 0%, #f4ffb8 100%)',
      borderLeft: '4px solid #52c41a'
    },
    '军事系统': {
      background: 'linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%)',
      borderLeft: '4px solid #fa8c16'
    },
    '中央机构': {
      background: 'linear-gradient(135deg, #fffbf0 0%, #fff8e6 100%)',
      borderLeft: '4px solid #d4af37'
    }
  };
  return styleMap[department] || styleMap['中央机构'];
};

// 根据部门获取部门标签样式
const getDepartmentTagStyle = (department) => {
  const styleMap = {
    '六部': {
      background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)',
      color: '#cf1322',
      borderColor: '#ff7875',
      boxShadow: '0 2px 8px rgba(255, 77, 79, 0.25)'
    },
    '都察院': {
      background: 'linear-gradient(135deg, #e6f7ff 0%, #91d5ff 100%)',
      color: '#0050b3',
      borderColor: '#40a9ff',
      boxShadow: '0 2px 8px rgba(24, 144, 255, 0.25)'
    },
    '翰林院': {
      background: 'linear-gradient(135deg, #f9f0ff 0%, #d3adf7 100%)',
      color: '#531dab',
      borderColor: '#b37feb',
      boxShadow: '0 2px 8px rgba(114, 46, 209, 0.25)'
    },
    '地方政府': {
      background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)',
      color: '#237804',
      borderColor: '#95de64',
      boxShadow: '0 2px 8px rgba(82, 196, 26, 0.25)'
    },
    '军事系统': {
      background: 'linear-gradient(135deg, #fff7e6 0%, #ffd591 100%)',
      color: '#ad4e00',
      borderColor: '#ffa940',
      boxShadow: '0 2px 8px rgba(250, 140, 22, 0.25)'
    },
    '中央机构': {
      background: 'linear-gradient(135deg, #fffbf0 0%, #ffe7ba 100%)',
      color: '#8b7355',
      borderColor: '#d4af37',
      boxShadow: '0 2px 8px rgba(212, 175, 55, 0.25)'
    }
  };
  return styleMap[department] || styleMap['中央机构'];
};

// 根据索引获取下属列表项样式（交替颜色）
const getSubordinateStyle = (index) => {
  const colors = [
    { bg: 'linear-gradient(135deg, #e6f7ff 0%, #d6efff 100%)', border: '#40a9ff', color: '#1890ff' },
    { bg: 'linear-gradient(135deg, #f9f0ff 0%, #efdbff 100%)', border: '#b37feb', color: '#722ed1' },
    { bg: 'linear-gradient(135deg, #fcffe6 0%, #f4ffb8 100%)', border: '#95de64', color: '#52c41a' },
    { bg: 'linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%)', border: '#ffa940', color: '#fa8c16' },
    { bg: 'linear-gradient(135deg, #fff1f0 0%, #ffe7e6 100%)', border: '#ff7875', color: '#ff4d4f' }
  ];
  const style = colors[index % colors.length];
  return {
    background: style.bg,
    borderColor: style.border,
    color: style.color
  };
};

// 获取官职职责说明
const getOfficialDuties = (position, department) => {
  const dutyMap = {
    // 一品
    '宰相': '辅佐皇帝处理国家政务，统领百官，决断军国大事。',
    '丞相': '辅佐皇帝治理天下，总揽行政、军事、财政等各方面事务。',
    '太师': '辅佐君主，负责教导储君，参与朝政决策。',
    '太傅': '辅弼皇帝，教导太子，参与重大决策。',
    '太保': '保护皇室安全，辅佐君主治理国家。',
    '大学士': '参与军国大政的商议和决策，起草诏书，辅佐皇帝处理政务。',

    // 二品
    '尚书': '掌管六部（吏、户、礼、兵、刑、工）事务，处理具体政务。',
    '都督': '统领地方军队，负责军事防御和作战指挥。',
    '总兵': '统率一方军队，负责地方军事防务。',

    // 三品
    '侍郎': '协助尚书处理部务，为尚书的副手。',
    '御史': '监察百官，纠察违法失职行为，维护朝纲。',
    '参将': '协助总兵管理军务，指挥作战。',

    // 四品
    '郎中': '掌管部内具体事务，处理日常行政工作。',
    '员外郎': '协助郎中处理部务，负责具体事务的执行。',
    '游击': '率领游击部队，机动作战，负责巡逻防守。',
    '知府': '管理一府政务，负责辖区内的行政、司法、财政等事务。',

    // 五品
    '给事中': '掌管朝廷奏章的审议和驳正，有封驳权。',
    '主簿': '协助长官处理文书、档案等事务。',
    '守备': '驻守城池或要塞，负责防守任务。',

    // 地方官
    '刺史': '监察地方官员，巡视州郡，纠察不法。',
    '知县': '管理一县政务，负责地方行政、司法、税收等事务。',
    '县令': '管理县级行政事务，为一县之长。',
    '县丞': '协助县令处理县务，为县令的副手。',

    // 特殊职位
    '翰林': '负责起草诏书、编修史书，为皇帝的文学侍从。',
    '学士': '参与编修、讲学，为皇帝的顾问和秘书。'
  };

  // 尝试精确匹配
  if (dutyMap[position]) {
    return dutyMap[position];
  }

  // 尝试模糊匹配
  for (const [key, value] of Object.entries(dutyMap)) {
    if (position.includes(key) || key.includes(position)) {
      return value;
    }
  }

  // 根据部门给出默认职责
  const departmentDutyMap = {
    '六部': '负责具体部务的执行和管理。',
    '都察院': '监察百官，纠察违法失职行为。',
    '翰林院': '负责起草诏书、编修典籍、培养人才。',
    '地方政府': '管理地方政务，维护一方安定。',
    '军事系统': '统率军队，负责军事防务和作战指挥。',
    '中央机构': '参与朝政，协助皇帝处理国家事务。'
  };

  return departmentDutyMap[department] || '负责相关政务的处理和执行。';
};

// 转换数据格式
const transformNode = (node) => {
  return {
    name: node.name,
    value: node.position || '未知官职',
    nodeData: node, // 保存完整节点数据
    label: {
      show: true,
      formatter: (params) => {
        const data = params.data;
        return `{name|${data.name}}\n{pos|${data.value}}\n{level|${node.level || ''}}`;
      },
      rich: {
        name: {
          fontSize: 14,
          fontWeight: 'bold',
          color: '#2c3e50',
          padding: [4, 0, 2, 0]
        },
        pos: {
          fontSize: 12,
          color: '#5470c6',
          padding: [2, 0]
        },
        level: {
          fontSize: 11,
          color: '#91cc75',
          padding: [2, 0, 4, 0]
        }
      }
    },
    itemStyle: {
      color: getLevelColor(node.level),
      borderColor: '#fff',
      borderWidth: 2.5,
      shadowBlur: 8,
      shadowColor: 'rgba(0, 0, 0, 0.2)',
      shadowOffsetY: 2
    },
    children: node.subordinates && node.subordinates.length > 0
      ? node.subordinates.map(transformNode)
      : []
  };
};

// 根据品级获取颜色
const getLevelColor = (level) => {
  const colorMap = {
    '一品': '#d4380d',
    '二品': '#fa541c',
    '三品': '#fa8c16',
    '四品': '#faad14',
    '五品': '#fadb14',
    '六品': '#a0d911',
    '七品': '#52c41a',
    '八品': '#13c2c2',
    '九品': '#1890ff',
    '未定品': '#95a5a6'
  };
  return colorMap[level] || '#95a5a6';
};

// 递归查找节点
const findNodeByName = (name, nodes) => {
  for (const node of nodes) {
    if (node.name === name) return node;
    if (node.subordinates && node.subordinates.length > 0) {
      const found = findNodeByName(name, node.subordinates);
      if (found) return found;
    }
  }
  return null;
};

const buildOption = () => {
  if (!hasData.value) {
    return {
      title: {
        text: '暂无官职数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#999', fontSize: 14 }
      }
    };
  }

  const treeData = props.nodes.map(transformNode);

  return {
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      backgroundColor: 'rgba(255, 252, 247, 0.98)',
      borderColor: '#d4af37',
      borderWidth: 2,
      textStyle: {
        color: '#2f2b2a',
        fontSize: 13,
        fontWeight: 600
      },
      padding: [14, 18],
      extraCssText: 'border-radius: 12px; box-shadow: 0 8px 24px rgba(212, 175, 55, 0.25);',
      formatter: (params) => {
        const node = findNodeByName(params.name, props.nodes);
        if (!node) return params.name;

        let html = `<div style="font-weight: 700; font-size: 16px; margin-bottom: 10px; color: #2c3e50; border-bottom: 1.5px solid #e8d5b5; padding-bottom: 6px;">${node.name}</div>`;
        html += `<div style="margin-bottom: 5px;"><strong style="color: #8a8178;">官职：</strong><span style="color: #5470c6; font-weight: 600;">${node.position || '未知'}</span></div>`;
        html += `<div style="margin-bottom: 5px;"><strong style="color: #8a8178;">品级：</strong><span style="color: ${getLevelColor(node.level)}; font-weight: 700;">${node.level || '未定品'}</span></div>`;
        html += `<div style="margin-bottom: 5px;"><strong style="color: #8a8178;">部门：</strong><span style="color: #1890ff; font-weight: 600;">${node.department || '未知'}</span></div>`;
        html += `<div style="color: #a08968; font-size: 12px; margin-top: 8px; font-style: italic;">💡 点击查看详细职责</div>`;
        return html;
      }
    },
    series: [
      {
        type: 'tree',
        data: treeData,
        top: '8%',
        left: '12%',
        bottom: '8%',
        right: '12%',
        symbolSize: 16,
        orient: 'TB',
        label: {
          position: 'top',
          verticalAlign: 'middle',
          align: 'center',
          fontSize: 12,
          backgroundColor: 'rgba(255, 255, 255, 0.98)',
          borderRadius: 8,
          padding: [10, 14],
          shadowBlur: 15,
          shadowColor: 'rgba(0, 0, 0, 0.15)',
          shadowOffsetY: 3
        },
        leaves: {
          label: {
            position: 'bottom',
            verticalAlign: 'middle',
            align: 'center'
          }
        },
        expandAndCollapse: false, // 禁用展开收起
        animationDuration: 650,
        animationDurationUpdate: 850,
        animationEasing: 'cubicOut',
        lineStyle: {
          color: '#d4af37',
          width: 2.5,
          curveness: 0.4,
          opacity: 0.6
        },
        emphasis: {
          focus: 'descendant',
          itemStyle: {
            borderWidth: 4,
            shadowBlur: 25,
            shadowColor: 'rgba(212, 175, 55, 0.5)',
            shadowOffsetY: 4
          },
          lineStyle: {
            width: 3,
            opacity: 1
          }
        }
      }
    ]
  };
};

const renderChart = () => {
  if (!chartRef.value || !hasData.value) return;

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);

    // 添加点击事件
    chartInstance.on('click', (params) => {
      if (params.componentType === 'series') {
        const node = findNodeByName(params.name, props.nodes);
        if (node) {
          selectOfficial(node);
        }
      }
    });
  }

  chartInstance.clear();
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
  () => props.nodes,
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
.official-tree-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 560px;
  background: linear-gradient(135deg, #fdfbf7 0%, #f5f2ed 50%, #fafafa 100%);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

/* 工具栏 */
.tree-toolbar {
  background: linear-gradient(135deg, rgba(255, 252, 245, 0.98) 0%, rgba(255, 255, 255, 0.95) 100%);
  padding: 18px 28px;
  border-bottom: 2px solid #f0ebe0;
  display: flex;
  align-items: center;
  backdrop-filter: blur(12px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.view-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  background: linear-gradient(135deg, #2c3e50 0%, #3d4f64 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 0.5px;
}

.official-count {
  font-size: 13px;
  color: #6a645f;
  font-weight: 700;
  padding: 6px 16px;
  background: linear-gradient(135deg, #fff9f0 0%, #fef5e7 100%);
  border-radius: 999px;
  border: 1.5px solid #e8d5b5;
  box-shadow: 0 2px 8px rgba(212, 175, 55, 0.15);
  transition: all 0.3s ease;
}

.official-count:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(212, 175, 55, 0.25);
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
.tree-container {
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
  background: linear-gradient(135deg, #ffffff 0%, #fafafa 100%);
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15), 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  border: 2px solid #e8e5e0;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.3s ease;
}

.detail-card:hover {
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.18), 0 4px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  padding: 24px 28px;
  background: linear-gradient(135deg, #f9f6ee 0%, #fdfbf7 50%, #ffffff 100%);
  border-bottom: 2px solid #e8e5e0;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.header-decoration {
  position: absolute;
  top: 0;
  right: 0;
  width: 200px;
  height: 100%;
  background: linear-gradient(135deg, transparent 0%, rgba(212, 175, 55, 0.08) 100%);
  pointer-events: none;
}

.header-decoration::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(212, 175, 55, 0.15) 0%, transparent 70%);
  border-radius: 50%;
}

.official-title-group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 40px;
}

.official-name {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  flex: 1;
  letter-spacing: 0.3px;
}

.level-badge {
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.2), 0 1px 4px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  letter-spacing: 0.5px;
}

.level-badge:hover {
  transform: translateY(-1px) scale(1.05);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25), 0 2px 6px rgba(0, 0, 0, 0.15);
}

.card-close-btn {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #f8f8f8 0%, #f0f0f0 100%);
  color: #999;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.card-close-btn:hover {
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a52 100%);
  color: white;
  transform: rotate(90deg) scale(1.1);
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.4);
}

.card-body {
  flex: 1;
  padding: 24px 28px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  cursor: default;
}

.card-body::-webkit-scrollbar {
  width: 8px;
}

.card-body::-webkit-scrollbar-track {
  background: #f5f5f5;
  border-radius: 4px;
}

.card-body::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #d4af37 0%, #c9a135 100%);
  border-radius: 4px;
  transition: background 0.3s;
}

.card-body::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #e0bd4d 0%, #d4af37 100%);
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 13px;
  font-weight: 800;
  color: #8a8178;
  display: flex;
  align-items: center;
  gap: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 2px;
}

.label-icon {
  font-size: 16px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
  animation: iconFloat 3s ease-in-out infinite;
}

@keyframes iconFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

.section-content {
  font-size: 14px;
  color: #2f2b2a;
  line-height: 1.7;
  padding: 14px 16px;
  background: linear-gradient(135deg, #fafafa 0%, #f8f8f8 100%);
  border-radius: 10px;
  border: 1.5px solid #e8e5e0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  transition: all 0.2s ease;
}

.section-content:hover {
  border-color: #d4af37;
  box-shadow: 0 3px 12px rgba(212, 175, 55, 0.1);
}

.official-position {
  font-size: 17px;
  font-weight: 700;
  background: linear-gradient(135deg, #5470c6 0%, #6b8dd6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 0.3px;
  border-left-width: 4px;
  padding-left: 16px;
  position: relative;
}

.official-position::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 60%;
  border-radius: 2px;
}

.duties-content {
  background: linear-gradient(135deg, #fafafa 0%, #f0f7ff 100%);
  border-left: 4px solid #1890ff;
  padding-left: 16px;
  font-style: italic;
  color: #4a5568;
  position: relative;
}

.duties-content::before {
  content: '"';
  position: absolute;
  left: 8px;
  top: 8px;
  font-size: 32px;
  color: rgba(24, 144, 255, 0.15);
  font-family: Georgia, serif;
  line-height: 1;
}

.duties-content::after {
  content: '"';
  position: absolute;
  right: 8px;
  bottom: 4px;
  font-size: 32px;
  color: rgba(24, 144, 255, 0.15);
  font-family: Georgia, serif;
  line-height: 1;
}

.tag {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  border: 1.5px solid;
  transition: all 0.3s ease;
  letter-spacing: 0.3px;
  cursor: default;
}

.department-tag:hover {
  transform: translateY(-2px) scale(1.05);
}

.subordinate-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.subordinate-item {
  padding: 10px 14px 10px 48px;
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #2c3e50;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1.5px solid #d9d9d9;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
  position: relative;
}

.subordinate-rank {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: white;
  font-size: 11px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(24, 144, 255, 0.3);
}

.subordinate-item:hover {
  background: linear-gradient(135deg, #e6f7ff 0%, #d6efff 100%);
  border-color: #40a9ff;
  color: #1890ff;
  transform: translateX(6px) translateY(-1px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.2);
  padding-left: 52px;
}

.subordinate-item:hover .subordinate-rank {
  transform: translateY(-50%) scale(1.15) rotate(360deg);
  transition: transform 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.subordinate-item:active {
  transform: translateX(4px) translateY(0);
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

/* 滑入动画 */
.slide-in-enter-active {
  animation: slideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-in-leave-active {
  animation: slideIn 0.3s cubic-bezier(0.4, 0, 1, 1) reverse;
}

@keyframes slideIn {
  0% {
    opacity: 0;
    transform: translateX(40px) scale(0.96);
  }
  60% {
    opacity: 1;
    transform: translateX(-4px) scale(1.01);
  }
  100% {
    opacity: 1;
    transform: translateX(0) scale(1);
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
