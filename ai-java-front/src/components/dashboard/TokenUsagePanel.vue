<script setup lang="ts">
import { computed } from "vue";
import { NCard } from "naive-ui";
import type { TokenUsageSummary } from "@/types/ai";

interface Props {
  /** Token 消耗数据；null 表示加载中 */
  usage: TokenUsageSummary | null;
}

const props = defineProps<Props>();

/** 千分位格式化；加载中显示占位符 */
function format(value: number | undefined): string {
  if (value === undefined) return "-";
  return value.toLocaleString("zh-CN");
}

const items = computed(() => [
  { label: "输入 Tokens", value: props.usage?.promptTokens, tone: "indigo" },
  { label: "输出 Tokens", value: props.usage?.completionTokens, tone: "violet" },
  { label: "合计 Tokens", value: props.usage?.totalTokens, tone: "cyan" },
]);
</script>

<template>
  <NCard title="Token 消耗" :bordered="false" class="usage-card">
    <div class="usage-grid">
      <div v-for="item in items" :key="item.label" class="usage-item">
        <span class="usage-bar" :class="`usage-bar--${item.tone}`" />
        <div class="usage-text">
          <p class="usage-label">{{ item.label }}</p>
          <p class="usage-value" :class="{ 'usage-value--pending': item.value === undefined }">
            {{ format(item.value) }}
          </p>
        </div>
      </div>
    </div>
  </NCard>
</template>

<style scoped>
.usage-card {
  border-radius: 12px;
}

.usage-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.usage-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.usage-bar {
  width: 6px;
  height: 40px;
  border-radius: 3px;
  flex-shrink: 0;
}

.usage-bar--indigo {
  background: linear-gradient(180deg, #6366f1, #818cf8);
}

.usage-bar--violet {
  background: linear-gradient(180deg, #8b5cf6, #a78bfa);
}

.usage-bar--cyan {
  background: linear-gradient(180deg, #06b6d4, #22d3ee);
}

.usage-label {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.usage-value {
  margin: 4px 0 0;
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1;
}

.usage-value--pending {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .usage-grid {
    grid-template-columns: 1fr;
  }
}
</style>
