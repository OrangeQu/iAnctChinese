<template>
  <div class="workspace">
    <div class="stage-grid">
      <aside class="panel text-panel">
        <div class="panel-head">
          <h3 class="section-title">原文</h3>
        </div>
        <div class="editor-toolbar" v-if="editor">
          <div class="toolbar-left">
            <el-button-group>
              <el-button size="small" @click="setAlign('left')">居左</el-button>
              <el-button size="small" @click="setAlign('center')">居中</el-button>
              <el-button size="small" @click="setAlign('right')">居右</el-button>
            </el-button-group>
            <el-select v-model="currentFontSize" size="small" class="font-select" @change="applyFontSize"
              placeholder="字号">
              <el-option v-for="size in fontSizeOptions" :key="size" :label="size + 'px'" :value="size" />
            </el-select>
            <el-button size="small" @click="handleUndo">撤销</el-button>
            <el-button size="small" @click="handleRedo">恢复</el-button>
          </div>
          <div class="toolbar-right">
            <el-tooltip content="手动在当前位置添加阅读标记" placement="bottom">
              <el-button size="small" type="primary" plain @click="saveBookmark">添加阅读标记</el-button>
            </el-tooltip>
            <el-button size="small" :disabled="bookmarkOffset === null" @click="jumpToBookmark">
              跳转标记
            </el-button>
            <el-button size="small" :disabled="bookmarkOffset === null" @click="clearBookmark">
              清除标记
            </el-button>
          </div>
        </div>
        <div v-if="store.selectedText" class="editor-wrapper">
          <EditorContent :editor="editor" class="text-editor" />
        </div>
        <p v-else class="placeholder">请先上传文言文或从左侧列表选择一篇文稿</p>
        <el-divider />
        <div class="action-row">
          <el-select v-model="selectedModel" size="small" style="width: 260px" placeholder="选择大模型" filterable>
            <el-option v-for="item in llmModels" :key="item.id"
              :label="item.isThinking ? `【深度思考】${item.label}` : item.label" :value="item.id" />
          </el-select>
          <el-button type="primary" :loading="store.analysisRunning" @click="handleFullAnalysis">
            触发模型分析
          </el-button>
          <el-button type="success" plain :loading="savingContent" @click="handleContentSave(true)">
            保存原文
          </el-button>
          <el-button type="warning" plain :loading="store.classifyRunning" @click="handleClassify">
            大模型判断类型
          </el-button>
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
            <el-form-item label="类别">
              <el-select v-model="entityForm.category" placeholder="实体类别" style="width: 80px">
                <el-option label="人物" value="PERSON" />
                <el-option label="地点" value="LOCATION" />
                <el-option label="事件" value="EVENT" />
                <el-option label="组织" value="ORGANIZATION" />
                <el-option label="器物" value="OBJECT" />
              </el-select>
            </el-form-item>
            <el-button type="primary" @click="submitEntity" class="align-button">添加实体</el-button>
          </el-form>
          <div class="entity-actions">
            <el-button type="primary" plain size="small" @click="entityDrawerVisible = true">
              抽屉查看（按类别分组）
            </el-button>
            <span class="entity-hint">在原文中框选片段再点“添加实体”，即可直接高亮标注</span>
          </div>
          <el-table :data="entities" border size="small" height="220">
            <el-table-column prop="label" label="实体" width="140" />
            <el-table-column prop="category" label="类别" width="120" />
            <el-table-column prop="confidence" label="置信度" />
          </el-table>
          <el-drawer v-model="entityDrawerVisible" title="实体列表（按类别）" direction="rtl" size="30%">
            <div class="entity-drawer">
              <div v-for="(list, category) in groupedEntities" :key="category" class="entity-group">
                <div class="entity-group-title">
                  {{ translateCategoryLabel(category) }}（{{ list.length }}）
                </div>
                <div class="entity-tags">
                  <el-tag v-for="item in list" :key="item.id" size="small"
                    :type="item.category === 'PERSON' ? 'warning' : item.category === 'LOCATION' ? 'info' : 'success'"
                    effect="plain">
                    {{ item.label }}
                    <span v-if="item.confidence"> · {{ (item.confidence * 100).toFixed(0) }}%</span>
                  </el-tag>
                </div>
              </div>
              <p v-if="!entities.length" class="placeholder">暂无实体，请先添加或触发模型分析</p>
            </div>
          </el-drawer>
        </div>
        <el-divider />
        <div class="annotation-section">
          <h3 class="section-title">关系标注</h3>
          <el-form :model="relationForm" inline class="form-inline">
            <el-form-item label="实体A">
              <el-select v-model="relationForm.sourceEntityId" placeholder="选择实体" style="width: 100px">
                <el-option v-for="entity in entities" :key="entity.id" :label="entity.label" :value="entity.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="实体B">
              <el-select v-model="relationForm.targetEntityId" placeholder="选择实体" style="width: 100px">
                <el-option v-for="entity in entities" :key="`target-${entity.id}`" :label="entity.label"
                  :value="entity.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="关系">
              <el-select v-model="relationForm.relationType" placeholder="关系类型" style="width: 120px">
                <el-option v-for="opt in relationOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitRelation">添加关系</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="activeRelations" border size="small" height="200">
            <el-table-column prop="source.label" label="实体A" />
            <el-table-column label="关系类型">
              <template #default="{ row }">
                {{ translateRelationLabel(row.relationType || row.type) }}
              </template>
            </el-table-column>
            <el-table-column prop="target.label" label="实体B" />
            <el-table-column prop="confidence" label="置信度" />
          </el-table>
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
              <el-input type="textarea" v-model="section.punctuatedText" :autosize="{ minRows: 3, maxRows: 6 }"
                placeholder="添加句读" @blur="handleUpdateSection(section)" />
            </div>
            <div class="segment-col">
              <div class="segment-label">摘要</div>
              <el-input type="textarea" v-model="section.summary" :autosize="{ minRows: 3, maxRows: 6 }"
                placeholder="一句话摘要" @blur="handleUpdateSection(section)" />
            </div>
          </div>
        </div>
        <p v-else class="placeholder">暂无句读分段，请先自动推荐或手动新增</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, nextTick, onMounted, onActivated, onBeforeUnmount } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import { useAuthStore } from "@/store/authStore";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import { TextStyle } from "@tiptap/extension-text-style";
