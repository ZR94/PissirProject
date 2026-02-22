import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import  { useAuthStore }  from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { auth: true }
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/AboutView.vue'),
      meta: { auth: true, role: 'administrators' }
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue'),
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

  const requiredRole = to.meta.role as string | undefined;
  if (requiredRole && !auth.hasRole(requiredRole)) {
    return { path: "/" };
  }

  return true;
});

export default router
