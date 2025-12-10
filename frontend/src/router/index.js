import { createRouter, createWebHistory } from "vue-router";
import HomeView from "@/views/HomeView.vue";
import LoginView from "@/views/LoginView.vue";
import RegisterView from "@/views/RegisterView.vue";
import DashboardView from "@/views/DashboardView.vue";
import DocumentManagementView from "@/views/DocumentManagementView.vue";
import UserProfile from "@/views/UserProfile.vue";
import ProjectManagementView from "@/views/ProjectManagementView.vue";
import { useAuthStore } from "@/store/authStore";

const routes = [
  {
    path: "/",
    name: "home",
    component: HomeView
  },
  {
    path: "/login",
    name: "login",
    component: LoginView
  },
  {
    path: "/register",
    name: "register",
    component: RegisterView
  },
  {
    path: "/dashboard",
    name: "dashboard",
    component: DashboardView,
    meta: { requiresAuth: true }
  },
  {
    path: "/documents",
    name: "documents",
    component: DocumentManagementView,
    meta: { requiresAuth: true }
  },
  {
    path: "/projects",
    name: "projects",
    component: ProjectManagementView,
    meta: { requiresAuth: true }
  },
  {
    path: "/projects/:projectId/documents",
    name: "project-documents",
    component: DocumentManagementView,
    meta: { requiresAuth: true }
  },
  {
    path: "/profile",
    name: "profile",
    component: UserProfile,
    meta: { requiresAuth: true }
  },
  {
    path: "/texts/:id",
    name: "text-workspace",
    component: DashboardView,
    props: true,
    meta: { requiresAuth: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore();

  if (authStore.token && !authStore.user) {
    await authStore.loadUser();
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next("/login");
  } else if (authStore.isAuthenticated && (to.name === "login" || to.name === "register")) {
    next("/dashboard");
  } else {
    next();
  }
});

export default router;