import TextAlign from "@tiptap/extension-text-align";
import { Mark } from "@tiptap/core";
import { TextSelection, Plugin, PluginKey } from "prosemirror-state";
import { Decoration, DecorationSet } from "prosemirror-view";

const EntityMark = Mark.create({
  name: "entity",
  inline: true,
  group: "inline",
  inclusive: false,
  addAttributes() {
    return {
      id: { default: null },
      category: { default: null },
      label: { default: null }
    };
  },
  parseHTML() {
    return [{ tag: "span[data-entity-id]" }];
  },
  renderHTML({ HTMLAttributes }) {
    return [
      "span",
      {
        ...HTMLAttributes,
        "data-entity-id": HTMLAttributes.id,
        "data-entity-category": HTMLAttributes.category,
        "data-entity-label": HTMLAttributes.label,
        class: "ner-entity",
        title: `${HTMLAttributes.label || ""}${HTMLAttributes.category ? `（${HTMLAttributes.category}）` : ""}`
      },
      0
    ];
  }
});

const router = useRouter();
const route = useRoute();
const store = useTextStore();
const authStore = useAuthStore();

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
const activeEntityId = ref(null);
const entityDrawerVisible = ref(false);
const allowHighlights = ref(false);
const suppressSelectionUpdate = ref(false);

const entityForm = reactive({
  label: "",
  category: "PERSON",
  startOffset: 0,
  endOffset: 0
});

const relationForm = reactive({
  sourceEntityId: null,
  targetEntityId: null,
  relationType: "ALLY"
});

const appliedContentSignature = ref("");

const sections = computed(() => store.sections || []);
const entities = computed(() => store.entities || []);
const relations = computed(() => store.relations || []);
const activeRelations = computed(() => {
  if (!activeEntityId.value) return relations.value || [];
  const targetId = String(activeEntityId.value);
  return (relations.value || []).filter(
    (r) =>
      String(r.sourceEntityId || r.source?.id) === targetId ||
      String(r.targetEntityId || r.target?.id) === targetId
  );
});

const activeTextCategory = computed(() => {
  return store.classification?.suggestedCategory || store.selectedText?.category || "other";
});

