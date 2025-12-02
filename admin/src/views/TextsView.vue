<template>
  <div class="panel view">
    <header class="toolbar">
      <div>
        <strong>文献管理</strong>
        <p>查看、筛选、导出或删除文本</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="category" placeholder="类别" clearable @change="loadTexts">
          <el-option label="全部" value="" />
          <el-option label="战争" value="warfare" />
          <el-option label="游记" value="travelogue" />
          <el-option label="传记" value="biography" />
        </el-select>
        <el-button :loading="loading" @click="loadTexts">刷新</el-button>
      </div>
    </header>
    <el-table :data="texts" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="category" label="类别" width="120" />
      <el-table-column prop="author" label="作者" width="120" />
      <el-table-column prop="era" label="时代" width="120" />
      <el-table-column prop="createdAt" label="创建时间" min-width="140">
        <template #default="{ row }">
          {{ row.createdAt || "—" }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-select
            v-model="tempCategory[row.id]"
            placeholder="更新分类"
            size="small"
            style="width: 130px; margin-right: 8px;"
          >
            <el-option label="战争" value="warfare" />
            <el-option label="游记" value="travelogue" />
            <el-option label="传记" value="biography" />
            <el-option label="其他" value="other" />
          </el-select>
          <el-button type="primary" link size="small" @click="saveCategory(row)">保存</el-button>
          <el-button type="danger" link size="small" @click="confirmDelete(row)">删除</el-button>
          <el-button type="success" link size="small" @click="exportDoc(row)">导出</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { fetchTexts, updateTextCategory, deleteText, exportText } from "@/api/texts";

const texts = ref([]);
const loading = ref(false);
const category = ref("");
const tempCategory = reactive({});

const loadTexts = async () => {
  loading.value = true;
  try {
    const { data } = await fetchTexts(category.value || undefined);
    texts.value = data;
    data.forEach((item) => {
      tempCategory[item.id] = item.category;
    });
  } finally {
    loading.value = false;
  }
};

const saveCategory = async (row) => {
  const next = tempCategory[row.id];
  if (!next) return;
  await updateTextCategory(row.id, next);
  ElMessage.success("已更新分类");
  await loadTexts();
};

const confirmDelete = (row) => {
  ElMessageBox.confirm(`确定删除「${row.title}」吗？`, "提示", {
    type: "warning"
  }).then(async () => {
    await deleteText(row.id);
    ElMessage.success("已删除");
    await loadTexts();
  });
};

const exportDoc = async (row) => {
  const { data } = await exportText(row.id);
  const blob = new Blob([data], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `text-${row.id}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("已导出");
};

onMounted(loadTexts);
</script>

<style scoped>
.view {
  padding: 14px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}
</style>
