import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/auth/LoginView.vue"),
      meta: { requiresGuest: true },
    },
    {
      path: "/register",
      name: "register",
      component: () => import("@/views/auth/RegisterView.vue"),
      meta: { requiresGuest: true },
    },
    {
      path: "/dashboard",
      name: "dashboard",
      component: () => import("@/views/DashboardView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/",
      redirect: "/dashboard",
    },
  ],
});

// 路由守卫
router.beforeEach((to) => {
  const userStore = useUserStore();
  const isAuthenticated = !!userStore.accessToken;

  // 需要登录但未登录 → 跳登录页
  if (to.meta.requiresAuth && !isAuthenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }

  // 已登录访问登录/注册页 → 跳仪表盘
  if (to.meta.requiresGuest && isAuthenticated) {
    return { name: "dashboard" };
  }
});

export default router;
