<template>
  <div class="panel view">
    <header class="toolbar">
      <div>
        <strong>模型任务</strong>
        <p>查看自动分类、实体识别、句读等任务状态</p>
      </div>
      <el-button :loading="loading" @click="loadJobs">刷新</el-button>
    </header>
    <el-table :data="jobs" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="textId" label="文本 ID" width="100" />
      <el-table-column prop="jobType" label="类型" width="150" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column prop="completedAt" label="完成时间" min-width="160" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="retry(row)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !jobs.length" description="暂未有任务" />
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { listJobs, retryJob } from "@/api/modelJobs";

const jobs = ref([]);
const loading = ref(false);

const loadJobs = async () => {
  loading.value = true;
  try {
    const { data } = await listJobs();
    jobs.value = data;
  } finally {
    loading.value = false;
  }
};

const retry = async (row) => {
  await retryJob(row.id);
  ElMessage.success("已重新提交任务");
  await loadJobs();
};

const statusTag = (status) => {
  if (status === "FAILED") return "danger";
  if (status === "SUCCESS") return "success";
  return "info";
};

onMounted(loadJobs);
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
</style>
