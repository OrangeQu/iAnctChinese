<template>
  <div class="section" v-if="loading">Loading profile...</div>
  <template v-else>
    <div class="section">
      <div class="cards">
        <div class="card">
          <h3>{{ profile?.username || "-" }}</h3>
          <p>Username</p>
        </div>
        <div class="card alt">
          <h3>{{ profile?.email || "-" }}</h3>
          <p>Email</p>
        </div>
        <div class="card">
          <h3>{{ formatDate(profile?.lastLoginTime) }}</h3>
          <p>Last login</p>
        </div>
      </div>
    </div>
    <div class="section">
      <div class="grid-two">
        <div class="form-card">
          <h2>Update Email</h2>
          <form @submit.prevent="handleEmail">
            <div class="row">
              <label>Email</label>
              <input class="input" type="email" v-model="emailForm.email" required />
            </div>
            <button class="button secondary" type="submit">Save Email</button>
          </form>
        </div>
        <div class="form-card">
          <h2>Change Password</h2>
          <form @submit.prevent="handlePassword">
            <div class="row">
              <label>Current Password</label>
              <input class="input" type="password" v-model="passwordForm.currentPassword" required />
            </div>
            <div class="row">
              <label>New Password</label>
              <input class="input" type="password" v-model="passwordForm.newPassword" required />
            </div>
            <button class="button primary" type="submit">Update Password</button>
            <div class="toast" v-if="message">{{ message }}</div>
          </form>
        </div>
      </div>
    </div>
  </template>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { useAppStore } from "@/stores/app";
import {
  getCurrentUser,
  updateEmail,
  updatePassword,
  type UserInfoResponse,
} from "@/services/api/user";

const appStore = useAppStore();

const profile = ref<UserInfoResponse | null>(null);
const loading = ref(true);
const message = ref("");

const emailForm = reactive({
  email: "",
});

const passwordForm = reactive({
  currentPassword: "",
  newPassword: "",
});

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const loadProfile = async () => {
  profile.value = await getCurrentUser();
  emailForm.email = profile.value.email || "";
};

const handleEmail = async () => {
  message.value = "";
  const updated = await updateEmail({ email: emailForm.email });
  profile.value = updated;
  message.value = "Email updated.";
};

const handlePassword = async () => {
  message.value = "";
  await updatePassword({
    currentPassword: passwordForm.currentPassword,
    newPassword: passwordForm.newPassword,
  });
  passwordForm.currentPassword = "";
  passwordForm.newPassword = "";
  message.value = "Password updated.";
};

watch(
  () => appStore.refreshKey,
  () => {
    loadProfile();
  }
);

onMounted(async () => {
  loading.value = true;
  await loadProfile();
  loading.value = false;
});
</script>
