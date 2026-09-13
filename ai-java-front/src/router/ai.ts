import type { RouteRecordRaw } from "vue-router";

/** AI 模块路由（挂载在主布局 "/" 的 children 下，使用相对路径） */
export const aiRoutes: RouteRecordRaw[] = [
  {
    path: "kb",
    name: "knowledge-base",
    component: () => import("@/views/ai/KnowledgeBaseView.vue"),
    meta: { requiresAuth: true, title: "知识库", menu: "kb" },
  },
  {
    path: "kb/:id",
    name: "knowledge-base-detail",
    component: () => import("@/views/ai/KnowledgeBaseDetailView.vue"),
    meta: { requiresAuth: true, title: "文档管理", menu: "kb" },
  },
  {
    path: "prompt",
    name: "prompt-template",
    component: () => import("@/views/ai/PromptTemplateView.vue"),
    meta: { requiresAuth: true, title: "提示词模板", menu: "prompt" },
  },
  {
    path: "chat",
    name: "chat",
    component: () => import("@/views/ai/ChatView.vue"),
    // fullscreen：占满内容区，由页面自身管理布局
    meta: {
      requiresAuth: true,
      title: "智能问答",
      menu: "chat",
      fullscreen: true,
    },
  },
];
