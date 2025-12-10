<template>
  <div class="documents-page" v-loading="store.loading">
    <div class="header-bar">
      <el-button type="default" plain @click="goProjectList"> 返回项目列表</el-button>
    </div>
    <header class="page-header">
      <div>
        <h1>文档管理</h1>
        <p class="subtitle">选择要分析的古籍文本，或上传新的材料</p>
      </div>
      <div class="actions">
        <el-input
          v-model="searchInput"
          placeholder="按标题或作者搜索"
          class="search-input"
          clearable
          @keyup.enter="applySearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="applySearch">搜索</el-button>
      </div>
    </header>

    <section class="stats">
      <div class="stat-card">
        <span class="label">总文档</span>
        <strong class="value">{{ store.texts.length }}</strong>
      </div>
      <div class="stat-card">
        <span class="label">筛选结果</span>
        <strong class="value">{{ filteredDocuments.length }}</strong>
      </div>
    </section>

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>文档列表</span>
          <div class="card-actions">
            <el-button type="primary" @click="handleCreate">新建文档</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredDocuments" stripe border>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column
          prop="category"
          label="类型"
          width="140"
          :formatter="formatCategory"
        />
        <el-table-column prop="author" label="作者" width="160" />
        <el-table-column prop="era" label="时代" width="160" />
        <el-table-column
          prop="updatedAt"
          label="最近更新"
          width="200"
          :formatter="formatDate"
        />
        <el-table-column label="操作" width="360">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openDocument(row)">进入文档</el-button>
            <el-button size="small" type="success" plain @click="openEditDialog(row)">分析工作台</el-button>
            <el-popconfirm
              title="删除后不可恢复，确认删除该文档？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建文档" width="720px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="文档名称" prop="title">
          <el-input v-model="createForm.title" placeholder="新文档" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="createForm.author" placeholder="作者/编者（可选）" />
        </el-form-item>
        <el-form-item label="朝代">
          <el-input v-model="createForm.era" placeholder="如 唐 / 宋 / 清" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="6"
            placeholder="粘贴文言文原文，可留空后续补充"
          />
        </el-form-item>
        <el-form-item label="文档描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文档描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateConfirm" :loading="creating">新建文档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑文档" width="640px">
      <el-form :model="editForm" label-width="72px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="如《赤壁赋》" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="editForm.author" placeholder="作者/编者" />
        </el-form-item>
        <el-form-item label="时代">
          <el-input v-model="editForm.era" placeholder="如宋" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="editForm.content" type="textarea" :rows="8" placeholder="粘贴完整文言文内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import AuthToolbar from "@/components/layout/AuthToolbar.vue";

const router = useRouter();
const route = useRoute();
const store = useTextStore();
const searchInput = ref("");
const activeKeyword = ref("");
const createDialogVisible = ref(false);
const creating = ref(false);
const createFormRef = ref();
const createForm = reactive({
  title: "新文档",
  author: "",
  era: "",
  content: "",
  description: ""
});
const editDialogVisible = ref(false);
const editForm = ref({
  id: null,
  title: "",
  author: "",
  era: "",
  content: ""
});
const currentProjectId = computed(() => (route.params.projectId ? Number(route.params.projectId) : null));

const createRules = {
  title: [{ required: true, message: "请输入文档名称", trigger: "blur" }]
};

onMounted(async () => {
  await store.loadTexts(undefined, currentProjectId.value);
});

watch(
  () => route.params.projectId,
  async (val, oldVal) => {
    if (val === oldVal) return;
    await store.loadTexts(undefined, val ? Number(val) : null);
  }
);

const filteredDocuments = computed(() => {
  if (!activeKeyword.value) {
    return store.texts;
  }
  const text = activeKeyword.value;
  return store.texts.filter((item) => {
    const title = (item.title || "").toLowerCase();
    const author = (item.author || "").toLowerCase();
    return title.includes(text) || author.includes(text);
  });
});

const categoryLabels = {
  warfare: "战争纪实",
  travelogue: "游记地理",
  biography: "人物传记",
  agriculture: "农书类",
  crafts: "工艺技术",
  other: "其他",
  unknown: "待识别"
};

const formatCategory = (_row, _column, value) => categoryLabels[value] || value || "未分类";

const formatDate = (_row, _column, value) => {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
};

const applySearch = () => {
  activeKeyword.value = (searchInput.value || "").trim().toLowerCase();
};

const openDocument = async (text) => {
  if (!text?.id) {
    return;
  }
  try {
    store.selectedTextId = text.id;
    store.selectedText = text;
    router.push({
      name: "text-workspace",
      params: { id: text.id },
      query: { projectId: currentProjectId.value ?? undefined }
    });
  } catch (error) {
    console.error("Failed to open document:", error);
    ElMessage.error("加载文档失败");
  }
};

const openEditDialog = (text) => {
  if (!text?.id) return;
  editForm.value = {
    id: text.id,
    title: text.title || "",
    author: text.author || "",
    era: text.era || "",
    content: text.content || ""
  };
  editDialogVisible.value = true;
};

const handleDelete = async (text) => {
  if (!text?.id) {
    return;
  }
  try {
    await store.deleteText(text.id);
    ElMessage.success("文档已删除");
  } catch (error) {
    console.error("Failed to delete text:", error);
    ElMessage.error("删除失败，请稍后再试");
  }
};

const handleSaveEdit = async () => {
  if (!editForm.value.id) {
    return;
  }
  try {
    await store.updateText(editForm.value.id, {
      title: editForm.value.title,
      author: editForm.value.author,
      era: editForm.value.era,
      content: editForm.value.content,
      category: editForm.value.category
    });
    ElMessage.success("文档已保存");
    editDialogVisible.value = false;
  } catch (error) {
    console.error("Failed to save text:", error);
    ElMessage.error("保存失败，请稍后再试");
  }
};

const handleCreate = () => {
  createForm.title = "新文档";
  createForm.description = "";
  createForm.author = "";
  createForm.era = "";
  createForm.content = "";
  createDialogVisible.value = true;
};

const goProjectList = () => {
  router.push("/projects");
};

const handleCreateConfirm = () => {
  createFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    creating.value = true;
    try {
      const payload = {
        title: createForm.title || "新文档",
        author: createForm.author || undefined,
        era: createForm.era || undefined,
        content: createForm.content || "",
        description: createForm.description || "",
        projectId: currentProjectId.value ?? undefined
      };
      const created = await store.uploadNewText(payload);
      createDialogVisible.value = false;
      if (created?.id) {
        // 确保进入结构标注阶段
        localStorage.setItem("dashboard-stage", "structure");
        router.push({
          name: "text-workspace",
          params: { id: created.id },
          query: { projectId: currentProjectId.value ?? undefined }
        });
      }
    } catch (e) {
      ElMessage.error("新建文档失败，请稍后再试");
    } finally {
      creating.value = false;
    }
  });
};
</script>

<style scoped>
.documents-page {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background: #f7f8fc;
  min-height: calc(100vh - 48px);
}

.header-bar {
  display: flex;
  justify-content: flex-start;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.page-header h1 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 4px 0 0;
  color: #6b7280;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 240px;
}

.stats {
  display: flex;
  gap: 16px;
}

.stat-card {
  flex: 1;
  background: white;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.stat-card .label {
  display: block;
  color: #9ca3af;
  font-size: 12px;
  margin-bottom: 4px;
}

.stat-card .value {
  font-size: 28px;
  font-weight: 600;
  color: #111827;
}

.table-card {
  flex: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
