<template>
  <div class="workspace" v-loading="store.loading">
    <div class="stage-grid">
      <aside class="panel text-panel">
        <div class="panel-head">
          <h3 class="section-title">原文</h3>
          <el-button link @click="goBackToDocuments">返回文档管理</el-button>
        </div>
        <QuillEditor
          v-if="store.selectedText"
          v-model:content="editableContent"
          class="text-editor"
          theme="snow"
          content-type="text"
          :read-only="savingContent"
          @blur="handleContentSave()"
        />
        <p v-else class="placeholder">请先上传文言文或从左侧列表选择一篇文章</p>
        <el-divider />
        <div class="action-row">
          <el-select
            v-model="selectedModel"
            size="small"
            style="width: 260px"
            placeholder="选择大模型"
            filterable
          >
            <el-option
              v-for="item in llmModels"
              :key="item.id"
              :label="item.isThinking ? `【深度思考】${item.label}` : item.label"
              :value="item.id"
            />
          </el-select>
          <el-button type="primary" :loading="store.analysisRunning" @click="handleFullAnalysis">触发模型分析</el-button>
          <el-button type="success" plain :loading="savingContent" @click="handleContentSave(true)">
            保存原文
          </el-button>
          <el-button type="warning" plain :loading="store.classifyRunning" @click="handleClassify">大模型判断类型</el-button>
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
              <el-input v-model="entityForm.label" placeholder="例：周瑜" />
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
                  :key="entity.id"
                  :label="entity.label"
                  :value="entity.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="实体B">
              <el-select v-model="relationForm.targetEntityId" placeholder="选择实体">
                <el-option
                  v-for="entity in entities"
                  :key="`target-${entity.id}`"
                  :label="entity.label"
                  :value="entity.id"
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
            <el-table-column prop="relationType" label="关系类型" />
            <el-table-column prop="target.label" label="实体B" />
            <el-table-column prop="confidence" label="置信度" />
          </el-table>
        </div>
      </section>

      <section class="panel visualization-panel">
        <div class="panel-head">
          <h3 class="section-title">可视化视图</h3>
          <el-radio-group v-model="activeView" size="small">
            <el-radio-button label="graph">知识图谱</el-radio-button>
            <el-radio-button label="map">地图轨迹</el-radio-button>
          </el-radio-group>
        </div>
        <div class="view-container">
          <GraphView
            v-if="activeView === 'graph'"
            :entities="entities"
            :relations="relations"
            style="height: 400px; width: 100%"
          />
          <MapView
            v-if="activeView === 'map'"
            :points="mapPoints"
            style="height: 400px; width: 100%"
          />
        </div>
      </section>

      <section class="panel sentence-panel">
        <h3 class="section-title">句读/分段</h3>
        <div class="section-actions">
          <el-button size="small" @click="handleAutoSegment">自动推荐句读</el-button>
        </div>
        <div class="segments" v-if="sections.length">
          <div v-for="section in sections" :key="section.id" class="segment-card">
            <div class="segment-col">
              <div class="segment-label">原文</div>
              <div class="segment-text original">{{ section.originalText || "（空）" }}</div>
            </div>
            <div class="segment-col">
              <div class="segment-label">句读</div>
              <el-input
                type="textarea"
                v-model="section.punctuatedText"
                :autosize="{ minRows: 3, maxRows: 6 }"
                placeholder="添加句读"
                @blur="handleUpdateSection(section)"
              />
            </div>
            <div class="segment-col">
              <div class="segment-label">摘要</div>
              <el-input
                type="textarea"
                v-model="section.summary"
                :autosize="{ minRows: 3, maxRows: 6 }"
                placeholder="一句话摘要"
                @blur="handleUpdateSection(section)"
              />
            </div>
          </div>
        </div>
        <p v-else class="placeholder">暂无句读分段，请先自动推荐或手动新增。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import { QuillEditor } from "@vueup/vue-quill";
