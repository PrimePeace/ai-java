<script setup lang="ts">
import type { ChatMessage } from "@/types/ai";
import { NTag } from "naive-ui";

interface Props {
  messages: ChatMessage[];
  streamingContent: string;
  isStreaming: boolean;
  hasError: boolean;
  errorText?: string;
}

withDefaults(defineProps<Props>(), { errorText: "" });
</script>

<template>
  <div class="message-list">
    <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
      <span class="role">{{ msg.role === "user" ? "你" : "AI" }}</span>
      <div class="bubble" v-if="msg.role === 'user'">{{ msg.content }}</div>
      <div class="bubble assistant" v-else>
        <pre class="content">{{ msg.content }}</pre>
        <div v-if="msg.citations?.length" class="cite-hint">
          📎 {{ msg.citations.length }} 条引用
        </div>
      </div>
    </div>
    <!-- 流式气泡 -->
    <div v-if="isStreaming" class="message assistant">
      <span class="role">AI</span>
      <div class="bubble assistant">
        <pre
          class="content">{{ streamingContent }}<span class="cursor">▌</span></pre>
      </div>
    </div>
    <div v-if="hasError" class="message assistant">
      <NTag type="error" size="small">{{
        errorText || "回答失败，可重发"
      }}</NTag>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.message {
  margin-bottom: 16px;
}
.message.user {
  text-align: right;
}
.role {
  font-size: 12px;
  color: #999;
  font-weight: bold;
}
.bubble {
  display: inline-block;
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  margin-top: 4px;
}
.bubble.assistant {
  background-color: #f1f5f9;
  text-align: left;
}
.message.user .bubble {
  background-color: #eef2ff;
  color: #312e81;
}
.content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
}
.cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}
.cite-hint {
  font-size: 12px;
  color: #18a058;
  margin-top: 4px;
}
</style>