const relationOptions = computed(() => {
  const cat = (activeTextCategory.value || "").toLowerCase();
  const all = [
    { label: "亲属", value: "FAMILY" },
    { label: "结盟/支持", value: "ALLY" },
    { label: "援助/支援", value: "SUPPORT" },
    { label: "对抗/敌对", value: "RIVAL" },
    { label: "冲突/对立", value: "CONFLICT" },
    { label: "师承/同门", value: "MENTOR" },
    { label: "影响/启发", value: "INFLUENCE" },
    { label: "所在", value: "LOCATION_OF" },
    { label: "隶属", value: "PART_OF" },
    { label: "因果", value: "CAUSE" },
    { label: "时间/时序", value: "TEMPORAL" },
    { label: "行旅/路线", value: "TRAVEL" },
    { label: "其他", value: "CUSTOM" }
  ];
  if (cat.includes("warfare")) {
    return all.filter((i) =>
      [
        "ALLY",
        "SUPPORT",
        "RIVAL",
        "CONFLICT",
        "PART_OF",
        "LOCATION_OF",
        "CAUSE",
        "INFLUENCE",
        "FAMILY",
        "TEMPORAL",
        "TRAVEL",
        "CUSTOM"
      ].includes(i.value)
    );
  }
  if (cat.includes("travel")) {
    return all.filter((i) =>
      [
        "LOCATION_OF",
        "PART_OF",
        "TRAVEL",
        "TEMPORAL",
        "INFLUENCE",
        "ALLY",
        "SUPPORT",
        "RIVAL",
        "CAUSE",
        "CUSTOM"
      ].includes(i.value)
    );
  }
  if (cat.includes("biography")) {
    return all.filter((i) =>
      [
        "FAMILY",
        "MENTOR",
        "ALLY",
        "SUPPORT",
        "RIVAL",
        "CONFLICT",
        "PART_OF",
        "LOCATION_OF",
        "INFLUENCE",
        "CAUSE",
        "TEMPORAL",
        "CUSTOM"
      ].includes(i.value)
    );
  }
  return all;
});

const normalizeContent = (text) => (text || "").replace(/\r\n/g, "\n");
const condenseBlankLines = (text) => (text || "").replace(/\n{3,}/g, "\n\n").replace(/^\n+|\n+$/g, "");

const crlfToLfOffset = (offset, rawText) => {
  let crlfCount = 0;
  const len = Math.min(offset, rawText.length);
  for (let i = 0; i < len; i++) {
    if (rawText[i] === "\r" && rawText[i + 1] === "\n") {
      crlfCount++;
      i++; // skip \n
    }
  }
  return offset - crlfCount;
};

const lfToCrlfOffset = (offset, text) => {
  const lfText = normalizeContent(text || "");
  const clamped = Math.max(0, Math.min(offset, lfText.length));
  const newlineCount = (lfText.slice(0, clamped).match(/\n/g) || []).length;
  return clamped + newlineCount;
};

const buildDocFromPlain = (text) => {
  const paragraphs = (text || "").split(/\n/);
  return {
    type: "doc",
    content: paragraphs.map((line) => ({
      type: "paragraph",
      content: line ? [{ type: "text", text: line }] : []
    }))
  };
};

const editor = useEditor({
  extensions: [
    StarterKit.configure({ history: true }),
    TextStyle,
    TextAlign.configure({
      types: ["heading", "paragraph"]
    }),
    EntityMark,
  ],
  content: "",
  editable: true,
  onUpdate: ({ editor }) => {
    editableContent.value = editor.getText();
  },
  onSelectionUpdate: () => {
    if (suppressSelectionUpdate.value) return;
    updateSelectionMeta();
  },
  onBlur: () => {
    handleContentSave();
  }
});

const getDocText = () => {
  const ed = editor.value;
  if (!ed) return "";
  const docSize = ed.state.doc.content.size;
  return ed.state.doc.textBetween(0, docSize, "\n", "\n");
};

const getOffsetFromPos = (pos) => {
  const ed = editor.value;
  if (!ed) return 0;
  return ed.state.doc.textBetween(0, pos, "\n", "\n").length;
};

