<script setup lang="ts">
import { computed, h, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NLayoutSider, NMenu, NIcon } from "naive-ui";
import type { MenuOption } from "naive-ui";
import type { Component } from "vue";
import {
  ChatbubblesOutline,
  DocumentTextOutline,
  GridOutline,
  LibraryOutline,
  ListOutline,
  MenuOutline,
  PeopleOutline,
  ShieldCheckmarkOutline,
  SparklesOutline,
} from "@vicons/ionicons5";
import { useMenuStore } from "@/stores/menu";
import type { MenuNode } from "@/types/menu";

const route = useRoute();
const router = useRouter();
const menuStore = useMenuStore();
const collapsed = ref(false);

const iconMap: Record<string, Component> = {
  grid: GridOutline,
  library: LibraryOutline,
  document: DocumentTextOutline,
  sparkles: SparklesOutline,
  chatbubbles: ChatbubblesOutline,
  people: PeopleOutline,
  shield: ShieldCheckmarkOutline,
  menu: MenuOutline,
  list: ListOutline,
};

function renderIcon(icon?: string | null) {
  const component = (icon && iconMap[icon]) || ListOutline;
  return () => h(NIcon, null, { default: () => h(component) });
}

function toOptions(nodes: MenuNode[]): MenuOption[] {
  return [...nodes]
    .filter((node) => node.visible === 1 && node.menuType !== "BUTTON")
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((node) => ({
      label: node.name,
      key: String(node.id),
      icon: renderIcon(node.icon),
      children: node.children?.length ? toOptions(node.children) : undefined,
    }));
}

const menuOptions = computed(() => toOptions(menuStore.tree));
const activeMenu = computed(() => (route.meta.menu as string) || "");

function handleMenuSelect(key: string) {
  const target = findPath(menuStore.tree, key);
  if (target) {
    router.push(target);
  }
}

function findPath(nodes: MenuNode[], id: string): string | null {
  for (const node of nodes) {
    if (String(node.id) === id && node.path && !node.path.includes(":")) {
      return node.path;
    }
    if (node.children?.length) {
      const child = findPath(node.children, id);
      if (child) {
        return child;
      }
    }
  }
  return null;
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
.sider-logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid #efeff5;
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
