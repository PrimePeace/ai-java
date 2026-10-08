<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NCard, NSpace, NButton, NDrawer, useMessage } from "naive-ui";
import ChatSidePane from "@/components/chat/ChatSidePane.vue";
import MessageList from "@/components/chat/MessageList.vue";
import ChatInput from "@/components/chat/ChatInput.vue";
import CitationPanel from "@/components/chat/CitationPanel.vue";
import type { ChatSession, ChatMessage, Citation } from "@/types/ai";
import { useChatStream } from "@/composables/useChatStream";
import { listMessagesApi } from "@/api/chat";

const route = useRoute();
const router = useRouter();
const message = useMessage();

const kbId = computed(() => Number(route.query.kbId) || 0);
const sessionId = computed(() => Number(route.query.sessionId) || 0);

const messages = ref<ChatMessage[]>([]);
const citations = ref<Citation[]>([]);
const showLeft = ref(false);
const showRight = ref(false);
const hasError = ref(false);
const errorText = ref("");

const { send, abort, isStreaming, assistantContent } = useChatStream({
  onMessage: (delta: string) => {
    assistantContent.value += delta;
  },
  onCitations: (cites: Citation[]) => {
    citations.value = cites;
  },
  onEnd: async () => {
    if (sessionId.value) {
      await loadMessages(sessionId.value);
    }
  },
  onError: (error: string) => {
    hasError.value = true;
    errorText.value = error;
    message.error(error);
  },
});

const cardContentStyle = {
  padding: "0",
  display: "flex",
  flexDirection: "column",
  flex: "1",
  minHeight: "0",
  overflow: "hidden",
};

const inputDisabled = computed(() => isStreaming.value || !sessionId.value);
const inputPlaceholder = computed(() => {
  if (!kbId.value) return "请先选择知识库";
  if (!sessionId.value) return "请先在左侧新建会话";
  return "输入问题，Enter 发送，Shift+Enter 换行";
});

async function loadMessages(sid: number) {
  hasError.value = false;
  errorText.value = "";
  citations.value = [];
  assistantContent.value = "";
  try {
    const res = await listMessagesApi(sid);
    messages.value = res.data ?? [];
  } catch {
    messages.value = [];
  }
}

function resetConversation() {
  abort();
  messages.value = [];
  citations.value = [];
  assistantContent.value = "";
  hasError.value = false;
  errorText.value = "";
}

async function handleSend(question: string) {
  if (!sessionId.value) {
    message.warning("请先新建会话");
    return;
  }
  hasError.value = false;
  errorText.value = "";
  assistantContent.value = "";
  citations.value = [];

  const tempMsg: ChatMessage = {
    id: Date.now(),
    role: "user",
    content: question,
    citations: null,
    createTime: new Date().toISOString(),
  };
  messages.value.push(tempMsg);

  await send(sessionId.value, question);
}

watch(
  sessionId,
  (newSid) => {
    if (newSid) {
      abort();
      loadMessages(newSid);
    } else {
      resetConversation();
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  abort();
});

function handleSelectSession(session: ChatSession) {
  router.replace({ query: { kbId: kbId.value, sessionId: session.id } });
}

function handleChangeKb(id: number) {
  router.replace({ query: { kbId: id } });
}
</script>

<template>
  <div class="chat-page">
    <div class="chat-sidebar">
      <ChatSidePane
        :kb-id="kbId"
        @select="handleSelectSession"
        @change-kb="handleChangeKb"
      />
    </div>
    <div class="chat-main">
      <NCard class="chat-card" :content-style="cardContentStyle">
        <template #header>
          <div class="drawer-triggers">
            <NSpace>
              <NButton size="small" @click="showLeft = true">会话</NButton>
              <NButton size="small" @click="showRight = true">引用</NButton>
            </NSpace>
          </div>
        </template>
        <MessageList
          :messages="messages"
          :streaming-content="assistantContent"
          :is-streaming="isStreaming"
          :has-error="hasError"
          :error-text="errorText"
        />
        <ChatInput
          :disabled="inputDisabled"
          :placeholder="inputPlaceholder"
          @send="handleSend"
        />
      </NCard>
    </div>
    <div class="chat-citation">
      <CitationPanel :citations="citations" />
    </div>
    <NDrawer v-model:show="showLeft" width="320" placement="left">
      <ChatSidePane
        :kb-id="kbId"
        @select="handleSelectSession"
        @change-kb="handleChangeKb"
      />
    </NDrawer>
    <NDrawer v-model:show="showRight" width="320" placement="right">
      <CitationPanel :citations="citations" />
    </NDrawer>
  </div>
</template>
