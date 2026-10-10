import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import { useMenuStore } from "@/stores/menu";

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
      path: "/403",
      name: "forbidden",
      component: () => import("@/views/error/ForbiddenView.vue"),
      meta: { requiresAuth: true, title: "无权限" },
    },
    {
      path: "/",
      name: "layout",
      component: () => import("@/layouts/MainLayout.vue"),
      meta: { requiresAuth: true },
      children: [],
    },
    {
      path: "/:pathMatch(.*)*",
      name: "not-found",
      component: () => import("@/views/error/ForbiddenView.vue"),
      meta: { requiresAuth: true, title: "无权限" },
    },
  ],
});

router.beforeEach(async (to) => {
  const userStore = useUserStore();
  const menuStore = useMenuStore();
  const authed = !!userStore.accessToken;

  if (to.meta.requiresAuth && !authed) {
    return { name: "login", query: { redirect: to.fullPath } };
  }

  if (to.meta.requiresGuest && authed) {
    if (!menuStore.loaded) {
      await menuStore.load();
    }
    return menuStore.homePath() || { name: "forbidden" };
  }

  if (authed && to.name !== "login" && to.name !== "register" && !menuStore.loaded) {
    await menuStore.load();
    return to.fullPath;
  }

  if (authed && (to.path === "/" || to.name === "layout")) {
    return menuStore.homePath() || { name: "forbidden" };
  }
});

router.afterEach((to) => {
  const page = (to.meta.title as string) || "";
  document.title = page ? `${page} · AI 知识库` : "AI 知识库";
});

export default router;
