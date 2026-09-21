<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import {
  NButton,
  NCard,
  NProgress,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
} from "naive-ui";
import {
  DatasetStatus,
  JobStatus,
  JOB_STATUS_LABEL,
  type FineTuneDataset,
  type FineTuneJob,
} from "@/types/fineTune";
import { deleteJobApi, listDatasetsApi, listJobsApi } from "@/api/fineTune";
import { listKbsApi, updateKbApi } from "@/api/kb";
import JobCreateModal from "@/components/ft/JobCreateModal.vue";

const props = defineProps<{ kbId: number }>();
const emit = defineEmits<{ (e: "style-cleared"): void }>();
const message = useMessage();
const dialog = useDialog();

const datasets = ref<FineTuneDataset[]>([]);
const selectedDatasetId = ref<number | null>(null);
const jobs = ref<FineTuneJob[]>([]);
const showCreate = ref(false);

const readyDatasets = computed(() =>
  datasets.value.filter((d) => d.status === DatasetStatus.READY),
);
const datasetOptions = computed(() =>
  readyDatasets.value.map((d) => ({ label: `${d.name}（${d.sampleCount} 样本）`, value: d.id })),
);
const hasRunning = computed(() =>
  jobs.value.some((j) => j.status === JobStatus.SUBMITTING || j.status === JobStatus.TRAINING),
);

function statusType(s: JobStatus) {
  if (s === JobStatus.SUCCEEDED) return "success";
  if (s === JobStatus.FAILED) return "error";
  if (s === JobStatus.CANCELLED) return "default";
  return "warning";
}

/** 风格摘要（列表展示用，截断 50 字） */
function truncate(sp: string) {
  return sp.length > 50 ? sp.slice(0, 50) + "…" : sp;
}

async function loadDatasets() {
  try {
    const res = await listDatasetsApi(props.kbId);
    datasets.value = res.data;
    if (!selectedDatasetId.value && readyDatasets.value.length > 0) {
      selectedDatasetId.value = readyDatasets.value[0]?.id ?? null;
    }
  } catch (e: any) {
    message.error(e.message || "数据集加载失败");
  }
}

async function loadJobs() {
  if (!selectedDatasetId.value) {
    jobs.value = [];
    return;
  }
  try {
    const res = await listJobsApi(selectedDatasetId.value);
    jobs.value = res.data;
  } catch (e: any) {
    message.error(e.message || "任务加载失败");
  }
}

function handlePreview(j: FineTuneJob) {
  dialog.info({
    title: "回答风格提示词",
    content: () => j.stylePrompt ?? "",
    positiveText: "关闭",
  });
}

/** 清除风格：解绑知识库上的 stylePrompt，引擎回退纯 RAG */
function handleClearStyle(j: FineTuneJob) {
  dialog.warning({
    title: "清除回答风格",
    content: "清除后知识库将回退为纯 RAG 回答，确定清除吗？",
    positiveText: "确认清除",
    negativeText: "再想想",
    onPositiveClick: async () => {
      const res = await listKbsApi();
      const kb = res.data.find((k) => k.id === props.kbId);
      if (!kb) throw new Error("知识库不存在");
      await updateKbApi({
        id: kb.id,
        name: kb.name,
        description: kb.description,
        stylePrompt: "",
        chatEngine: "rag",
      });
      message.success("已清除回答风格");
      emit("style-cleared");
    },
  });
}

async function handleDelete(j: FineTuneJob) {
  await deleteJobApi(j.id);
  message.success("已删除");
  await loadJobs();
}

// 单套轮询：存在进行中任务时每 10s 刷新
let timer: ReturnType<typeof setInterval> | null = null;
watch(hasRunning, (v) => {
  if (v && !timer) {
    timer = setInterval(loadJobs, 10000);
  } else if (!v && timer) {
    clearInterval(timer);
    timer = null;
  }
});
watch(() => props.kbId, loadDatasets, { immediate: true });
watch(selectedDatasetId, loadJobs);
onUnmounted(() => { if (timer) clearInterval(timer); });
</script>

<template>
  <div>
    <div class="panel-bar">
      <NSelect v-model:value="selectedDatasetId" :options="datasetOptions"
          placeholder="选择已就绪的数据集" style="width: 280px" />
      <NButton type="primary" size="small" :disabled="!selectedDatasetId"
          @click="showCreate = true">生成回答风格</NButton>
    </div>
    <p v-if="jobs.length === 0" class="empty">暂无风格任务</p>
    <NSpace vertical size="small">
      <NCard v-for="j in jobs" :key="j.id" size="small" hoverable>
        <div class="row">
          <div class="info">
            <NSpace align="center" size="small">
              <b>风格任务 #{{ j.id }}</b>
              <NTag size="small" :type="statusType(j.status)">{{ JOB_STATUS_LABEL[j.status] }}</NTag>
            </NSpace>
            <NProgress v-if="j.status === JobStatus.SUBMITTING || j.status === JobStatus.TRAINING"
                type="line" :percentage="j.progress" style="margin-top: 6px" />
            <p v-if="j.stylePrompt" class="meta">风格：{{ truncate(j.stylePrompt) }}</p>
            <p v-if="j.errorMessage" class="error">{{ j.errorMessage }}</p>
          </div>
          <NSpace size="small">
            <NButton v-if="j.stylePrompt" size="tiny" tertiary @click="handlePreview(j)">预览风格</NButton>
            <NButton v-if="j.stylePrompt" size="tiny" tertiary type="warning" @click="handleClearStyle(j)">清除风格</NButton>
            <NButton size="tiny" tertiary type="error" @click="handleDelete(j)">删除</NButton>
          </NSpace>
        </div>
      </NCard>
    </NSpace>
    <JobCreateModal v-model:show="showCreate" :dataset-id="selectedDatasetId" @created="loadJobs" />
  </div>
</template>

<style scoped>
.panel-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.info {
  flex: 1;
  min-width: 0;
}
.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
  word-break: break-all;
}
.error {
  margin: 4px 0 0;
  font-size: 12px;
  color: #d03050;
}
.empty {
  text-align: center;
  color: #999;
  padding: 30px 0;
}
</style>
