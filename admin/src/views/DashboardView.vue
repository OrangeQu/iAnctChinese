<template>
  <div class="grid">
    <section class="panel stats">
      <div class="stat" v-for="item in stats" :key="item.label">
        <p class="label">{{ item.label }}</p>
        <h2>{{ loading ? "…" : item.value }}</h2>
      </div>
    </section>
    <section class="panel timeline">
      <header>
        <div>
          <strong>最新模型任务</strong>
          <p>查看自动分类、标注与摘要的进度</p>
        </div>
        <el-button type="primary" text @click="$router.push('/model-jobs')">
          查看全部
        </el-button>
      </header>
      <el-timeline v-if="recentJobs.length" class="jobs">
        <el-timeline-item
          v-for="job in recentJobs"
          :key="job.id"
          :timestamp="formatTime(job.createdAt)"
          :type="tagType(job.status)"
        >
          <div class="job-row">
            <span class="job-type">{{ job.jobType }}</span>
            <el-tag :type="tagType(job.status)" size="small">{{ job.status }}</el-tag>
          </div>
          <small>文本ID: {{ job.textId }}</small>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无任务" />
    </section>
    <section class="panel tips">
      <h3>快速操作</h3>
      <el-button type="primary" @click="$router.push('/texts')">管理文献</el-button>
      <el-button @click="$router.push('/annotations')">审查标注</el-button>
      <el-button @click="$router.push('/model-jobs')">查看模型任务</el-button>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { fetchAdminOverview } from "@/api/dashboard";

const loading = ref(false);
const stats = reactive([
  { key: "textCount", label: "文献数", value: "—" },
  { key: "entityCount", label: "实体标注", value: "—" },
  { key: "relationCount", label: "关系标注", value: "—" },
  { key: "modelJobCount", label: "模型任务", value: "—" }
]);
const recentJobs = ref([]);

const load = async () => {
  loading.value = true;
  try {
    const { data } = await fetchAdminOverview();
    stats.forEach((item) => {
      item.value = data?.[item.key] ?? "—";
    });
    recentJobs.value = data?.recentJobs || [];
  } catch (err) {
    const msg = err.response?.data?.message || "加载仪表盘失败";
    ElMessage.error(msg);
  } finally {
    loading.value = false;
  }
};

const tagType = (status) => {
  if (!status) return "info";
  switch (status) {
    case "SUCCEEDED":
      return "success";
    case "FAILED":
      return "danger";
    case "RUNNING":
      return "warning";
    default:
      return "info";
  }
};

const formatTime = (value) => {
  if (!value) return "";
  return String(value).replace("T", " ");
};

onMounted(load);
</script>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 2fr 2fr 1fr;
  gap: 14px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px;
}

.stat {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f3f6ff;
  border: 1px solid #e5e7eb;
}

.label {
  margin: 0;
  color: #6b7280;
}

.timeline {
  padding: 16px;
}

.timeline header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tips {
  padding: 16px;
  display: grid;
  gap: 10px;
  align-content: flex-start;
}

.jobs {
  margin-top: 10px;
}

.job-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
