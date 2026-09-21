<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { NCard, NSelect, NSpace, NTabs, NTabPane, useMessage } from "naive-ui";
import type { KnowledgeBase } from "@/types/ai";
import { listKbsApi } from "@/api/kb";
import DatasetPanel from "@/components/ft/DatasetPanel.vue";
import JobPanel from "@/components/ft/JobPanel.vue";
import EvaluationPanel from "@/components/ft/EvaluationPanel.vue";

const message = useMessage();
const kbs = ref<KnowledgeBase[]>([]);
const selectedKbId = ref<number | null>(null);
const activeTab = ref("dataset");

const kbOptions = computed(() =>
  kbs.value.map((kb) => ({ label: kb.name, value: kb.id })),
);

const selectedKb = computed(
  () => kbs.value.find((kb) => kb.id === selectedKbId.value) ?? null,
);

const ENGINE_LABEL: Record<string, string> = {
  rag: "纯 RAG",
  style: "RAG+风格",
  auto: "自动",
};

/** 回答风格摘要（绑定信息展示用，截断 60 字） */
const styleSummary = computed(() => {
  const sp = selectedKb.value?.stylePrompt;
  if (!sp) return "";
  return sp.length > 60 ? sp.slice(0, 60) + "…" : sp;
});

const engineLabel = computed(
  () => ENGINE_LABEL[selectedKb.value?.chatEngine ?? "rag"] ?? "纯 RAG",
);

async function loadKbs() {
  try {
    const res = await listKbsApi();
    kbs.value = res.data;
    if (!selectedKbId.value && kbs.value.length > 0) {
      selectedKbId.value = kbs.value[0]?.id ?? null;
    }
  } catch (e: any) {
    message.error(e.message || "知识库加载失败");
  }
}

onMounted(loadKbs);
</script>

<template>
  <div class="ft-page">
    <NCard title="回答风格" class="ft-card">
      <template #header-extra>
        <NSpace align="center">
          <span class="kb-label">知识库</span>
          <NSelect
              v-model:value="selectedKbId"
              :options="kbOptions"
              placeholder="选择知识库"
              style="width: 220px"
          />
        </NSpace>
      </template>
      <p v-if="!selectedKbId" class="empty">请先创建知识库并上传文档</p>
      <template v-else>
        <p v-if="selectedKb?.stylePrompt" class="ft-bind">
          已生成回答风格：{{ styleSummary }}（引擎：{{ engineLabel }}）
        </p>
        <NTabs v-model:value="activeTab" type="line" animated>
          <NTabPane name="dataset" tab="数据集">
            <DatasetPanel :kb-id="selectedKbId" />
          </NTabPane>
          <NTabPane name="job" tab="风格任务">
            <JobPanel :kb-id="selectedKbId" @style-cleared="loadKbs" />
          </NTabPane>
          <NTabPane name="evaluation" tab="风格评测">
            <EvaluationPanel :kb-id="selectedKbId" />
          </NTabPane>
        </NTabs>
      </template>
    </NCard>
  </div>
</template>

<style scoped>
.ft-page {
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
}
.ft-card {
  min-height: 500px;
}
.kb-label {
  font-size: 13px;
  color: #64748b;
}
.ft-bind {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
}
.empty {
  text-align: center;
  color: #999;
  padding: 60px 0;
}
</style>
