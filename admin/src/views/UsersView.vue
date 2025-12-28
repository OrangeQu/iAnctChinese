<template>
  <div class="section" v-if="role && !isAdmin">
    <div class="banner">You do not have permission to manage users.</div>
  </div>
  <template v-else>
    <div class="section">
      <div class="banner" v-if="!role">
        Admin-only controls are shown. Role checks should be enforced by the backend.
      </div>
      <FilterBar>
        <input class="input" v-model="query" placeholder="Search username/email" />
        <select class="input" v-model="enabledFilter">
          <option value="all">All</option>
          <option value="true">Enabled</option>
          <option value="false">Disabled</option>
        </select>
        <button class="button secondary" type="button" @click="handleSearch">
          Search
        </button>
      </FilterBar>
      <div v-if="loading" class="section">Loading users...</div>
      <div v-else>
        <DataTable>
          <template #head>
            <th>ID</th>
            <th>Username</th>
            <th>Email</th>
            <th>Enabled</th>
            <th>Created</th>
            <th>Last Login</th>
            <th>Actions</th>
          </template>
          <tr v-if="users.length === 0">
            <td colspan="7">No users found.</td>
          </tr>
          <tr v-for="user in users" :key="user.id">
            <td>#{{ user.id }}</td>
            <td>{{ user.username }}</td>
            <td>{{ user.email }}</td>
            <td>
              <span :class="userEnabled(user) ? 'tag success' : 'tag danger'">
                {{ userEnabled(user) ? "Enabled" : "Disabled" }}
              </span>
            </td>
            <td>{{ formatDate(user.createTime || user.create_time) }}</td>
            <td>{{ formatDate(user.lastLoginTime || user.last_login_time) }}</td>
            <td>
              <button class="button ghost" type="button" @click="openReset(user)">
                Reset Password
              </button>
              <button
                class="button secondary"
                type="button"
                @click="toggleEnabled(user)"
              >
                {{ userEnabled(user) ? "Disable" : "Enable" }}
              </button>
            </td>
          </tr>
        </DataTable>
        <PaginationBar v-model:page="page" :total-pages="totalPages" />
      </div>
    </div>
  </template>

  <ModalDialog :open="resetOpen" title="Reset Password" @close="closeReset">
    <div class="row">
      <label>User</label>
      <div>{{ resetUser?.username }}</div>
    </div>
    <div class="row">
      <label>New password</label>
      <input class="input" v-model="resetPassword" type="text" />
    </div>
    <div class="row">
      <button class="button ghost" type="button" @click="generatePassword">
        Generate
      </button>
    </div>
    <template #footer>
      <button class="button secondary" type="button" @click="closeReset">
        Cancel
      </button>
      <button class="button primary" type="button" @click="submitReset">
        Confirm
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useAuthStore } from "@/stores/auth";
import DataTable from "@/components/DataTable.vue";
import FilterBar from "@/components/FilterBar.vue";
import PaginationBar from "@/components/PaginationBar.vue";
import ModalDialog from "@/components/ModalDialog.vue";
import { notify } from "@/services/toast";
import {
  listUsers,
  resetUserPassword,
  updateUserEnabled,
  type AdminUser,
  type AdminUserPage,
} from "@/services/api/adminUsers";

const authStore = useAuthStore();
const role = computed(() => authStore.user?.role);
const isAdmin = computed(() => !role.value || role.value === "ADMIN");

const users = ref<AdminUser[]>([]);
const loading = ref(false);
const query = ref("");
const enabledFilter = ref("all");
const page = ref(1);
const size = ref(10);
const totalPages = ref(1);

const resetOpen = ref(false);
const resetUser = ref<AdminUser | null>(null);
const resetPassword = ref("");

const userEnabled = (user: AdminUser) => {
  return user.enabled === true || user.enabled === 1;
};

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

const normalizePage = (data: AdminUser[] | AdminUserPage) => {
  if (Array.isArray(data)) {
    const needle = query.value.trim().toLowerCase();
    const filtered = data.filter((item) => {
      const hay = `${item.username} ${item.email}`.toLowerCase();
      const match = !needle || hay.includes(needle);
      const enabledMatch =
        enabledFilter.value === "all" ||
        (enabledFilter.value === "true" && userEnabled(item)) ||
        (enabledFilter.value === "false" && !userEnabled(item));
      return match && enabledMatch;
    });
    totalPages.value = Math.max(1, Math.ceil(filtered.length / size.value));
    if (page.value > totalPages.value) {
      page.value = totalPages.value;
    }
    const start = (page.value - 1) * size.value;
    users.value = filtered.slice(start, start + size.value);
    return;
  }

  users.value = data.content || [];
  totalPages.value =
    data.totalPages ||
    Math.max(1, Math.ceil((data.totalElements || users.value.length) / size.value));
};

const loadUsers = async () => {
  if (!isAdmin.value) return;
  loading.value = true;
  try {
    const params: Record<string, any> = {
      query: query.value || undefined,
      page: page.value - 1,
      size: size.value,
    };
    if (enabledFilter.value !== "all") {
      params.enabled = enabledFilter.value === "true";
    }
    const data = await listUsers(params);
    normalizePage(data);
  } catch (error: any) {
    notify(error?.response?.data?.message || error?.message || "Failed to load users.", "error");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  page.value = 1;
  loadUsers();
};

const toggleEnabled = async (user: AdminUser) => {
  const nextEnabled = !userEnabled(user);
  try {
    await updateUserEnabled(user.id, nextEnabled);
    notify(`User ${nextEnabled ? "enabled" : "disabled"}.`, "success");
    await loadUsers();
  } catch (error: any) {
    notify(error?.response?.data?.message || error?.message || "Failed to update user.", "error");
  }
};

const openReset = (user: AdminUser) => {
  resetUser.value = user;
  resetPassword.value = "";
  resetOpen.value = true;
};

const closeReset = () => {
  resetOpen.value = false;
  resetUser.value = null;
  resetPassword.value = "";
};

const generatePassword = () => {
  const seed = Math.random().toString(36).slice(2, 10);
  resetPassword.value = `Adm-${seed}`;
};

const submitReset = async () => {
  if (!resetUser.value) return;
  if (!resetPassword.value.trim()) {
    notify("Please enter a new password.", "error");
    return;
  }
  try {
    await resetUserPassword(resetUser.value.id, resetPassword.value.trim());
    notify("Password reset successfully.", "success");
    closeReset();
  } catch (error: any) {
    notify(error?.response?.data?.message || error?.message || "Failed to reset password.", "error");
  }
};

watch(page, () => {
  loadUsers();
});

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.06);
}

.tag.success {
  background: rgba(44, 110, 102, 0.15);
  color: #2c6e66;
}

.tag.danger {
  background: rgba(207, 75, 47, 0.15);
  color: #b8331f;
}
</style>