// 将按 \n 计数的线性 offset 映射回 ProseMirror 的文档位置
const getPosFromOffset = (offset) => {
  const ed = editor.value;
  if (!ed) return 1;
  const doc = ed.state.doc;
  const size = doc.content.size;
  const target = Math.max(0, offset);
  // 二分搜索找到最小的 pos，使得 textBetween(0, pos) 的长度 >= target
  let lo = 1;
  let hi = size - 1;
  let ans = hi;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const len = doc.textBetween(0, mid, "\n", "\n").length;
    if (len >= target) {
      ans = mid;
      hi = mid - 1;
    } else {
      lo = mid + 1;
    }
  }
  return Math.min(Math.max(ans, 1), size - 1);
};

const updateSelectionMeta = () => {
  const ed = editor.value;
  if (!ed) return;
  const { from, to } = ed.state.selection;
  if (from === to) return;
  const selectedTextRaw = ed.state.doc.textBetween(from, to, "\n", "\n");
  const selectedText = selectedTextRaw.trim();
  // 避免一次性选中过长片段导致名称被填充全文
  if (!selectedText || selectedText.length > 2000) return;
  const plainText = getDocText();
  const startLf = getOffsetFromPos(from);
  const endLf = getOffsetFromPos(to);
  entityForm.startOffset = lfToCrlfOffset(startLf, plainText);
  entityForm.endOffset = lfToCrlfOffset(endLf, plainText);
  entityForm.label = selectedText;
};

const bookmarkOffset = ref(null);
const fontSizeOptions = [14, 16, 18, 20, 22];
const currentFontSize = ref(16);
const bookmarkPluginKey = new PluginKey("bookmark-widget");

const bookmarkKey = computed(() => {
  if (!store.selectedTextId) return null;
  const user = authStore.user?.username || "guest";
  return `bookmark:${user}:${store.selectedTextId}`;
});

const loadBookmark = () => {
  const key = bookmarkKey.value;
  if (!key) {
    bookmarkOffset.value = null;
    return;
  }
  const raw = localStorage.getItem(key);
  const num = Number(raw);
  bookmarkOffset.value = Number.isFinite(num) ? num : null;
  nextTick(applyBookmarkDecoration);
};

const saveBookmark = () => {
  const ed = editor.value;
  const key = bookmarkKey.value;
  if (!ed || !key) return;
  const { from } = ed.state.selection;
  const offset = getOffsetFromPos(from);
  bookmarkOffset.value = offset;
  localStorage.setItem(key, String(offset));
  ElMessage.success("阅读标记已保存");
  applyBookmarkDecoration();
};

const jumpToBookmark = () => {
  if (bookmarkOffset.value == null || !editor.value) return;
  const pos = getPosFromOffset(bookmarkOffset.value);
  editor.value
    .chain()
    .focus()
    .setTextSelection({ from: pos, to: pos })
    .scrollIntoView()
    .run();
};

const clearBookmark = () => {
  const key = bookmarkKey.value;
  if (key) {
    localStorage.removeItem(key);
  }
  bookmarkOffset.value = null;
  applyBookmarkDecoration();
};

const setAlign = (align) => {
  if (!editor.value) return;
  editor.value.chain().focus().setTextAlign(align).run();
};

const applyFontSize = (size) => {
  if (!editor.value) return;
  currentFontSize.value = size;
  editor.value.chain().focus().setMark("textStyle", { fontSize: `${size}px` }).run();
};

const handleUndo = () => {
  editor.value?.chain().focus().undo().run();
};

const handleRedo = () => {
  editor.value?.chain().focus().redo().run();
};

const applyBookmarkDecoration = () => {
  const ed = editor.value;
  if (!ed) return;
  const doc = ed.state.doc;
  let decorations = [];
  if (bookmarkOffset.value != null) {
    const pos = getPosFromOffset(bookmarkOffset.value);
    decorations.push(
      Decoration.widget(pos, () => {
        const span = document.createElement("span");
        span.className = "bookmark-flag";
        span.title = "阅读标记";
        span.textContent = "🚩";
        return span;
      })
    );
  }
  const decoSet = DecorationSet.create(doc, decorations);
  const tr = ed.state.tr.setMeta(bookmarkPluginKey, decoSet);
  ed.view.dispatch(tr);
};

watch(
  () => route.params.id,
  (id) => {
    const numId = Number(id);
    if (numId && numId !== store.selectedTextId) {
      store.selectText(numId);
    }
  },
  { immediate: true }
);

