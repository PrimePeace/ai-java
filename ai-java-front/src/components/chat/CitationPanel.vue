<script setup lang="ts">
import type { Citation } from "@/types/ai";
import { NCard, NCollapse, NCollapseItem } from "naive-ui";

interface Props {
  citations: Citation[];
}

defineProps<Props>();
</script>

<template>
  <div class="citation-panel">
    <NCollapse>
      <NCollapseItem
        v-for="cite in citations"
        :key="cite.chunkId"
        :title="`[引用 ${cite.chunkIndex + 1}] ${cite.docName}`"
      >
        <p><strong>得分：</strong>{{ cite.score?.toFixed(4) }}</p>
        <pre class="snippet">{{ cite.content }}</pre>
      </NCollapseItem>
    </NCollapse>
    <p v-if="citations.length === 0" class="empty">无引用</p>
  </div>
</template>

<style scoped>
.citation-panel {
  padding: 8px;
  overflow-y: auto;
}
.snippet {
  white-space: pre-wrap;
  background: #f7f7f7;
  padding: 8px;
  border-radius: 4px;
  font-size: 13px;
}
.empty {
  text-align: center;
  color: #999;
  padding: 20px 0;
}
</style>
