<template>
  <div class="panel view">
    <header class="toolbar">
      <div>
        <strong>用户管理</strong>
        <p>查看数据库中的用户，支持新增与启用/禁用</p>
      </div>
      <el-button type="primary" @click="openDialog">新增用户</el-button>
    </header>
    <el-table :data="users" size="small" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="createTime" label="创建时间" min-width="180">
        <template #default="{ row }">
          {{ formatTime(row.createTime) || "—" }}
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="上次登录" min-width="180">
        <template #default="{ row }">
          {{ formatTime(row.lastLoginTime) || "—" }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? "启用" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="toggle(row)">
            {{ row.enabled ? "禁用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新增用户" width="420px">
      <el-form :model="form" label-width="90px" :rules="rules" ref="formRef">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" autocomplete="off" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { fetchUsers, createUserApi, updateUserStatus } from "@/api/users";

const users = ref([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const formRef = ref(null);
const form = reactive({
  username: "",
  email: "",
  password: ""
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 50, message: "3-50 个字符", trigger: "blur" }
  ],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" }
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "至少 6 位", trigger: "blur" }
  ]
};

const loadUsers = async () => {
  loading.value = true;
  try {
    const { data } = await fetchUsers();
    users.value = data || [];
  } catch (err) {
    const msg = err.response?.data?.message || "加载失败";
    ElMessage.error(msg);
  } finally {
    loading.value = false;
  }
};

const openDialog = () => {
  form.username = "";
  form.email = "";
  form.password = "";
  dialogVisible.value = true;
};

const submit = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    saving.value = true;
    try {
      await createUserApi(form);
      ElMessage.success("新增用户成功");
      dialogVisible.value = false;
      await loadUsers();
    } catch (err) {
      const msg = err.response?.data?.message || "创建失败";
      ElMessage.error(msg);
    } finally {
      saving.value = false;
    }
  });
};

const toggle = (row) => {
  const action = row.enabled ? "禁用" : "启用";
  ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, "提示", {
    type: "warning"
  }).then(async () => {
    await updateUserStatus(row.id, !row.enabled);
    ElMessage.success(`${action}成功`);
    await loadUsers();
  });
};

const formatTime = (value) => {
  if (!value) return "";
  return String(value).replace("T", " ");
};

onMounted(loadUsers);
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
