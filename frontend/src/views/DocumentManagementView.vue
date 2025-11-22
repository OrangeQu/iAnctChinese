<template>
  <div class="page-container" v-loading="store.loading">
    <div class="header-section">
      <div>
        <h1>文档管理</h1>
        <p class="subtitle">管理与分析您的古籍文献库</p>
      </div>
      <div class="actions">
        <div class="search-wrapper">
          <el-icon class="search-icon"><Search /></el-icon>
          <input 
            v-model="keyword" 
            placeholder="搜索标题或作者..." 
            class="search-input"
          />
        </div>
        <el-button type="primary" @click="openUploadDrawer" size="large">
          <el-icon class="mr-2"><Plus /></el-icon>
          新建文档
        </el-button>
      </div>
    </div>

    <div class="content-layout">
      <div class="card table-card">
        <el-table 
          :data="filteredDocuments" 
          style="width: 100%" 
          size="large"
          :header-cell-style="{ background: '#FAFAFA', color: '#666', fontWeight: '600' }"
        >
          <el-table-column prop="title" label="标题" min-width="240">
            <template #default="{ row }">
              <div class="title-cell">
                <div class="doc-icon-mini" :class="row.category || 'unknown'">
                  {{ (row.category || '文').charAt(0).toUpperCase() }}
                </div>
                <span class="doc-title" @click="openDocument(row)">{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="author" label="作者" width="160">
             <template #default="{ row }">
               <span class="author-text">{{ row.author || '佚名' }}</span>
             </template>
          </el-table-column>
          <el-table-column prop="category" label="类型" width="140">
            <template #default="{ row }">
              <span class="category-badge" :class="row.category">{{ formatCategory(row.category) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="era" label="时代" width="120">
             <template #default="{ row }">
               <span class="era-tag">{{ row.era || '-' }}</span>
             </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180">
            <template #default="{ row }">
              <span class="date-text">{{ formatDate(row.updatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-tooltip content="查看文档" placement="top">
                  <el-button circle size="small" @click="openDocument(row)">
                    <el-icon><View /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="编辑信息" placement="top">
                  <el-button circle size="small" @click="openEditDialog(row)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-popconfirm
                  title="确认删除该文档？"
                  @confirm="handleDelete(row)"
                >
                  <template #reference>
                    <el-button circle size="small" type="danger" plain>
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>
        
        <div v-if="filteredDocuments.length === 0" class="empty-state">
           <el-empty description="暂无文档，请点击右上角新建" />
        </div>
      </div>
    </div>

    <TextUploadDrawer ref="uploadDrawerRef" />

    <el-dialog v-model="editDialogVisible" title="编辑文档信息" width="500px" align-center>
      <el-form :model="editForm" label-width="80px" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="作者">
              <el-input v-model="editForm.author" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时代">
              <el-input v-model="editForm.era" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="正文内容">
          <el-input v-model="editForm.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存更改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Search, Plus, View, Edit, Delete } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useTextStore } from "@/store/textStore";
import TextUploadDrawer from "@/components/layout/TextUploadDrawer.vue";

const router = useRouter();
const store = useTextStore();
const keyword = ref("");
const editDialogVisible = ref(false);
const uploadDrawerRef = ref(null);

const editForm = ref({
  id: null,
  title: "",
  author: "",
  era: "",
  content: ""
});

const openUploadDrawer = () => {
  uploadDrawerRef.value?.open();
};

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

const formatCategory = (value) => categoryLabels[value] || value || "未分类";

const formatDate = (value) => {
  if (!value) return "-";
  return new Date(value).toLocaleDateString();
};

const openDocument = async (text) => {
  if (!text?.id) return;
  store.selectedTextId = text.id;
  store.selectedText = text;
  router.push({ name: "text-workspace", params: { id: text.id } });
};

const openEditDialog = (text) => {
  editForm.value = { ...text };
  editDialogVisible.value = true;
};

const handleDelete = async (text) => {
  try {
    await store.deleteText(text.id);
    ElMessage.success("文档已删除");
  } catch (error) {
    ElMessage.error("删除失败");
  }
};

const handleSaveEdit = async () => {
  try {
    await store.updateText(editForm.value.id, editForm.value);
    ElMessage.success("保存成功");
    editDialogVisible.value = false;
  } catch (error) {
    ElMessage.error("保存失败");
  }
};
</script>

<style scoped>
.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

h1 {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.subtitle {
  color: var(--text-secondary);
  margin: 0;
  font-size: 16px;
}

.actions {
  display: flex;
  gap: 16px;
  align-items: center;
}

.search-wrapper {
  position: relative;
  width: 320px;
}

.search-input {
  width: 100%;
  padding: 12px 16px 12px 44px;
  border-radius: 50px;
  border: 1px solid transparent;
  background: white;
  box-shadow: var(--shadow-sm);
  outline: none;
  transition: all 0.2s;
  font-size: 14px;
}

.search-input:focus {
  box-shadow: 0 0 0 2px rgba(0,0,0,0.05);
}

.search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-tertiary);
}

.table-card {
  padding: 0;
  overflow: hidden;
  border-radius: 24px;
  border: none;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.doc-icon-mini {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.doc-icon-mini.warfare { background: #FFE8E8; color: #FF6B6B; }
.doc-icon-mini.travelogue { background: #E8FDF5; color: #06D6A0; }
.doc-icon-mini.biography { background: #E8F4FF; color: #118AB2; }
.doc-icon-mini.unknown { background: #F0F0F0; color: #999; }

.doc-title {
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
  font-size: 15px;
}

.doc-title:hover {
  color: var(--primary-color);
}

.author-text {
  font-weight: 500;
  color: var(--text-primary);
}

.category-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
  background: #f0f0f0;
  color: #666;
}

.category-badge.warfare { color: #FF6B6B; background: #FFF0F0; }
.category-badge.travelogue { color: #06D6A0; background: #F0FDF9; }
.category-badge.biography { color: #118AB2; background: #F0F8FF; }

.era-tag {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid #eee;
  border-radius: 6px;
  font-size: 12px;
  color: #888;
}

.date-text {
  color: #999;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.empty-state {
  padding: 40px 0;
  display: flex;
  justify-content: center;
}
</style>
