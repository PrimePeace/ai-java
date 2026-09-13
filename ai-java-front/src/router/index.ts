import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import { aiRoutes } from "./ai";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/auth/LoginView.vue"),
      meta: { requiresGuest: true, title: "登录" },
    },
    {
      path: "/register",
      name: "register",
      component: () => import("@/views/auth/RegisterView.vue"),
      meta: { requiresGuest: true, title: "注册" },
    },
    {
      // 受保护页面统一挂载主布局（侧边栏 + 顶栏）
      path: "/",
      component: () => import("@/layouts/MainLayout.vue"),
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          name: "dashboard",
          component: () => import("@/views/DashboardView.vue"),
          meta: { requiresAuth: true, title: "仪表盘", menu: "dashboard" },
        },
        ...aiRoutes,
      ],
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

router.afterEach((to) => {
  const page = (to.meta.title as string) || "";
  document.title = page ? `${page} · AI 知识库` : "AI 知识库";
});

export default router;
