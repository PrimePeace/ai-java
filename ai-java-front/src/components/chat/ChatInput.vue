<script setup lang="ts">
import { ref } from "vue";
import { NInput, NButton, NSpace } from "naive-ui";

const emit = defineEmits<{
  (e: "send", question: string): void;
}>();

interface Props {
  disabled?: boolean;
  placeholder?: string;
}

withDefaults(defineProps<Props>(), {
  placeholder: "输入问题，Enter 发送，Shift+Enter 换行",
});

const question = ref("");

function handleSend() {
  const q = question.value.trim();
  if (!q) return;
  emit("send", q);
  question.value = "";
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}
</script>

<template>
  <div class="chat-input">
    <NSpace vertical>
      <NInput
        v-model:value="question"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :placeholder="placeholder"
        :disabled="disabled"
        @keydown="handleKeydown"
      />
      <NButton
        type="primary"
        :disabled="disabled || !question.trim()"
        @click="handleSend"
      >
        发送
      </NButton>
    </NSpace>
  </div>
</template>

<style scoped>
.chat-input {
  padding: 12px;
  border-top: 1px solid #eee;
}
</style>
