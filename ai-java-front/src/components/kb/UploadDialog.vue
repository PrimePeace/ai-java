<script setup lang="ts">
import { ref, watch } from "vue";
import { NModal, NButton, NSpace, useMessage } from "naive-ui";

import { uploadDocumentApi } from "@/api/kb";

interface Props {
  kbId: number;
  show: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "close"): void;
  (e: "uploaded"): void;
}>();

const message = useMessage();
const fileInputRef = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const dragOver = ref(false);
const uploading = ref(false);

const ALLOWED_TYPES = ["pdf", "docx", "md", "txt"];
const MAX_SIZE = 20 * 1024 * 1024;

watch(
  () => props.show,
  (visible) => {
    if (!visible) {
      selectedFile.value = null;
      dragOver.value = false;
      if (fileInputRef.value) fileInputRef.value.value = "";
    }
  },
);

function validateFile(file: File): boolean {
  const ext = file.name.split(".").pop()?.toLowerCase();
  if (!ext || !ALLOWED_TYPES.includes(ext)) {
    message.error(
      `不支持的文件类型：.${ext}，仅允许 ${ALLOWED_TYPES.join("/ ")}`,
    );
    return false;
  }
  if (file.size > MAX_SIZE) {
    message.error("文件超过 20MB 限制");
    return false;
  }
  return true;
}

function pickFile(file: File | undefined) {
  if (!file) return;
  if (!validateFile(file)) {
    selectedFile.value = null;
    if (fileInputRef.value) fileInputRef.value.value = "";
    return;
  }
  selectedFile.value = file;
}

function onInputChange(e: Event) {
  const input = e.target as HTMLInputElement;
  pickFile(input.files?.[0]);
}

function onDrop(e: DragEvent) {
  dragOver.value = false;
  pickFile(e.dataTransfer?.files?.[0]);
}

async function handleUpload() {
  const file = selectedFile.value;
  if (!file) {
    message.error("请先选择文件");
    return;
  }
  uploading.value = true;
  try {
    await uploadDocumentApi(props.kbId, file);
    message.success("上传成功，正在解析处理中");
    emit("close");
    emit("uploaded");
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : "上传失败";
    message.error(msg);
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="dialog"
    title="上传文档"
    :closable="true"
    @close="emit('close')"
    @update:show="(v: boolean) => !v && emit('close')"
  >
    <div
      class="dropzone"
      :class="{ 'dropzone--over': dragOver }"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDrop"
    >
      <input
        ref="fileInputRef"
        class="dropzone-input"
        type="file"
        accept=".pdf,.docx,.md,.txt"
        @change="onInputChange"
      />
      <p v-if="selectedFile" class="dropzone-name">{{ selectedFile.name }}</p>
      <template v-else>
        <p class="dropzone-title">点击或拖拽文件到此处</p>
        <p class="dropzone-hint">
          支持 PDF / Word / Markdown / TXT，不超过 20MB
        </p>
      </template>
    </div>
    <template #action>
      <NSpace justify="end">
        <NButton @click="emit('close')">取消</NButton>
        <NButton
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          上传
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.dropzone {
  position: relative;
  display: block;
  width: 100%;
  box-sizing: border-box;
  padding: 28px 16px;
  border: 1px dashed #d0d3e0;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  text-align: center;
  font: inherit;
  color: inherit;
}
.dropzone:hover,
.dropzone--over {
  border-color: #6366f1;
  background: #f5f4ff;
}
.dropzone-input {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}
.dropzone-title,
.dropzone-name {
  margin: 0;
  font-size: 15px;
  color: #334155;
}
.dropzone-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
