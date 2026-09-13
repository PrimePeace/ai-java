<script setup lang="ts">
import { NCard, NIcon } from "naive-ui";
import type { Component } from "vue";

interface Props {
  title: string;
  value: number | string;
  icon: Component;
  /** 图标底色风格 */
  tone?: "indigo" | "violet" | "cyan";
}

withDefaults(defineProps<Props>(), { tone: "indigo" });
</script>

<template>
  <NCard class="stat-card" :bordered="false">
    <div class="stat-body">
      <span class="stat-icon" :class="`stat-icon--${tone}`">
        <NIcon :size="22"><component :is="icon" /></NIcon>
      </span>
      <div class="stat-text">
        <p class="stat-title">{{ title }}</p>
        <p class="stat-value">{{ value }}</p>
      </div>
    </div>
  </NCard>
</template>

<style scoped>
.stat-card {
  border-radius: 12px;
}

.stat-body {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #ffffff;
}

.stat-icon--indigo {
  background: linear-gradient(135deg, #6366f1, #818cf8);
}

.stat-icon--violet {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}

.stat-icon--cyan {
  background: linear-gradient(135deg, #06b6d4, #22d3ee);
}

.stat-title {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.stat-value {
  margin: 4px 0 0;
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1;
}
</style>
