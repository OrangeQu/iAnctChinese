<template>
  <div class="workspace" v-loading="store.loading">
    <div class="stage-grid">
      <aside class="panel text-panel">
        <h3 class="section-title">原文</h3>
        <div v-if="store.selectedText?.content" class="text-content">
          <pre class="raw-text">{{ store.selectedText.content }}</pre>
        </div>
        <p v-else class="placeholder">请先上传文言文或从左侧列表选择一篇。</p>
        <el-divider />
        <div class="action-row">
          <el-button type="primary" @click="handleFullAnalysis">触发模型分析</el-button>
          <el-button type="warning" plain @click="handleClassify">大模型判断类型</el-button>
          <el-button @click="store.triggerAutoAnnotation">自动生成示例标注</el-button>
        </div>
        <div v-if="store.classification?.suggestedCategory" class="classification-tip">
          <el-alert title="模型分析建议" type="info" :closable="false" show-icon>
            <template #default>
              <p>当前类型：<strong>{{ translateCategory(store.selectedText?.category) }}</strong></p>
              <p>
                模型建议：<strong>{{ translateCategory(store.classification.suggestedCategory) }}</strong>
                （置信度：{{ ((store.classification.confidence || 0) * 100).toFixed(1) }}%）
              </p>
            </template>
          </el-alert>
        </div>
      </aside>

      <section class="panel annotation-panel">
      <div class="annotation-section">
        <h3 class="section-title">实体标注</h3>
        <el-form :model="entityForm" inline class="form-inline">
          <el-form-item label="名称">
            <el-input v-model="entityForm.label" placeholder="如 周瑜" />
          </el-form-item>
          <el-form-item label="类别">
            <el-select v-model="entityForm.category" placeholder="实体类别">
              <el-option label="人物" value="PERSON" />
              <el-option label="地点" value="LOCATION" />
              <el-option label="事件" value="EVENT" />
              <el-option label="组织" value="ORGANIZATION" />
              <el-option label="器物" value="OBJECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="起止">
            <el-input-number v-model="entityForm.startOffset" :min="0" />
            <span> - </span>
            <el-input-number v-model="entityForm.endOffset" :min="0" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitEntity">添加实体</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="entities" border size="small" height="220">
          <el-table-column prop="label" label="实体" width="120" />
          <el-table-column prop="category" label="类别" width="120" />
          <el-table-column prop="confidence" label="置信度" />
        </el-table>
      </div>
        <el-divider />
        <div class="annotation-section">
          <h3 class="section-title">关系标注</h3>
          <el-form :model="relationForm" inline class="form-inline">
            <el-form-item label="实体A">
              <el-select v-model="relationForm.sourceEntityId" placeholder="选择实体">
                <el-option
                  v-for="entity in entities"
                  :label="entity.label"
                  :value="entity.id"
                  :key="entity.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="实体B">
              <el-select v-model="relationForm.targetEntityId" placeholder="选择实体">
                <el-option
                  v-for="entity in entities"
                  :label="entity.label"
                  :value="entity.id"
                  :key="`target-${entity.id}`"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关系">
              <el-select v-model="relationForm.relationType" placeholder="关系类型">
                <el-option label="对抗" value="CONFLICT" />
                <el-option label="结盟" value="SUPPORT" />
                <el-option label="行旅" value="TRAVEL" />
                <el-option label="亲属" value="FAMILY" />
                <el-option label="时间" value="TEMPORAL" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitRelation">添加关系</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="relations" border size="small" height="200">
            <el-table-column prop="source.label" label="实体A" />
            <el-table-column prop="relationType" label="关系" width="140" />
            <el-table-column prop="target.label" label="实体B" />
          </el-table>
        </div>
      </section>
    </div>

    <SentencePanel
      :sections="sections"
      @auto-segment="handleAutoSegment"
      @update-section="handleUpdateSection"
    />
  </div>
</template>

<script setup>
import { computed, reactive, watch, onMounted } from "vue";
import { useRoute } from "vue-router";
import { useTextStore } from "@/store/textStore";
import { ElMessage } from "element-plus";
import SentencePanel from "@/components/visualizations/SentencePanel.vue";

const route = useRoute();
const store = useTextStore();

const loadText = async (id) => {
  if (!id) {
    return;
  }
  try {
    await store.selectText(id);
  } catch (error) {
    console.error("Failed to load text", error);
  }
};

onMounted(async () => {
  await loadText(route.params.id);
});

watch(
  () => route.params.id,
  async (id) => {
    await loadText(id);
  }
);

const entities = computed(() => store.entities);
const relations = computed(() => store.relations);
const sections = computed(() => store.sections);

const entityForm = reactive({
  label: "",
  category: "PERSON",
  startOffset: 0,
  endOffset: 0
});

const relationForm = reactive({
  sourceEntityId: null,
  targetEntityId: null,
  relationType: "CONFLICT"
});

const submitEntity = async () => {
  if (!entityForm.label) {
    ElMessage.warning("请填写实体名称");
    return;
  }
  await store.createEntityAnnotation({
    textId: store.selectedTextId,
    startOffset: entityForm.startOffset,
    endOffset: entityForm.endOffset,
    label: entityForm.label,
    category: entityForm.category,
    confidence: 0.9
  });
  ElMessage.success("实体已添加");
  entityForm.label = "";
};

const submitRelation = async () => {
  if (!relationForm.sourceEntityId || !relationForm.targetEntityId) {
    ElMessage.warning("请选择实体");
    return;
  }
  await store.createRelationAnnotation({
    textId: store.selectedTextId,
    sourceEntityId: relationForm.sourceEntityId,
    targetEntityId: relationForm.targetEntityId,
    relationType: relationForm.relationType,
    confidence: 0.8
  });
  ElMessage.success("关系已添加");
};

const handleAutoSegment = async () => {
  await store.autoSegmentSections();
  ElMessage.success("已重新生成句读结果");
};

const handleUpdateSection = async (section) => {
  await store.updateSection(section.id, {
    originalText: section.originalText,
    punctuatedText: section.punctuatedText,
    summary: section.summary
  });
  ElMessage.success("句读内容已更新");
};

const handleFullAnalysis = async () => {
  try {
    await store.runFullAnalysis();
    ElMessage.success("模型分析完成，已更新标注与句读");
  } catch (error) {
    ElMessage.error("模型分析失败，请稍后重试");
  }
};

const handleClassify = async () => {
  try {
    await store.classifySelectedText();
    ElMessage.success("模型已完成类型判断");
  } catch (error) {
    ElMessage.error("类型判断失败，请稍后重试");
  }
};

const translateCategory = (category) => {
  const map = {
    warfare: "战争纪实",
    travelogue: "游记地理",
    biography: "人物传记",
    unknown: "待识别",
    other: "其他"
  };
  return map[category] || category || "未知";
};
</script>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stage-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.text-content {
  max-height: 320px;
  overflow: auto;
  line-height: 1.8;
  padding-right: 8px;
  color: var(--muted);
}

.raw-text {
  white-space: pre-line;
  margin: 0;
  font-size: 15px;
  color: #4a443e;
}

.placeholder {
  color: var(--muted);
  margin: 0;
}

.action-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.classification-tip {
  margin-top: 12px;
}

.annotation-section {
  margin-bottom: 12px;
}

.form-inline :deep(.el-form-item) {
  margin-right: 12px;
}
</style>
