import { createRouter, createWebHistory } from "vue-router";
import DashboardView from "../views/DashboardView.vue";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "dashboard",
      component: DashboardView,
      meta: { auth: true },
    },
    {
      path: "/toll",
      name: "toll",
      component: () => import("../views/TollView.vue"),
      meta: { auth: true, roles: ["employees", "administrators"] },
    },
    {
      path: "/infrastructure",
      name: "infrastructure",
      component: () => import("../views/InfrastructureView.vue"),
      meta: { auth: true, roles: ["employees", "administrators"] },
    },
    {
      path: "/payments",
      name: "payments",
      component: () => import("../views/PaymentsView.vue"),
      meta: { auth: true, roles: ["customers", "employees", "administrators"] },
    },
    {
      path: "/admin",
      name: "admin",
      component: () => import("../views/AboutView.vue"),
      meta: { auth: true, roles: ["administrators"] },
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();

  const needsAuth = !!to.meta.auth;
  if (needsAuth && !auth.isAuthenticated) {
    auth.login();
    return false;
  }

  const requiredRoles = to.meta.roles as string[] | undefined;
  if (requiredRoles && !auth.hasAnyRole(requiredRoles)) {
    return { path: "/" };
  }

  return true;
});

export default router;