import "@vueup/vue-quill/dist/vue-quill.snow.css";
import GraphView from "@/components/visualizations/GraphView.vue";
import MapView from "@/components/visualizations/MapView.vue";

const router = useRouter();
const store = useTextStore();

const activeView = ref("graph");
const mapPoints = computed(() => store.insights?.mapPoints || []);

const llmModels = [
  { id: "deepseek-ai/DeepSeek-V3.2-Exp", label: "deepseek-ai/DeepSeek-V3.2-Exp", isThinking: false },
  { id: "Pro/deepseek-ai/DeepSeek-V3.2-Exp", label: "Pro/deepseek-ai/DeepSeek-V3.2-Exp", isThinking: false },
  { id: "inclusionAI/Ling-1T", label: "inclusionAI/Ling-1T", isThinking: false },
  { id: "zai-org/GLM-4.6", label: "zai-org/GLM-4.6", isThinking: false },
  { id: "moonshotai/Kimi-K2-Instruct-0905", label: "moonshotai/Kimi-K2-Instruct-0905", isThinking: false },
  { id: "Pro/deepseek-ai/DeepSeek-V3.1-Terminus", label: "Pro/deepseek-ai/DeepSeek-V3.1-Terminus", isThinking: false },
  { id: "deepseek-ai/DeepSeek-V3.1-Terminus", label: "deepseek-ai/DeepSeek-V3.1-Terminus", isThinking: false },
  { id: "Qwen/Qwen3-Next-80B-A3B-Instruct", label: "Qwen/Qwen3-Next-80B-A3B-Instruct", isThinking: false },
  { id: "Qwen/Qwen3-Next-80B-A3B-Thinking", label: "Qwen/Qwen3-Next-80B-A3B-Thinking", isThinking: true },
  { id: "inclusionAI/Ring-flash-2.0", label: "inclusionAI/Ring-flash-2.0", isThinking: false },
  { id: "inclusionAI/Ling-flash-2.0", label: "inclusionAI/Ling-flash-2.0", isThinking: false },
  { id: "inclusionAI/Ling-mini-2.0", label: "inclusionAI/Ling-mini-2.0", isThinking: false },
  { id: "ByteDance-Seed/Seed-OSS-36B-Instruct", label: "ByteDance-Seed/Seed-OSS-36B-Instruct", isThinking: false },
  { id: "stepfun-ai/step3", label: "stepfun-ai/step3", isThinking: false },
  { id: "Qwen/Qwen3-Coder-30B-A3B-Instruct", label: "Qwen/Qwen3-Coder-30B-A3B-Instruct", isThinking: false },
  { id: "Qwen/Qwen3-Coder-480B-A35B-Instruct", label: "Qwen/Qwen3-Coder-480B-A35B-Instruct", isThinking: false },
  { id: "Qwen/Qwen3-30B-A3B-Thinking-2507", label: "Qwen/Qwen3-30B-A3B-Thinking-2507", isThinking: true },
  { id: "Qwen/Qwen3-30B-A3B-Instruct-2507", label: "Qwen/Qwen3-30B-A3B-Instruct-2507", isThinking: false },
  { id: "Qwen/Qwen3-235B-A22B-Thinking-2507", label: "Qwen/Qwen3-235B-A22B-Thinking-2507", isThinking: true },
  { id: "Qwen/Qwen3-235B-A22B-Instruct-2507", label: "Qwen/Qwen3-235B-A22B-Instruct-2507", isThinking: false },
  { id: "zai-org/GLM-4.5-Air", label: "zai-org/GLM-4.5-Air", isThinking: false },
  { id: "zai-org/GLM-4.5", label: "zai-org/GLM-4.5", isThinking: false },
  { id: "baidu/ERNIE-4.5-300B-A47B", label: "baidu/ERNIE-4.5-300B-A47B", isThinking: false },
  { id: "ascend-tribe/pangu-pro-moe", label: "ascend-tribe/pangu-pro-moe", isThinking: false },
  { id: "tencent/Hunyuan-A13B-Instruct", label: "tencent/Hunyuan-A13B-Instruct", isThinking: false },
  { id: "MiniMaxAI/MiniMax-M1-80k", label: "MiniMaxAI/MiniMax-M1-80k", isThinking: false },
  { id: "Tongyi-Zhiwen/QwenLong-L1-32B", label: "Tongyi-Zhiwen/QwenLong-L1-32B", isThinking: false },
  { id: "Qwen/Qwen3-30B-A3B", label: "Qwen/Qwen3-30B-A3B", isThinking: false },
  { id: "Qwen/Qwen3-32B", label: "Qwen/Qwen3-32B", isThinking: false },
  { id: "Qwen/Qwen3-14B", label: "Qwen/Qwen3-14B", isThinking: false },
  { id: "Qwen/Qwen3-8B", label: "Qwen/Qwen3-8B", isThinking: false },
  { id: "Qwen/Qwen3-235B-A22B", label: "Qwen/Qwen3-235B-A22B", isThinking: false },
  { id: "THUDM/GLM-Z1-32B-0414", label: "THUDM/GLM-Z1-32B-0414", isThinking: false },
  { id: "THUDM/GLM-4-32B-0414", label: "THUDM/GLM-4-32B-0414", isThinking: false },
  { id: "THUDM/GLM-Z1-Rumination-32B-0414", label: "THUDM/GLM-Z1-Rumination-32B-0414", isThinking: true },
  { id: "THUDM/GLM-4-9B-0414", label: "THUDM/GLM-4-9B-0414", isThinking: false },
  { id: "Qwen/QwQ-32B", label: "Qwen/QwQ-32B", isThinking: true },
  { id: "Pro/deepseek-ai/DeepSeek-R1", label: "Pro/deepseek-ai/DeepSeek-R1", isThinking: true },
  { id: "Pro/deepseek-ai/DeepSeek-V3", label: "Pro/deepseek-ai/DeepSeek-V3", isThinking: false },
  { id: "deepseek-ai/DeepSeek-R1", label: "deepseek-ai/DeepSeek-R1", isThinking: true },
  { id: "deepseek-ai/DeepSeek-V3", label: "deepseek-ai/DeepSeek-V3", isThinking: false },
  { id: "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B", label: "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B", isThinking: true },
  { id: "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B", label: "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B", isThinking: true },
  { id: "deepseek-ai/DeepSeek-R1-Distill-Qwen-14B", label: "deepseek-ai/DeepSeek-R1-Distill-Qwen-14B", isThinking: true },
  { id: "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", label: "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", isThinking: true },
  { id: "Pro/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", label: "Pro/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", isThinking: true },
  { id: "deepseek-ai/DeepSeek-V2.5", label: "deepseek-ai/DeepSeek-V2.5", isThinking: false },
  { id: "Qwen/Qwen2.5-72B-Instruct-128K", label: "Qwen/Qwen2.5-72B-Instruct-128K", isThinking: false },
  { id: "Qwen/Qwen2.5-72B-Instruct", label: "Qwen/Qwen2.5-72B-Instruct", isThinking: false },
  { id: "Qwen/Qwen2.5-32B-Instruct", label: "Qwen/Qwen2.5-32B-Instruct", isThinking: false },
  { id: "Qwen/Qwen2.5-14B-Instruct", label: "Qwen/Qwen2.5-14B-Instruct", isThinking: false },
  { id: "Qwen/Qwen2.5-7B-Instruct", label: "Qwen/Qwen2.5-7B-Instruct", isThinking: false },
  { id: "Qwen/Qwen2.5-Coder-32B-Instruct", label: "Qwen/Qwen2.5-Coder-32B-Instruct", isThinking: false },
  { id: "Qwen/Qwen2.5-Coder-7B-Instruct", label: "Qwen/Qwen2.5-Coder-7B-Instruct", isThinking: false },
  { id: "Qwen/Qwen2-7B-Instruct", label: "Qwen/Qwen2-7B-Instruct", isThinking: false },
  { id: "THUDM/glm-4-9b-chat", label: "THUDM/glm-4-9b-chat", isThinking: false },
  { id: "internlm/internlm2_5-7b-chat", label: "internlm/internlm2_5-7b-chat", isThinking: false },
  { id: "Pro/Qwen/Qwen2.5-7B-Instruct", label: "Pro/Qwen/Qwen2.5-7B-Instruct", isThinking: false },
  { id: "Pro/Qwen/Qwen2-7B-Instruct", label: "Pro/Qwen/Qwen2-7B-Instruct", isThinking: false },
  { id: "Pro/THUDM/glm-4-9b-chat", label: "Pro/THUDM/glm-4-9b-chat", isThinking: false }
];

