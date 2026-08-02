<script setup lang="ts">
import { useUserStore } from "@/stores/user";
import { useRouter } from "vue-router";
import {
  NCard,
  NButton,
  NSpace,
  NAvatar,
  NTag,
  NDescriptions,
  NDescriptionsItem,
  useMessage,
  useDialog,
} from "naive-ui";

const userStore = useUserStore();
const router = useRouter();
const message = useMessage();
const dialog = useDialog();

function handleLogout() {
  dialog.warning({
    title: "确认退出",
    content: "退出后需要重新登录，是否继续？",
    positiveText: "确认退出",
    negativeText: "取消",
    onPositiveClick: () => {
      userStore.logout();
      message.success("已退出登录");
      router.push("/login");
    },
  });
}
</script>

<template>
  <div class="dashboard">
    <NCard title="仪表盘" class="dashboard-card">
      <NSpace vertical size="large">
        <div class="user-header">
          <NAvatar :size="64" round :style="{ backgroundColor: '#18a058' }">
            {{ userStore.user?.username?.charAt(0)?.toUpperCase() }}
          </NAvatar>
          <NSpace vertical>
            <h2>{{ userStore.user?.nickname || userStore.user?.username }}</h2>
            <NTag :type="userStore.user?.status === 1 ? 'success' : 'warning'">
              {{ userStore.user?.status === 1 ? "正常" : "未知" }}
            </NTag>
          </NSpace>
        </div>

        <NDescriptions bordered :column="1" size="small">
          <NDescriptionsItem label="用户名">
            {{ userStore.user?.username }}
          </NDescriptionsItem>
          <NDescriptionsItem label="昵称">
            {{ userStore.user?.nickname || "-" }}
          </NDescriptionsItem>
          <NDescriptionsItem label="邮箱">
            {{ userStore.user?.email || "-" }}
          </NDescriptionsItem>
          <NDescriptionsItem label="手机号">
            {{ userStore.user?.phone || "-" }}
          </NDescriptionsItem>
          <NDescriptionsItem label="注册时间">
            {{ userStore.user?.createTime }}
          </NDescriptionsItem>
        </NDescriptions>

        <NButton type="error" @click="handleLogout"> 退出登录 </NButton>
      </NSpace>
    </NCard>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 100vh;
  background-color: #f5f5f5;
  padding: 40px 16px;
}

.dashboard-card {
  width: 500px;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
</style>
