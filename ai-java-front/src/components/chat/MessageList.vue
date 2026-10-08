<script setup lang="ts">
import { ref } from "vue";
import type { ChatMessage } from "@/types/ai";
import { NTag } from "naive-ui";
import { renderChatMarkdown } from "@/utils/chatMarkdown";
import { useFollowBottom } from "@/composables/useFollowBottom";

interface Props {
  messages: ChatMessage[];
  streamingContent: string;
  isStreaming: boolean;
  hasError: boolean;
  errorText?: string;
}

const props = withDefaults(defineProps<Props>(), { errorText: "" });
const listRef = ref<HTMLDivElement | null>(null);

useFollowBottom(
  listRef,
  () =>
    [
      props.messages.length,
      props.messages.at(-1)?.id,
      props.streamingContent,
      props.isStreaming,
      props.hasError,
    ] as const,
);
</script>

<template>
  <div ref="listRef" class="message-list">
    <div class="thread">
      <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.role]">
        <span class="avatar">{{ msg.role === "user" ? "你" : "AI" }}</span>
        <div class="bubble">
          <div class="md" v-html="renderChatMarkdown(msg.content)" />
          <div v-if="msg.citations?.length" class="cite">
            {{ msg.citations.length }} 条引用
          </div>
        </div>
      </div>
      <div v-if="isStreaming" class="msg assistant">
        <span class="avatar">AI</span>
        <div class="bubble">
          <div class="md" v-html="renderChatMarkdown(streamingContent, true)" />
        </div>
      </div>
      <div v-if="hasError" class="msg assistant">
        <span class="avatar">AI</span>
        <NTag type="error" size="small">{{
          errorText || "回答失败，可重发"
        }}</NTag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-anchor: none;
  padding: 20px 24px 12px;
  background: #f5f6fa;
}

.thread {
  width: min(820px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.msg.user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.msg.assistant .avatar {
  background: #eef2ff;
  color: #4f46e5;
}

.msg.user .avatar {
  background: #6366f1;
  color: #ffffff;
}

.bubble {
  max-width: min(720px, calc(100% - 38px));
  min-width: 0;
  padding: 10px 14px;
  border-radius: 4px 12px 12px 12px;
  background: #ffffff;
  border: 1px solid #e7e9f0;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.75;
}

.msg.user .bubble {
  border-radius: 12px 4px 12px 12px;
  background: #eef2ff;
  border-color: #e0e7ff;
  color: #1e1b4b;
}

.md :deep(h1),
.md :deep(h2),
.md :deep(h3) {
  margin: 12px 0 6px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #0f172a;
}

.md :deep(p) {
  margin: 0 0 8px;
}

.md :deep(ul),
.md :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 1.25em;
}

.md :deep(li) {
  margin: 2px 0;
}

.md :deep(li + li) {
  margin-top: 4px;
}

.md :deep(:first-child) {
  margin-top: 0;
}

.md :deep(:last-child) {
  margin-bottom: 0;
}

.md :deep(strong) {
  font-weight: 600;
}

.md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.92em;
  background: #f1f5f9;
  padding: 0 4px;
  border-radius: 4px;
}

.md :deep(pre) {
  margin: 8px 0;
  padding: 10px 12px;
  overflow-x: auto;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e7e9f0;
}

.md :deep(pre code) {
  padding: 0;
  background: transparent;
  white-space: pre-wrap;
  word-break: break-word;
}

.md {
  overflow-wrap: anywhere;
}

.md :deep(.cursor) {
  color: #6366f1;
  animation: blink 1s step-end infinite;
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

.cite {
  display: inline-flex;
  margin-top: 10px;
  padding: 1px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  line-height: 20px;
}
</style>