onMounted(() => {
  const numId = Number(route.params.id);
  if (numId) {
    store.selectText(numId);
  }
  loadBookmark();
});

onActivated(() => {
  const numId = Number(route.params.id);
  if (numId) {
    store.selectText(numId);
  }
});

const groupedEntities = computed(() => {
  const map = {};
  (entities.value || []).forEach((e) => {
    const key = e.category || "OTHER";
    if (!map[key]) map[key] = [];
    map[key].push(e);
  });
  return map;
});

const syncContentToEditor = () => {
  if (!editor.value) return;
  if (store.selectedText?.content === undefined || store.selectedText?.content === null) return;
  const rawValue = store.selectedText?.content || "";
  const signature = `${store.selectedTextId || ""}::${rawValue}`;
  if (appliedContentSignature.value === signature) return;

  const normalized = normalizeContent(rawValue);
  const cleaned = condenseBlankLines(normalized);
  editor.value.commands.setContent(buildDocFromPlain(cleaned), false);
  appliedContentSignature.value = signature;
  editableContent.value = editor.value.getText();
  nextTick(applyEntityHighlight);
};

function applyEntityHighlight() {
  const ed = editor?.value;
  if (!ed || !store.selectedText?.content) return;
  if (!entities.value || entities.value.length === 0) return;
  // 确保高亮开关在有实体时自动开启
  if (!allowHighlights.value) {
    allowHighlights.value = true;
  }
  const state = ed.state;
  const doc = state.doc;
  const docSize = doc.content.size;
  const textLen = doc.textBetween(0, docSize, "\n", "\n").length;
  if (!textLen) return;

  // 映射字符偏移到 ProseMirror 位置，块间加 \n
  const offsetToPos = (offset) => {
    const target = Math.max(0, Math.min(offset, textLen));
    return getPosFromOffset(target);
  };

  const rawText = store.selectedText?.content || "";
  const lfText = normalizeContent(rawText);
  const lfLen = lfText.length;

  const findRangeBySearch = (entity) => {
    const label = (entity.label || "").trim();
    if (!label) return null;
    const target =
      entity.startOffset != null ? Math.max(0, crlfToLfOffset(entity.startOffset, rawText)) : null;
    let best = null;
    let idx = 0;
    while (idx >= 0) {
      idx = lfText.indexOf(label, idx);
      if (idx === -1) break;
      const end = idx + label.length;
      if (target == null) return [idx, end]; // 无偏移信息时取首个匹配
      const dist = Math.abs(idx - target);
      if (!best || dist < best.dist) best = { dist, range: [idx, end] };
      idx = end;
    }
    return best ? best.range : null;
  };

  const prevSel = state.selection;

  suppressSelectionUpdate.value = true;
  try {
    ed.chain().setTextSelection({ from: 1, to: docSize }).unsetMark("entity").run();

    let tr = state.tr;
    const markType = state.schema.marks.entity;

    entities.value.forEach((entity) => {
      const range = findRangeBySearch(entity);
      if (!range) return;
      const [startOffset, endOffset] = range;
      if (startOffset >= lfLen) return;
      const from = offsetToPos(startOffset);
      const to = offsetToPos(Math.min(endOffset, lfLen));
      if (to <= from) return;
      tr = tr.addMark(
        from,
        to,
        markType.create({ id: entity.id, category: entity.category, label: entity.label })
      );
    });

    const safeFrom = Math.max(1, Math.min(prevSel.from, tr.doc.content.size - 1));
    const safeTo = Math.max(1, Math.min(prevSel.to, tr.doc.content.size - 1));
    tr = tr.setSelection(
      TextSelection.between(tr.doc.resolve(safeFrom), tr.doc.resolve(Math.max(safeFrom, safeTo)))
    );
    ed.view.dispatch(tr);
  } finally {
    suppressSelectionUpdate.value = false;
  }
}

watch(
  () =>
    entities.value.map(
      (entity) => `${entity.id}-${entity.startOffset}-${entity.endOffset}-${entity.category}-${entity.label}`
    ),
  () => nextTick(applyEntityHighlight),
  { deep: true }
);

watch(
  () =>
    relations.value.map(
      (relation) =>
        `${relation.id || relation.relationType}-${relation.sourceEntityId || relation.source?.id}-${relation.targetEntityId || relation.target?.id}`
    ),
  () => nextTick(applyEntityHighlight),
  { deep: true }
);

