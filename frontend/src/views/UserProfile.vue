<template>
  <div class="profile-page">
    <AuthToolbar :show-back="true" back-to="/documents">
      <template #left>
        <h2 class="page-title">个人中心</h2>
      </template>
    </AuthToolbar>

    <div class="profile-grid" v-loading="loadingProfile">
      <section class="panel">
        <header class="panel-header">
          <div>
            <p class="eyebrow">账号信息</p>
            <h3 class="panel-title">基础资料</h3>
          </div>
        </header>
        <ul class="info-list" v-if="profile">
          <li>
            <span class="label">用户名</span>
            <span class="value">{{ profile.username }}</span>
          </li>
          <li>
            <span class="label">邮箱</span>
            <span class="value">{{ profile.email }}</span>
          </li>
          <li v-if="profile.createTime">
            <span class="label">创建时间</span>
            <span class="value">{{ formatDate(profile.createTime) }}</span>
          </li>
          <li v-if="profile.lastLoginTime">
            <span class="label">上次登录</span>
            <span class="value">{{ formatDate(profile.lastLoginTime) }}</span>
          </li>
        </ul>
      </section>

      <section class="panel">
        <header class="panel-header">
          <div>
            <p class="eyebrow">安全</p>
            <h3 class="panel-title">修改邮箱</h3>
          </div>
        </header>
        <el-form ref="emailFormRef" :model="emailForm" :rules="emailRules" label-position="top">
          <el-form-item label="新邮箱" prop="email">
            <el-input v-model="emailForm.email" placeholder="请输入新的邮箱地址" />
          </el-form-item>
          <div class="actions">
            <el-button type="primary" @click="handleUpdateEmail" :loading="emailUpdating">保存邮箱</el-button>
          </div>
        </el-form>
      </section>

      <section class="panel">
        <header class="panel-header">
          <div>
            <p class="eyebrow">安全</p>
            <h3 class="panel-title">修改密码</h3>
          </div>
        </header>
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
          <el-form-item label="当前密码" prop="currentPassword">
            <el-input v-model="passwordForm.currentPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
          </el-form-item>
          <div class="actions">
            <el-button type="primary" @click="handleChangePassword" :loading="passwordUpdating">更新密码</el-button>
          </div>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import AuthToolbar from "@/components/layout/AuthToolbar.vue";
import { useAuthStore } from "@/store/authStore";

const authStore = useAuthStore();

const loadingProfile = ref(false);
const emailUpdating = ref(false);
const passwordUpdating = ref(false);

const profile = computed(() => authStore.user || {});

const emailFormRef = ref();
const passwordFormRef = ref();

const emailForm = reactive({
  email: profile.value?.email || ""
});

const passwordForm = reactive({
  currentPassword: "",
  newPassword: "",
  confirmPassword: ""
});

watch(
  () => profile.value?.email,
  (val) => {
    emailForm.email = val || "";
  }
);

const emailRules = {
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" }
  ]
};

const passwordRules = {
  currentPassword: [{ required: true, message: "请输入当前密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, max: 64, message: "密码长度需在6-64字符", trigger: "blur" }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback(new Error("请确认新密码"));
        if (value !== passwordForm.newPassword) return callback(new Error("两次输入的密码不一致"));
        return callback();
      },
      trigger: "blur"
    }
  ]
};

const formatDate = (value) => {
  if (!value) return "";
  try {
    return new Intl.DateTimeFormat("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).format(new Date(value));
  } catch (e) {
    return value;
  }
};

const fetchProfile = async () => {
  if (!authStore.token) return;
  loadingProfile.value = true;
  try {
    await authStore.loadUser();
  } finally {
    loadingProfile.value = false;
  }
};

const handleUpdateEmail = () => {
  emailFormRef.value.validate(async (valid) => {
    if (!valid) return;
    emailUpdating.value = true;
    const { success, message } = await authStore.updateEmail(emailForm.email);
    emailUpdating.value = false;
    if (success) {
      ElMessage.success(message);
    } else {
      ElMessage.error(message);
    }
  });
};

const handleChangePassword = () => {
  passwordFormRef.value.validate(async (valid) => {
    if (!valid) return;
    passwordUpdating.value = true;
    const payload = {
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword
    };
    const { success, message } = await authStore.changePassword(payload);
    passwordUpdating.value = false;
    if (success) {
      ElMessage.success(message);
      passwordForm.currentPassword = "";
      passwordForm.newPassword = "";
      passwordForm.confirmPassword = "";
    } else {
      ElMessage.error(message);
    }
  });
};

onMounted(() => {
  if (!authStore.user) {
    fetchProfile();
  }
});
</script>

<style scoped>
.profile-page {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.profile-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  background: white;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.04);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  margin: 4px 0 0 0;
  font-size: 18px;
  font-weight: 700;
}

.eyebrow {
  margin: 0;
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.info-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.info-list li {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fafafa;
}

.label {
  color: #6b7280;
}

.value {
  font-weight: 600;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
