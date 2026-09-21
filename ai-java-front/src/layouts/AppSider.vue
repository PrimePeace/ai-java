<script setup lang="ts">
import { computed, h, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NLayoutSider, NMenu, NIcon } from "naive-ui";
import type { MenuOption } from "naive-ui";
import type { Component } from "vue";
import {
  ChatbubblesOutline,
  ConstructOutline,
  DocumentTextOutline,
  LibraryOutline,
  SparklesOutline,
  SpeedometerOutline,
} from "@vicons/ionicons5";

const route = useRoute();
const router = useRouter();
const collapsed = ref(false);

function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) });
}

const menuOptions: MenuOption[] = [
  { label: "仪表盘", key: "dashboard", icon: renderIcon(SpeedometerOutline) },
  { label: "知识库", key: "kb", icon: renderIcon(LibraryOutline) },
  { label: "提示词模板", key: "prompt", icon: renderIcon(DocumentTextOutline) },
  { label: "模型微调", key: "fine-tune", icon: renderIcon(ConstructOutline) },
  { label: "智能问答", key: "chat", icon: renderIcon(ChatbubblesOutline) },
];

const menuRoutes: Record<string, string> = {
  dashboard: "/dashboard",
  kb: "/kb",
  prompt: "/prompt",
  "fine-tune": "/fine-tune",
  chat: "/chat",
};

const activeMenu = computed(() => (route.meta.menu as string) || "dashboard");

function handleMenuSelect(key: string) {
  const target = menuRoutes[key];
  if (target) {
    router.push(target);
  }
}
</script>

<template>
  <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed="collapsed"
      :collapsed-width="64"
      :width="220"
      :native-scrollbar="false"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
  >
    <div class="sider-logo">
      <span class="logo-badge">
        <NIcon :size="18"><SparklesOutline /></NIcon>
      </span>
      <span v-show="!collapsed" class="logo-text">AI 知识库</span>
    </div>
    <NMenu
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="20"
        :options="menuOptions"
        :value="activeMenu"
        @update:value="handleMenuSelect"
    />
  </NLayoutSider>
</template>

<style scoped>

/* 定义在当前组件的作用域内 */
.sider-logo {
  /* 或者直接定义在父级 */
}
/* 也可以在组件的根元素上定义，如果这个变量只在当前组件用的话 */
:root {
  --n-border-color: #eff5f5;
}

.sider-logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
}

.logo-badge {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--brand-gradient);
  color: #ffffff;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
}
</style>
