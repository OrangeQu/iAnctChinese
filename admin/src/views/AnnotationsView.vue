<template>
  <div class="panel view">
    <header class="toolbar">
      <div>
        <strong>标注审查</strong>
        <p>查看指定文本的实体与关系，支持快速删除无效标注</p>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="textId" placeholder="文本 ID" style="width: 160px;" />
        <el-button type="primary" :loading="loading" @click="load">加载</el-button>
      </div>
    </header>
    <el-row :gutter="12">
      <el-col :span="12">
        <div class="panel inner">
          <div class="panel-title">实体 ({{ entities.length }})</div>
          <el-table :data="entities" height="420" size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="label" label="文本" min-width="120" />
            <el-table-column prop="category" label="类型" width="100" />
            <el-table-column prop="confidence" label="置信度" width="100" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="removeEntity(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel inner">
          <div class="panel-title">关系 ({{ relations.length }})</div>
          <el-table :data="relations" height="420" size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="source.label" label="源实体" min-width="100">
              <template #default="{ row }">{{ row.source?.label }}</template>
            </el-table-column>
            <el-table-column prop="target.label" label="目标实体" min-width="100">
              <template #default="{ row }">{{ row.target?.label }}</template>
            </el-table-column>
            <el-table-column prop="relationType" label="关系类型" width="120" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="removeRelation(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && !entities.length && !relations.length" description="暂无数据" />
  </div>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { fetchEntities, fetchRelations, deleteEntity, deleteRelation } from "@/api/annotations";

const textId = ref("");
const entities = ref([]);
const relations = ref([]);
const loading = ref(false);

const load = async () => {
  if (!textId.value) return;
  loading.value = true;
  try {
    const [ent, rel] = await Promise.all([
      fetchEntities(textId.value),
      fetchRelations(textId.value)
    ]);
    entities.value = ent.data;
    relations.value = rel.data;
  } finally {
    loading.value = false;
  }
};

const removeEntity = async (row) => {
  await deleteEntity(row.id);
  ElMessage.success("已删除实体");
  await load();
};

const removeRelation = async (row) => {
  await deleteRelation(row.id);
  ElMessage.success("已删除关系");
  await load();
};
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

.inner {
  padding: 12px;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 8px;
}
</style>