const selectedModel = ref(llmModels[0].id);
const editableContent = ref("");
const savingContent = ref(false);

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

const sections = computed(() => store.sections || []);
const entities = computed(() => store.entities || []);
const relations = computed(() => store.relations || []);

watch(
  () => store.selectedText?.content,
  (value) => {
    editableContent.value = value || "";
  },
  { immediate: true }
);

const handleContentSave = async (force = false) => {
  if (!store.selectedTextId || !store.selectedText) {
    return;
  }
  if (!force && (savingContent.value || editableContent.value === store.selectedText.content)) {
    return;
  }
  savingContent.value = true;
  try {
    const payload = {
      title: store.selectedText.title || "未命名文献",
      content: editableContent.value,
      category: store.selectedText.category || "unknown",
      author: store.selectedText.author || "",
      era: store.selectedText.era || ""
    };
    const updated = await store.updateText(store.selectedTextId, payload);
    store.selectedText.content = updated.content;
    ElMessage.success("原文内容已保存");
  } catch (error) {
    ElMessage.error("原文保存失败，请稍后重试");
  } finally {
    savingContent.value = false;
  }
};

const goBackToDocuments = () => {
  router.push("/documents");
};

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
    await store.runFullAnalysis(selectedModel.value);
    ElMessage.success("模型分析完成，已更新标注与句读");
  } catch (error) {
    ElMessage.error("模型分析失败，请稍后重试");
  }
};

