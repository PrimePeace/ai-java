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

onMounted(async () => {
  try {
    const res = await listKbsApi();
    kbs.value = res.data;
    if (kbs.value.length > 0) {
      selectedKbId.value = kbs.value[0].id;
    }
  } catch (e: any) {
    message.error(e.message || "知识库加载失败");
  }
});
</script>

<template>
  <div class="ft-page">
    <NCard title="模型微调" class="ft-card">
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
        <p v-if="selectedKb?.ftModelId" class="ft-bind">
          已绑定微调模型：{{ selectedKb.ftModelId }}（引擎：{{ selectedKb.chatEngine }}）
        </p>
        <NTabs v-model:value="activeTab" type="line" animated>
          <NTabPane name="dataset" tab="数据集">
            <DatasetPanel :kb-id="selectedKbId" />
          </NTabPane>
          <NTabPane name="job" tab="微调任务">
            <JobPanel :kb-id="selectedKbId" />
          </NTabPane>
          <NTabPane name="evaluation" tab="模型评测">
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