watch(editableContent, () => nextTick(applyEntityHighlight));

watch(
  () => store.selectedText?.content,
  (value) => {
    if (!editor.value) return;
    if (value === undefined || value === null) return;
    syncContentToEditor();
  },
  { immediate: true }
);

watch(
  () => [editor.value, store.selectedTextId],
  () => {
    syncContentToEditor();
    nextTick(applyEntityHighlight);
    loadBookmark();
    applyBookmarkDecoration();
  }
);

watch(
  () => store.selectedTextId,
  () => {
    allowHighlights.value = false;
    entityForm.label = "";
    entityForm.startOffset = 0;
    entityForm.endOffset = 0;
    loadBookmark();
    applyBookmarkDecoration();
  }
);

watch(bookmarkOffset, () => applyBookmarkDecoration());

// 模型分析完成后强制重刷高亮，防止中途清空 mark 后未恢复
watch(
  () => store.analysisRunning,
  (running) => {
    if (!running && (entities.value || []).length > 0) {
      allowHighlights.value = true;
      nextTick(applyEntityHighlight);
    }
  }
);

watch(
  () => entities.value.length,
  (len) => {
    if (len > 0) {
      allowHighlights.value = true;
      nextTick(applyEntityHighlight);
    } else {
      allowHighlights.value = false;
    }
  },
  { immediate: true }
);

watch(
  relationOptions,
  (opts) => {
    if (!opts || !opts.length) return;
    const values = opts.map((o) => o.value);
    if (!values.includes(relationForm.relationType)) {
      relationForm.relationType = values[0];
    }
  },
  { immediate: true }
);

const handleContentSave = async (force = false) => {
  if (!store.selectedTextId || !store.selectedText) return;
  if (!editor.value) return;
  let currentText = editor.value.getText();
  if (currentText.endsWith("\n")) {
    currentText = currentText.slice(0, -1);
  }
  currentText = condenseBlankLines(currentText);
  const crlfContent = currentText.replace(/\n/g, "\r\n");
  if (!force && (savingContent.value || crlfContent === store.selectedText.content)) return;
  savingContent.value = true;
  try {
    const payload = {
      title: store.selectedText.title || "未命名文稿",
      content: crlfContent,
      category: store.selectedText.category || "unknown",
      author: store.selectedText.author || "",
      era: store.selectedText.era || ""
    };
    const updated = await store.updateText(store.selectedTextId, payload);
    store.selectedText.content = updated.content;
    editableContent.value = normalizeContent(updated.content);
    ElMessage.success("原文内容已保存");
  } catch (error) {
    // 输出详细错误方便定位
    console.error("保存原文失败", error);
    ElMessage.error("原文保存失败，请稍后重试");
  } finally {
    savingContent.value = false;
  }
};

const goBackToDocuments = () => {
  const projectId = route.query.projectId;
  if (projectId) {
    router.push(`/projects/${projectId}/documents`);
  } else {
    router.push("/documents");
  }
};

const submitEntity = async () => {
  if (!entityForm.label) {
    ElMessage.warning("请填写实体名称或先框选原文片段");
    return;
  }
  if (entityForm.endOffset <= entityForm.startOffset) {
    ElMessage.warning("请在原文中框选需要标注的文本");
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
  allowHighlights.value = true;
  nextTick(applyEntityHighlight);
  entityForm.label = "";
};

const submitRelation = async () => {
  if (!relationForm.sourceEntityId || !relationForm.targetEntityId) {
    ElMessage.warning("请选择实体");
    return;
  }
  try {
    await store.createRelationAnnotation({
      textId: store.selectedTextId,
      sourceEntityId: relationForm.sourceEntityId,
      targetEntityId: relationForm.targetEntityId,
      relationType: relationForm.relationType,
      confidence: 0.8
    });
    ElMessage.success("关系已添加");
  } catch (error) {
    console.error("createRelation error", error);
    ElMessage.error("关系添加失败，请检查登录状态或稍后再试");
  }
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
    entityForm.label = "";
    await handleContentSave(true);
    await store.runFullAnalysis(selectedModel.value);
    allowHighlights.value = true;
    nextTick(applyEntityHighlight);
    ElMessage.success("模型分析完成，已更新标注与句读");
  } catch (error) {
    console.error("模型分析失败", error);
    ElMessage.error("模型分析失败，请稍后重试");
  }
};