const handleClassify = async () => {
  try {
    await store.classifySelectedText(selectedModel.value);
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
  align-items: flex-start;
}

.sentence-panel {
  grid-column: 1 / -1;
}

.visualization-panel {
  grid-column: 1 / -1;
}

@media (max-width: 1200px) {
  .stage-grid {
    grid-template-columns: 1fr;
  }

  .sentence-panel {
    grid-column: auto;
  }
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
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

.text-editor :deep(.ql-container) {
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  min-height: 320px;
}

.text-editor :deep(.ql-editor) {
  min-height: 280px;
  line-height: 1.8;
  font-size: 15px;
  color: #4a443e;
}

.text-editor :deep(.ql-toolbar) {
  border-radius: 12px 12px 0 0;
}

.annotation-section {
  margin-bottom: 12px;
}

.form-inline :deep(.el-form-item) {
  margin-right: 12px;
}

.section-actions {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-end;
}

.segments {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.segment-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.04);
  display: grid;
  grid-template-columns: 1.1fr 1fr 1fr;
  gap: 12px;
}

.segment-col {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.segment-label {
  font-weight: 600;
  color: #8c7a6b;
  font-size: 13px;
}

.segment-text {
  background: #f9f7f2;
  border: 1px solid #f1ede4;
  border-radius: 10px;
  padding: 10px 12px;
  line-height: 1.6;
  color: #4a443e;
  min-height: 88px;
  white-space: pre-wrap;
}
</style>
