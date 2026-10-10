<script setup lang="ts">
import { computed, h } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NDropdown,
  NAvatar,
  NIcon,
  useDialog,
  useMessage,
} from "naive-ui";
import type { MenuOption } from "naive-ui";
import { LogOutOutline } from "@vicons/ionicons5";
import type { Component } from "vue";
import AppSider from "./AppSider.vue";
import { useUserStore } from "@/stores/user";
import { BRAND_GRADIENT } from "@/styles/theme";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const message = useMessage();
const dialog = useDialog();

const pageTitle = computed(() => (route.meta.title as string) || "AI 知识库");
// 智能问答等页面需要占满内容区，去掉内边距
const isFullscreen = computed(() => !!route.meta.fullscreen);
const displayName = computed(
  () => userStore.user?.nickname || userStore.user?.username || "用户",
);
const initial = computed(() =>
  (userStore.user?.username || "U").charAt(0).toUpperCase(),
);

function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) });
}

const userOptions: MenuOption[] = [
  { label: "退出登录", key: "logout", icon: renderIcon(LogOutOutline) },
];

function handleUserSelect(key: string) {
  if (key !== "logout") return;
  dialog.warning({
    title: "确认退出",
    content: "退出后需要重新登录，是否继续？",
    positiveText: "确认退出",
    negativeText: "取消",
    onPositiveClick: async () => {
      await userStore.logout();
      message.success("已退出登录");
      router.push("/login");
    },
  });
}
</script>

<template>
  <NLayout position="absolute">
    <NLayout has-sider class="main-body">
      <AppSider />
      <NLayout class="main-right">
        <NLayoutHeader bordered class="main-header">
          <span class="page-title">{{ pageTitle }}</span>
          <NDropdown :options="userOptions" @select="handleUserSelect">
            <div class="user-chip">
              <NAvatar
                round
                :size="30"
                :style="{ backgroundImage: BRAND_GRADIENT }"
                class="user-avatar"
              >
                {{ initial }}
              </NAvatar>
              <span class="user-name">{{ displayName }}</span>
            </div>
          </NDropdown>
        </NLayoutHeader>
        <NLayoutContent
          :native-scrollbar="false"
          content-style="display: flex; flex-direction: column; min-height: 100%"
        >
          <div class="page-wrap" :class="{ 'page-wrap--flush': isFullscreen }">
            <RouterView />
          </div>
        </NLayoutContent>
      </NLayout>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.main-body {
  height: 100vh;
}

.main-right {
  background: transparent;
}

.main-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #ffffff;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-chip:hover {
  background-color: #f1f2f8;
}

.user-avatar {
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  color: #334155;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-wrap {
  flex: 1;
  width: 100%;
  padding: 24px;
  box-sizing: border-box;
}

.page-wrap--flush {
  padding: 0;
}
</style>
