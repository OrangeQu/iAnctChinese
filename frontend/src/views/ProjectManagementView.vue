<template>
  <div class="project-page" v-loading="projectStore.loading">
    <div class="page-head">
      <div class="title">
        <h2>项目管理</h2>
        <p class="sub">查看我负责或参与的项目，管理成员与文档</p>
      </div>
      <el-button type="primary" @click="openCreate = true">+ 新建项目</el-button>
    </div>

    <el-card class="project-card">
      <template #header>
        <div class="card-head">
          <span>我的项目</span>
        </div>
      </template>
      <el-table :data="projects" v-loading="projectStore.loading" border>
        <el-table-column prop="name" label="名称" min-width="200">
          <template #default="{ row }">
            <div class="proj-name">
              <el-icon><Folder /></el-icon>
              <span>{{ row.name }}</span>
              <el-tag v-if="row.ownerId === currentUserId" size="small" type="warning">组长</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="组长" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="成员" min-width="140">
          <template #default="{ row }">
            <div class="member-list">
              <el-tag v-for="m in (row.members || []).slice(0, 3)" :key="m.userId" size="small">
                {{ m.username }}
              </el-tag>
              <span v-if="(row.members || []).length > 3">...</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="openProject(row)">打开项目</el-button>
            <el-button size="small" @click="showMembers(row)">项目详情</el-button>
            <el-popconfirm
              v-if="row.ownerId === currentUserId"
              title="删除项目会同时软删除项目文档，确认删除？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button size="small" type="danger">删除项目</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建项目 -->
    <el-dialog v-model="openCreate" title="新建项目" width="520px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="4"
            placeholder="用于说明团队/文档范围"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openCreate = false">取消</el-button>
        <el-button type="primary" :loading="projectStore.saving" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 成员管理 -->
    <el-dialog v-model="openMembers" :title="activeProject?.name || '项目详情'" width="620px">
      <div class="member-actions">
        <el-input
          v-model="memberName"
          placeholder="输入用户名添加成员"
          size="small"
          style="width: 240px"
        />
        <el-button
          size="small"
          type="primary"
          :disabled="!canManage"
          @click="handleAddMember"
        >
          添加成员
        </el-button>
      </div>
      <el-table :data="activeProject?.members || []" border size="small">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              :disabled="!canManage || row.username === activeProject?.ownerName"
              @click="handleRemove(row)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Folder } from "@element-plus/icons-vue";
import { useProjectStore } from "@/store/projectStore";
import { useAuthStore } from "@/store/authStore";

const router = useRouter();
const projectStore = useProjectStore();
const authStore = useAuthStore();

const projects = computed(() => projectStore.projects || []);
const currentUserId = computed(() => authStore.user?.id);
const canManage = computed(() => activeProject.value?.ownerId === currentUserId.value);

const openCreate = ref(false);
const createForm = ref({ name: "", description: "" });
const createFormRef = ref();
const createRules = {
  name: [{ required: true, message: "请输入项目名称", trigger: "blur" }]
};

const openMembers = ref(false);
const activeProject = ref(null);
const memberName = ref("");

onMounted(async () => {
  await projectStore.fetchMyProjects();
});

const handleCreate = () => {
  createFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    const data = await projectStore.createProject(createForm.value);
    ElMessage.success("项目已创建");
    openCreate.value = false;
    createForm.value = { name: "", description: "" };
    openMembers.value = false;
    activeProject.value = data;
  });
};

const showMembers = (row) => {
  activeProject.value = row;
  openMembers.value = true;
};

const handleAddMember = async () => {
  if (!memberName.value) return;
  try {
    await projectStore.addMember(activeProject.value.id, memberName.value);
    memberName.value = "";
    ElMessage.success("已添加成员");
    activeProject.value = projectStore.projects.find((p) => p.id === activeProject.value.id);
  } catch (e) {
    ElMessage.error(e.response?.data?.message || "添加失败");
  }
};

const handleRemove = async (member) => {
  try {
    await projectStore.removeMember(activeProject.value.id, member.username);
    ElMessage.success("已移除成员");
    activeProject.value = projectStore.projects.find((p) => p.id === activeProject.value.id);
  } catch (e) {
    ElMessage.error(e.response?.data?.message || "移除失败");
  }
};

const handleDelete = async (row) => {
  await projectStore.deleteProject(row.id);
  ElMessage.success("项目已删除");
  if (openMembers.value && activeProject.value?.id === row.id) {
    openMembers.value = false;
    activeProject.value = null;
  }
};

const openProject = (row) => {
  router.push({ path: `/projects/${row.id}/documents` });
};
</script>

<style scoped>
.project-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title h2 {
  margin: 0;
}
.title .sub {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
}
.project-card {
  width: 100%;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.proj-name {
  display: flex;
  align-items: center;
  gap: 6px;
}
.member-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.member-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  align-items: center;
}
</style>
