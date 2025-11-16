<template>
  <div class="documents-page" v-loading="store.loading">
    <AuthToolbar back-to="/" />
    <header class="page-header">
      <div>
        <h1>文档管理</h1>
        <p class="subtitle">选择要分析的古籍文本，或上传新的材料</p>
      </div>
      <div class="actions">
        <el-input
          v-model="keyword"
          placeholder="按标题或作者搜索"
          class="search-input"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <TextUploadDrawer />
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
          <small>点击“进入文档”会打开分析工作台</small>
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
            <el-button size="small" type="success" plain @click="openDashboard(row)">分析工作台</el-button>
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import TextUploadDrawer from "@/components/layout/TextUploadDrawer.vue";
import AuthToolbar from "@/components/layout/AuthToolbar.vue";

const router = useRouter();
const store = useTextStore();
const keyword = ref("");

onMounted(async () => {
  if (!store.texts.length) {
    await store.loadTexts();
  }
});

const filteredDocuments = computed(() => {
  if (!keyword.value) {
    return store.texts;
  }
  const text = keyword.value.trim().toLowerCase();
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

const openDocument = async (text) => {
  if (!text?.id) {
    return;
  }
  try {
    // 预先设置选中文档以便界面立即有数据，真正数据加载由 TextWorkspace 完成
    store.selectedTextId = text.id;
    store.selectedText = text;
    router.push({ name: "text-workspace", params: { id: text.id } });
  } catch (error) {
    console.error("Failed to open document:", error);
    ElMessage.error("加载文档失败");
  }
};

const openDashboard = async (text) => {
  if (!text?.id) {
    return;
  }
  try {
    await store.selectText(text.id);
    router.push({ name: "dashboard" });
  } catch (error) {
    console.error("Failed to open dashboard:", error);
    ElMessage.error("加载文档失败");
  }
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

.card-header small {
  color: #9ca3af;
  font-weight: 400;
}

</style>
