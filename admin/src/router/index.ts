import { createRouter, createWebHistory } from "vue-router";
import LoginView from "@/views/LoginView.vue";
import DashboardView from "@/views/DashboardView.vue";
import TextsView from "@/views/TextsView.vue";
import TextDetailView from "@/views/TextDetailView.vue";
import ProjectsView from "@/views/ProjectsView.vue";
import ProjectDetailView from "@/views/ProjectDetailView.vue";
import AnnotationsView from "@/views/AnnotationsView.vue";
import QualityEntitiesView from "@/views/QualityEntitiesView.vue";
import QualityRelationsView from "@/views/QualityRelationsView.vue";
import ModelJobsView from "@/views/ModelJobsView.vue";
import ProfileView from "@/views/ProfileView.vue";
import UsersView from "@/views/UsersView.vue";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/dashboard" },
    {
      path: "/login",
      name: "login",
      component: LoginView,
      meta: { layout: "auth", title: "Login" },
    },
    {
      path: "/dashboard",
      name: "dashboard",
      component: DashboardView,
      meta: { title: "Overview" },
    },
    {
      path: "/texts",
      name: "texts",
      component: TextsView,
      meta: { title: "Texts" },
    },
    {
      path: "/texts/:id",
      name: "text-detail",
      component: TextDetailView,
      meta: { title: "Text Detail" },
    },
    {
      path: "/projects",
      name: "projects",
      component: ProjectsView,
      meta: { title: "Projects" },
    },
    {
      path: "/projects/:id",
      name: "project-detail",
      component: ProjectDetailView,
      meta: { title: "Project Detail" },
    },
    {
      path: "/annotations",
      name: "annotations",
      component: AnnotationsView,
      meta: { title: "Annotations" },
    },
    {
      path: "/quality/entities",
      name: "quality-entities",
      component: QualityEntitiesView,
      meta: { title: "Quality - Entities" },
    },
    {
      path: "/quality/relations",
      name: "quality-relations",
      component: QualityRelationsView,
      meta: { title: "Quality - Relations" },
    },
    {
      path: "/model-jobs",
      name: "model-jobs",
      component: ModelJobsView,
      meta: { title: "Model Jobs" },
    },
    {
      path: "/users",
      name: "users",
      component: UsersView,
      meta: { title: "Users" },
    },
    {
      path: "/me",
      name: "me",
      component: ProfileView,
      meta: { title: "Profile" },
    },
  ],
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (!authStore.token) {
    authStore.restore();
  }
  if (to.path === "/login" && authStore.token) {
    return { path: "/dashboard" };
  }
  if (to.path === "/login") return true;
  if (!authStore.token) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (!authStore.user) {
    await authStore.fetchCurrentUser();
  }
  return true;
});

export default router;
