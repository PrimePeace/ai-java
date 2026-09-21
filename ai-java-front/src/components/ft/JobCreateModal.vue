<script setup lang="ts">
import { ref } from "vue";
import {
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  useMessage,
} from "naive-ui";
import { BASE_MODEL_OPTIONS, DEFAULT_BASE_MODEL } from "@/types/fineTune";
import { createJobApi } from "@/api/fineTune";

const props = defineProps<{ show: boolean; datasetId: number | null }>();
const emit = defineEmits<{
  (e: "update:show", v: boolean): void;
  (e: "created"): void;
}>();
const message = useMessage();

const submitting = ref(false);
const form = ref({
  baseModel: DEFAULT_BASE_MODEL,
  modelName: "",
  epochs: 3,
  batchSize: 4,
  learningRateMultiplier: 1.0,
});

async function handleCreate() {
  const suffix = form.value.modelName.trim();
  if (!props.datasetId || !suffix) {
    message.warning("请选择数据集并填写模型后缀");
    return false;
  }
  if (suffix.length > 8 || !/^[a-zA-Z0-9_-]+$/.test(suffix)) {
    message.warning("模型后缀须为 1-8 位字母/数字/下划线/连字符");
    return false;
  }
  submitting.value = true;
  try {
    await createJobApi({ datasetId: props.datasetId, ...form.value });
    message.success("任务已提交，正在上传数据集");
    form.value.modelName = "";
    emit("update:show", false);
    emit("created");
  } catch (e: any) {
    message.error(e.message || "创建失败");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <NModal
      :show="show"
      preset="dialog"
      title="创建微调任务"
      positive-text="提交训练"
      negative-text="取消"
      :loading="submitting"
      :on-positive-click="handleCreate"
      @update:show="(v: boolean) => emit('update:show', v)"
  >
    <p class="label">基座模型</p>
    <NSelect v-model:value="form.baseModel" :options="BASE_MODEL_OPTIONS" />
    <p class="label">模型后缀（智谱约束：1-8 位，字母/数字/_/-）</p>
    <NInput
        v-model:value="form.modelName"
        maxlength="8"
        show-count
        placeholder="如 domainv1"
    />
    <p class="label">训练轮数 / 批大小 / 学习率倍数</p>
    <NSpace>
      <NInputNumber v-model:value="form.epochs" :min="1" :max="10" placeholder="轮数" />
      <NInputNumber v-model:value="form.batchSize" :min="1" :max="64" placeholder="批大小" />
      <NInputNumber
          v-model:value="form.learningRateMultiplier"
          :min="0.01" :max="10" :step="0.1" placeholder="学习率倍数"
      />
    </NSpace>
  </NModal>
</template>

<style scoped>
.label {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
</style>
