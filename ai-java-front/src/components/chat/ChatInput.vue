<script setup lang="ts">
import { ref } from "vue";
import { NInput, NButton } from "naive-ui";

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
    <div class="composer">
      <NInput
        v-model:value="question"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :placeholder="placeholder"
        :disabled="disabled"
        @keydown="handleKeydown"
      />
      <div class="composer-actions">
        <NButton
          type="primary"
          :disabled="disabled || !question.trim()"
          @click="handleSend"
        >
          发送
        </NButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  padding: 12px 16px 14px;
  background: #ffffff;
  border-top: 1px solid #e7e9f0;
}

.composer {
  width: min(820px, 100%);
  margin: 0 auto;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
