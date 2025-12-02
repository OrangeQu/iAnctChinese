import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import LoginView from "@/views/LoginView.vue";
import DashboardView from "@/views/DashboardView.vue";
import TextsView from "@/views/TextsView.vue";
import UsersView from "@/views/UsersView.vue";
import AnnotationsView from "@/views/AnnotationsView.vue";
import ModelJobsView from "@/views/ModelJobsView.vue";
import SettingsView from "@/views/SettingsView.vue";

const routes = [
  { path: "/login", name: "login", component: LoginView },
  {
    path: "/",
    redirect: "/dashboard"
  },
  {
    path: "/dashboard",
    name: "dashboard",
    component: DashboardView,
    meta: { requiresAuth: true }
  },
  {
    path: "/texts",
    name: "texts",
    component: TextsView,
    meta: { requiresAuth: true }
  },
  {
    path: "/annotations",
    name: "annotations",
    component: AnnotationsView,
    meta: { requiresAuth: true }
  },
  {
    path: "/model-jobs",
    name: "model-jobs",
    component: ModelJobsView,
    meta: { requiresAuth: true }
  },
  {
    path: "/users",
    name: "users",
    component: UsersView,
    meta: { requiresAuth: true }
  },
  {
    path: "/settings",
    name: "settings",
    component: SettingsView,
    meta: { requiresAuth: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore();
  if (auth.hasToken && !auth.user && to.meta.requiresAuth) {
    await auth.fetchProfile();
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    next({ name: "login", query: { redirect: to.fullPath } });
  } else if (to.name === "login" && auth.isAuthenticated) {
    next({ name: "dashboard" });
  } else {
    next();
  }
});

export default router;
