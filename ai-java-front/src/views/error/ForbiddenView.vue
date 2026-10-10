<script setup lang="ts">
import { useRouter } from "vue-router";
import { NButton, NResult } from "naive-ui";
import { useMenuStore } from "@/stores/menu";

const router = useRouter();
const menuStore = useMenuStore();

function goHome() {
  router.push(menuStore.homePath() || "/login");
}
</script>

<template>
  <div class="forbidden">
    <NResult status="403" title="没有访问权限" description="当前账号不能打开这个页面">
      <template #footer>
        <NButton type="primary" @click="goHome">返回首页</NButton>
      </template>
    </NResult>
  </div>
</template>

<style scoped>
.forbidden {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
