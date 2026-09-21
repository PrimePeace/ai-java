<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import {
  NButton,
  NCard,
  NInput,
  NInputNumber,
  NModal,
  NSpace,
  NTag,
  useDialog,
  useMessage,
} from "naive-ui";
import {
  DatasetStatus,
  DATASET_STATUS_LABEL,
  type FineTuneDataset,
} from "@/types/fineTune";
import { deleteDatasetApi, generateDatasetApi, listDatasetsApi } from "@/api/fineTune";

const props = defineProps<{ kbId: number }>();
const message = useMessage();
const dialog = useDialog();

const datasets = ref<FineTuneDataset[]>([]);
const loading = ref(false);
const showGenerate = ref(false);
const generating = ref(false);
const form = ref({ name: "", description: "", qaPerChunk: 3 });

const hasGenerating = computed(() =>
  datasets.value.some((d) => d.status === DatasetStatus.GENERATING),
);

function statusType(s: DatasetStatus) {
  return s === DatasetStatus.READY ? "success" : s === DatasetStatus.FAILED ? "error" : "warning";
}

async function load() {
  loading.value = true;
  try {
    const res = await listDatasetsApi(props.kbId);
    datasets.value = res.data;
  } catch (e: any) {
    message.error(e.message || "数据集加载失败");
  } finally {
    loading.value = false;
  }
}

async function handleGenerate() {
  if (!form.value.name.trim()) {
    message.warning("数据集名称不能为空");
    return false;
  }
  generating.value = true;
  try {
    await generateDatasetApi({
      kbId: props.kbId,
      name: form.value.name,
      description: form.value.description,
      qaPerChunk: form.value.qaPerChunk,
    });
    message.success("已提交，正在异步生成");
    showGenerate.value = false;
    form.value = { name: "", description: "", qaPerChunk: 3 };
    await load();
  } catch (e: any) {
    message.error(e.message || "生成失败");
  } finally {
    generating.value = false;
  }
}

function handleDelete(d: FineTuneDataset) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除数据集「${d.name}」吗？JSONL 文件将一并删除。`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteDatasetApi(d.id);
        message.success("已删除");
        await load();
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}

// 单套轮询：存在 GENERATING 数据集时每 5s 刷新（规范：禁止双 polling 并存）
let timer: ReturnType<typeof setInterval> | null = null;
watch(hasGenerating, (v) => {
  if (v && !timer) {
    timer = setInterval(load, 5000);
  } else if (!v && timer) {
    clearInterval(timer);
    timer = null;
  }
});
watch(() => props.kbId, load, { immediate: true });
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div>
    <div class="panel-bar">
      <span class="hint">基于知识库切片生成 ChatML 格式训练集（每切片约 {{ form.qaPerChunk }} 个问答对）</span>
      <NButton type="primary" size="small" @click="showGenerate = true">生成训练集</NButton>
    </div>
    <p v-if="datasets.length === 0 && !loading" class="empty">暂无数据集</p>
    <NSpace vertical size="small">
      <NCard v-for="d in datasets" :key="d.id" size="small" hoverable>
        <div class="row">
          <div class="info">
            <NSpace align="center" size="small">
              <b>{{ d.name }}</b>
              <NTag size="small" :type="statusType(d.status)">
                {{ DATASET_STATUS_LABEL[d.status] }}
              </NTag>
            </NSpace>
            <p class="meta">
              {{ d.sampleCount }} 个样本 · {{ d.format }} · {{ d.createTime }}
            </p>
            <p v-if="d.errorMessage" class="error">{{ d.errorMessage }}</p>
          </div>
          <NButton size="tiny" tertiary type="error" @click="handleDelete(d)">删除</NButton>
        </div>
      </NCard>
    </NSpace>
    <NModal
        v-model:show="showGenerate"
        preset="dialog"
        title="生成训练集"
        positive-text="开始生成"
        negative-text="取消"
        :loading="generating"
        :on-positive-click="handleGenerate"
    >
      <NInput v-model:value="form.name" placeholder="数据集名称" />
      <NInput
          v-model:value="form.description"
          type="textarea"
          placeholder="描述（可选）"
          style="margin-top: 12px"
      />
      <p class="label">每切片问答对数</p>
      <NInputNumber v-model:value="form.qaPerChunk" :min="1" :max="10" style="width: 100%" />
    </NModal>
  </div>
</template>

<style scoped>
.panel-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.hint {
  font-size: 12px;
  color: #94a3b8;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
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
.label {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
</style>
