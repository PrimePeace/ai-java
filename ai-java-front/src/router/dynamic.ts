import type { RouteRecordRaw, Router } from "vue-router";
import type { MenuNode } from "@/types/menu";

/** 组件键白名单。后端只下发键，不按接口字符串加载任意文件。 */
const viewMap: Record<string, () => Promise<unknown>> = {
  "dashboard/DashboardView": () => import("@/views/DashboardView.vue"),
  "kb/KnowledgeBaseView": () => import("@/views/ai/KnowledgeBaseView.vue"),
  "kb/KnowledgeBaseDetailView": () => import("@/views/ai/KnowledgeBaseDetailView.vue"),
  "prompt/PromptTemplateView": () => import("@/views/ai/PromptTemplateView.vue"),
  "ft/FineTuneView": () => import("@/views/ai/FineTuneView.vue"),
  "chat/ChatView": () => import("@/views/ai/ChatView.vue"),
  "system/UserManageView": () => import("@/views/system/UserManageView.vue"),
  "system/RoleManageView": () => import("@/views/system/RoleManageView.vue"),
  "system/MenuManageView": () => import("@/views/system/MenuManageView.vue"),
  "system/AuditLogView": () => import("@/views/system/AuditLogView.vue"),
};

const addedNames: string[] = [];

export function resetDynamicRoutes(router: Router) {
  for (const name of addedNames) {
    if (router.hasRoute(name)) {
      router.removeRoute(name);
    }
  }
  addedNames.length = 0;
}

export function installDynamicRoutes(router: Router, nodes: MenuNode[]) {
  resetDynamicRoutes(router);
  for (const menu of flattenMenus(nodes)) {
    const component = menu.component ? viewMap[menu.component] : undefined;
    if (!menu.path || !component) {
      if (menu.component) {
        console.warn(`未登记的组件键: ${menu.component}`);
      }
      continue;
    }
    const name = `menu-${menu.id}`;
    const route: RouteRecordRaw = {
      path: menu.path.replace(/^\//, ""),
      name,
      component,
      meta: {
        requiresAuth: true,
        title: menu.name,
        menu: String(menu.id),
        fullscreen: menu.component === "chat/ChatView",
      },
    };
    router.addRoute("layout", route);
    addedNames.push(name);
  }
}

export function firstVisiblePath(nodes: MenuNode[]): string | null {
  const sorted = [...nodes].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  for (const node of sorted) {
    if (node.children?.length) {
      const child = firstVisiblePath(node.children);
      if (child) {
        return child;
      }
    }
    if (
      node.menuType === "MENU" &&
      node.visible === 1 &&
      node.path &&
      !node.path.includes(":")
    ) {
      return node.path;
    }
  }
  return null;
}

function flattenMenus(nodes: MenuNode[], acc: MenuNode[] = []): MenuNode[] {
  for (const node of nodes) {
    if (node.menuType === "MENU") {
      acc.push(node);
    }
    if (node.children?.length) {
      flattenMenus(node.children, acc);
    }
  }
  return acc;
}
