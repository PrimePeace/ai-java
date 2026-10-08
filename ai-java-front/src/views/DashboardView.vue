<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { NButton, NCard, NIcon, NSpin, useMessage } from "naive-ui";
import {
  ChatbubblesOutline,
  DocumentTextOutline,
  LibraryOutline,
} from "@vicons/ionicons5";
import StatCard from "@/components/dashboard/StatCard.vue";
import KbDocChart from "@/components/dashboard/KbDocChart.vue";
import RecentSessionPanel from "@/components/dashboard/RecentSessionPanel.vue";
import TokenUsagePanel from "@/components/dashboard/TokenUsagePanel.vue";
import { listKbsApi } from "@/api/kb";
import { getTokenUsageSummaryApi, listSessionsApi } from "@/api/chat";
import type {
  ChatSession,
  KnowledgeBase,
  TokenUsageSummary,
} from "@/types/ai";
import { useUserStore } from "@/stores/user";
import { BRAND_GRADIENT } from "@/styles/theme";

const router = useRouter();
const message = useMessage();
const userStore = useUserStore();

const kbs = ref<KnowledgeBase[]>([]);
const sessions = ref<ChatSession[]>([]);
/** Token 消耗汇总；null 表示加载中（面板显示占位符） */
const tokenUsage = ref<TokenUsageSummary | null>(null);
const loading = ref(true);

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return "夜深了";
  if (hour < 12) return "早上好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

const displayName = computed(
  () => userStore.user?.nickname || userStore.user?.username || "用户",
);

const totalDocs = computed(() =>
  kbs.value.reduce((sum, kb) => sum + (kb.docCount ?? 0), 0),
);

const chartData = computed(() =>
  kbs.value.map((kb) => ({ name: kb.name, value: kb.docCount ?? 0 })),
);

onMounted(async () => {
  try {
    const [kbRes, sessionRes, usageRes] = await Promise.all([
      listKbsApi(),
      listSessionsApi(),
      // Token 接口失败不阻塞其他数据，回落为全 0
      getTokenUsageSummaryApi().catch(() => null),
    ]);
    kbs.value = kbRes.data ?? [];
    sessions.value = sessionRes.data ?? [];
    tokenUsage.value = usageRes?.data ?? {
      promptTokens: 0,
      completionTokens: 0,
      totalTokens: 0,
    };
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : "数据加载失败";
    message.error(msg);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="dashboard">
    <NSpin :show="loading">
      <!-- 欢迎横幅 -->
      <div class="hero" :style="{ backgroundImage: BRAND_GRADIENT }">
        <div class="hero-text">
          <h2>{{ greeting }}，{{ displayName }}</h2>
          <p>管理你的知识库，或直接开始一场智能问答</p>
        </div>
        <div class="hero-actions">
          <NButton ghost color="#fff" @click="router.push('/kb')">
            <template #icon>
              <NIcon><LibraryOutline /></NIcon>
            </template>
            管理知识库
          </NButton>
          <NButton
            color="#fff"
            text-color="#4f46e5"
            @click="router.push('/chat')"
          >
            开始问答
          </NButton>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="stat-grid">
        <StatCard
          title="知识库"
          :value="kbs.length"
          :icon="LibraryOutline"
          tone="indigo"
        />
        <StatCard
          title="文档总数"
          :value="totalDocs"
          :icon="DocumentTextOutline"
          tone="violet"
        />
        <StatCard
          title="问答会话"
          :value="sessions.length"
          :icon="ChatbubblesOutline"
          tone="cyan"
        />
      </div>

      <!-- Token 消耗面板 -->
      <div class="usage-panel">
        <TokenUsagePanel :usage="tokenUsage" />
      </div>

      <!-- 图表 + 最近会话 -->
      <div class="panel-grid">
        <NCard title="知识库文档分布" :bordered="false" class="panel-card">
          <KbDocChart :data="chartData" />
        </NCard>
        <NCard title="最近会话" :bordered="false" class="panel-card">
          <RecentSessionPanel :sessions="sessions" />
        </NCard>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.hero {
  border-radius: 16px;
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  color: #ffffff;
}

.hero-text h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.hero-text p {
  margin: 8px 0 0;
  font-size: 13px;
  opacity: 0.85;
}

.hero-actions {
  display: flex;
  gap: 12px;
}

.stat-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.panel-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 20px;
}

.usage-panel {
  margin-top: 20px;
}

.panel-card {
  border-radius: 12px;
}

@media (max-width: 900px) {
  .stat-grid,
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
