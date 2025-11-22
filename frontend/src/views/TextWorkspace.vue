<template>
  <div class="page-container workspace-container" v-loading="store.loading">
    <!-- Top Bar: Title and Actions -->
    <div class="workspace-header card">
      <div class="header-left">
        <el-button link @click="goBackToDocuments">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h1 class="doc-title">{{ store.selectedText?.title || '未命名文档' }}</h1>
        <el-tag v-if="store.selectedText?.category" effect="plain">
          {{ translateCategory(store.selectedText.category) }}
        </el-tag>
      </div>
      <div class="header-actions">
         <el-select
            v-model="selectedModel"
            placeholder="选择模型"
            class="model-select"
            filterable
            default-first-option
          >
            <el-option
              v-for="item in llmModels"
              :key="item.id"
              :label="item.isThinking ? `【思考】${item.label}` : item.label"
              :value="item.id"
            />
          </el-select>
          <el-button type="primary" :loading="store.analysisRunning" @click="handleFullAnalysis">
            <el-icon class="mr-2"><Cpu /></el-icon>
            模型全量分析
          </el-button>
          <el-button @click="router.push('/graph')">
            <el-icon class="mr-2"><Share /></el-icon>
            查看图谱
          </el-button>
          <el-button type="success" plain :loading="savingContent" @click="handleContentSave(true)">
            保存
          </el-button>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="workspace-grid">
      <!-- Left: Editor -->
      <div class="card editor-card">
        <div class="card-header">
          <h3>原文内容</h3>
          <el-tooltip content="原文修改后请记得保存" placement="top">
            <el-icon><InfoFilled /></el-icon>
          </el-tooltip>
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
        <div v-else class="empty-placeholder">
          正在加载文档内容...
        </div>
        </div>

      <!-- Right: Annotation Tools & Analysis Panel (Tabs) -->
      <div class="right-column">
        <div class="card tool-card">
           <el-tabs v-model="activeTab">
             <el-tab-pane label="标注工具" name="annotate">
                <!-- Entity Annotation -->
                <div class="tool-section">
                  <h4 class="tool-title">实体标注</h4>
                  <div class="input-group">
                    <el-input v-model="entityForm.label" placeholder="实体名称" />
                    <el-select v-model="entityForm.category" placeholder="类型" style="width: 100px">
                <el-option label="人物" value="PERSON" />
                <el-option label="地点" value="LOCATION" />
                <el-option label="事件" value="EVENT" />
                <el-option label="组织" value="ORGANIZATION" />
                <el-option label="器物" value="OBJECT" />
              </el-select>
                    <el-button type="primary" circle @click="submitEntity">
                      <el-icon><Plus /></el-icon>
                    </el-button>
        </div>
                  <div class="entity-list">
                    <el-tag 
                  v-for="entity in entities"
                  :key="entity.id"
                      class="entity-tag"
                      :type="getEntityTagType(entity.category)"
                      closable
                      @close="removeEntity(entity.id)"
                    >
                      {{ entity.label }}
                    </el-tag>
                    <div v-if="entities.length === 0" class="empty-text">暂无实体</div>
                  </div>
                </div>

                <el-divider />

                <!-- Relation Annotation -->
                <div class="tool-section">
                  <h4 class="tool-title">关系抽取</h4>
                  <div class="relation-form">
                    <el-select v-model="relationForm.sourceEntityId" placeholder="实体 A" filterable>
                        <el-option v-for="e in entities" :key="e.id" :label="e.label" :value="e.id" />
              </el-select>
                    <el-select v-model="relationForm.relationType" placeholder="关系" style="width: 100px">
                <el-option label="对抗" value="CONFLICT" />
                <el-option label="结盟" value="SUPPORT" />
                <el-option label="行旅" value="TRAVEL" />
                <el-option label="亲属" value="FAMILY" />
                <el-option label="时间" value="TEMPORAL" />
              </el-select>
                    <el-select v-model="relationForm.targetEntityId" placeholder="实体 B" filterable>
                        <el-option v-for="e in entities" :key="`t-${e.id}`" :label="e.label" :value="e.id" />
                    </el-select>
                    <el-button type="primary" @click="submitRelation">添加</el-button>
                  </div>
                  <el-table :data="relations" size="small" height="200" style="width: 100%">
                    <el-table-column prop="source.label" label="A" width="80" />
                    <el-table-column prop="relationType" label="关系" width="80">
                      <template #default="{ row }">
                        <el-tag size="small" effect="plain">{{ translateRelation(row.relationType) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="target.label" label="B" width="80" />
          </el-table>
        </div>
             </el-tab-pane>
             
             <el-tab-pane label="智能分析" name="analysis">
                <AnalysisPanel
                  v-if="store.selectedText"
                  :current-category="store.selectedText.category || ''"
                  :classification="store.classification"
                  :stats="store.insights?.stats"
                  :words="store.insights?.wordCloud"
                  :analysis-summary="store.insights?.analysisSummary"
                  @update-category="store.updateSelectedCategory"
                />
             </el-tab-pane>
           </el-tabs>
        </div>
        </div>
        </div>

    <!-- Bottom: Segmentation (Full Width) -->
    <div class="card segment-section">
      <div class="card-header">
        <h3>句读与摘要</h3>
        <el-button size="small" @click="handleAutoSegment">
          <el-icon class="mr-1"><MagicStick /></el-icon> 智能句读
        </el-button>
        </div>
      <div class="segments-list" v-if="sections.length">
         <div v-for="(section, index) in sections" :key="section.id" class="segment-row">
           <div class="segment-index">{{ index + 1 }}</div>
           <div class="segment-content">
             <div class="segment-original">{{ section.originalText }}</div>
             <div class="segment-edit">
              <el-input
                  v-model="section.punctuatedText" 
                type="textarea"
                  :rows="2" 
                  placeholder="句读结果" 
                @blur="handleUpdateSection(section)"
              />
            </div>
             <div class="segment-summary">
              <el-input
                v-model="section.summary"
                  placeholder="本段摘要" 
                @blur="handleUpdateSection(section)"
              />
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-placeholder">
        暂无分段数据，请点击“智能句读”生成
    </div>
    </div>

  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import { QuillEditor } from "@vueup/vue-quill";
import "@vueup/vue-quill/dist/vue-quill.snow.css";
import { ArrowLeft, Cpu, Share, InfoFilled, Plus, MagicStick } from "@element-plus/icons-vue";
import AnalysisPanel from "@/components/visualizations/AnalysisPanel.vue";

const router = useRouter();
const route = useRoute();
const store = useTextStore();
const activeTab = ref('annotate');

// Restored Full LLM Models List
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

onMounted(async () => {
  if (route.params.id) {
    if (store.selectedTextId !== route.params.id) {
       await store.selectText(route.params.id);
    }
  }
});

watch(
  () => store.selectedText?.content,
  (value) => {
    editableContent.value = value || "";
  },
  { immediate: true }
);

const goBackToDocuments = () => router.push("/documents");

const handleContentSave = async (force = false) => {
  if (!store.selectedTextId || !store.selectedText) return;
  if (!force && (savingContent.value || editableContent.value === store.selectedText.content)) return;
  
  savingContent.value = true;
  try {
    const payload = { ...store.selectedText, content: editableContent.value };
    await store.updateText(store.selectedTextId, payload);
    store.selectedText.content = editableContent.value;
    if (force) ElMessage.success("已保存");
  } catch (error) {
    ElMessage.error("保存失败");
  } finally {
    savingContent.value = false;
  }
};

const submitEntity = async () => {
  if (!entityForm.label) return;
  await store.createEntityAnnotation({
    textId: store.selectedTextId,
    label: entityForm.label,
    category: entityForm.category,
    confidence: 1.0
  });
  entityForm.label = "";
};

const removeEntity = (id) => {
  // Assuming store has delete method
  // store.deleteEntity(id); 
  console.log("Delete entity", id);
};

const submitRelation = async () => {
  if (!relationForm.sourceEntityId || !relationForm.targetEntityId) return;
  await store.createRelationAnnotation({
    textId: store.selectedTextId,
    sourceEntityId: relationForm.sourceEntityId,
    targetEntityId: relationForm.targetEntityId,
    relationType: relationForm.relationType,
    confidence: 1.0
  });
  ElMessage.success("关系已添加");
};

const handleAutoSegment = async () => {
  await store.autoSegmentSections();
  ElMessage.success("智能句读完成");
};

const handleUpdateSection = async (section) => {
  await store.updateSection(section.id, {
    punctuatedText: section.punctuatedText,
    summary: section.summary
  });
};

const handleFullAnalysis = async () => {
  try {
    await store.runFullAnalysis(selectedModel.value);
    ElMessage.success("全量分析完成");
    activeTab.value = 'analysis'; // Switch to analysis tab on success
  } catch (error) {
    ElMessage.error("分析失败");
  }
};

const translateCategory = (c) => {
  const map = { warfare: "战争", travelogue: "游记", biography: "传记" };
  return map[c] || c;
};

const translateRelation = (t) => {
  const map = { CONFLICT: "对抗", SUPPORT: "结盟", TRAVEL: "行旅", FAMILY: "亲属", TEMPORAL: "时间" };
  return map[t] || t;
};

const getEntityTagType = (c) => {
  const map = { PERSON: "", LOCATION: "success", EVENT: "warning", ORGANIZATION: "info", OBJECT: "danger" };
  return map[c] || "info";
};
</script>

<style scoped>
.workspace-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 40px;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.doc-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.model-select {
  width: 240px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
}

.editor-card {
  min-height: 600px;
  display: flex;
  flex-direction: column;
}

.text-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* Quill Override */
.text-editor :deep(.ql-container) {
  border: none;
  font-size: 16px;
  line-height: 1.8;
}
.text-editor :deep(.ql-toolbar) {
  border: none;
  border-bottom: 1px solid #eee;
}

.right-column {
  display: flex;
  flex-direction: column;
}

.tool-card {
  height: 100%;
  min-height: 600px;
  display: flex;
  flex-direction: column;
}

.tool-section {
  margin-bottom: 20px;
}

.tool-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.input-group {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.entity-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.relation-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.segment-section {
  min-height: 200px;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.segment-row {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 12px;
}

.segment-index {
  font-weight: bold;
  color: #ccc;
  font-size: 18px;
  width: 24px;
}

.segment-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.segment-original {
  font-size: 15px;
  color: #444;
  line-height: 1.6;
}

.segment-edit, .segment-summary {
  margin-top: 4px;
}

.empty-placeholder {
  color: #999;
  text-align: center;
  padding: 40px;
}
</style>