const handleClassify = async () => {
  try {
    entityForm.label = "";
    await handleContentSave(true);
    await store.classifySelectedText(selectedModel.value);
    ElMessage.success("模型已完成类型判断");
  } catch (error) {
    console.error("类型判断失败", error);
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

const translateCategoryLabel = (cat) => {
  const map = {
    PERSON: "人物",
    LOCATION: "地点",
    EVENT: "事件",
    ORGANIZATION: "组织",
    OBJECT: "器物",
    CUSTOM: "其他",
    OTHER: "其他"
  };
  return map[cat] || cat || "未分类";
};

const translateRelationLabel = (rel) => {
  if (!rel) return "其他";
  const key = String(rel).toUpperCase();
  const map = {
    FAMILY: "亲属",
    ALLY: "结盟/支持",
    SUPPORT: "结盟/支持",
    RIVAL: "对抗/敌对",
    CONFLICT: "对抗/敌对",
    MENTOR: "师承/同门",
    INFLUENCE: "影响/启发",
    LOCATION_OF: "所在",
    PART_OF: "隶属",
    CAUSE: "因果",
    TRAVEL: "行旅",
    TEMPORAL: "时间/时序",
    CUSTOM: "其他"
  };
  return map[key] || rel || "未知";
};

onBeforeUnmount(() => {
  editor.value?.destroy();
});

onMounted(() => {
  if ((entities.value || []).length > 0) {
    allowHighlights.value = true;
    nextTick(applyEntityHighlight);
  }
});
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
  align-items: stretch;
}

.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.stage-top-actions {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 6px;
}

.sentence-panel {
  grid-column: 1 / -1;
  margin-top: 50px;
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
  align-items: center;
  margin-bottom: 12px;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.font-select {
  width: 110px;
}

.editor-wrapper {
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  overflow: hidden;
  height: 520px;
  display: flex;
  flex-direction: column;
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

.text-editor {
  flex: 1;
  padding: 12px;
  background: #fff;
  overflow-y: auto;
}

.text-editor :deep(.ProseMirror) {
  min-height: 100%;
  outline: none;
  line-height: 1.8;
  font-size: 15px;
  color: #4a443e;
  white-space: pre-wrap;
}

.text-editor :deep(p) {
  margin: 0 0 8px;
}

.text-editor :deep(p:last-child) {
  margin-bottom: 0;
}

.annotation-section {
  margin-bottom: 12px;
}

.form-inline :deep(.el-form-item) {
  margin-right: 12px;
}

.align-button {
  margin-top: 2px;
}

.entity-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0 4px;
}

.entity-hint {
  font-size: 12px;
  color: #8c7a6b;
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

.text-editor :deep(.ner-entity) {
  border-radius: 4px;
  padding: 1px 2px;
  box-shadow: inset 0 0 0 1px rgba(17, 24, 39, 0.05);
}

.text-editor :deep(.bookmark-flag) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  margin-left: 4px;
  cursor: pointer;
  user-select: none;
}

.text-editor :deep(.ner-entity[data-entity-category="PERSON"]) {
  background: rgba(250, 204, 21, 0.4);
  color: #4a443e;
}

.text-editor :deep(.ner-entity[data-entity-category="LOCATION"]) {
  background: rgba(147, 197, 253, 0.4);
  color: #0f172a;
}

.text-editor :deep(.ner-entity[data-entity-category="EVENT"]) {
  background: rgba(134, 239, 172, 0.4);
  color: #14532d;
}

.text-editor :deep(.ner-entity[data-entity-category="ORGANIZATION"]) {
  background: rgba(249, 168, 212, 0.4);
  color: #4a044e;
}

.text-editor :deep(.ner-entity[data-entity-category="OBJECT"]) {
  background: rgba(165, 180, 252, 0.4);
  color: #111827;
}

.text-editor :deep(.ner-entity[data-entity-category="CUSTOM"]),
.text-editor :deep(.ner-entity[data-entity-category="OTHER"]),
.text-editor :deep(.ner-entity:not([data-entity-category])) {
  background: rgba(229, 231, 235, 0.6);
  color: #111827;
}

.entity-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.entity-group-title {
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}

.entity-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
