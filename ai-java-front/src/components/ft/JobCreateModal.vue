<script setup lang="ts">
import { ref } from "vue";
import { NInputNumber, NModal, useMessage } from "naive-ui";
import { createJobApi } from "@/api/fineTune";

const props = defineProps<{ show: boolean; datasetId: number | null }>();
const emit = defineEmits<{
  (e: "update:show", v: boolean): void;
  (e: "created"): void;
}>();
const message = useMessage();

const submitting = ref(false);
/** 参与蒸馏的抽样条数：null=使用全部样本 */
const sampleLimit = ref<number | null>(null);

async function handleCreate() {
  if (!props.datasetId) {
    message.warning("请先选择数据集");
    return false;
  }
  submitting.value = true;
  try {
    await createJobApi({
      datasetId: props.datasetId,
      sampleLimit: sampleLimit.value ?? undefined,
    });
    message.success("风格生成任务已提交");
    sampleLimit.value = null;
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
      title="从数据集生成风格"
      positive-text="开始生成"
      negative-text="取消"
      :loading="submitting"
      :on-positive-click="handleCreate"
      @update:show="(v: boolean) => emit('update:show', v)"
  >
    <p class="desc">
      将对数据集中的问答对进行风格蒸馏，生成回答风格提示词并绑定到当前知识库。
    </p>
    <p class="label">抽样条数（可选，留空使用全部样本）</p>
    <NInputNumber
        v-model:value="sampleLimit"
        :min="1"
        :max="500"
        clearable
        placeholder="默认使用全部样本"
        style="width: 100%"
    />
  </NModal>
</template>

<style scoped>
.desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}
.label {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
</style>
