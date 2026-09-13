<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { NIcon } from "naive-ui";
import { ChatbubbleEllipsesOutline } from "@vicons/ionicons5";
import type { ChatSession } from "@/types/ai";

const props = defineProps<{ sessions: ChatSession[] }>();

const router = useRouter();

/** 最近更新的 5 个会话 */
const recentSessions = computed(() =>
  [...props.sessions]
    .sort((a, b) => b.updateTime.localeCompare(a.updateTime))
    .slice(0, 5),
);

function formatTime(time: string) {
  return time ? time.slice(0, 16).replace("T", " ") : "";
}

function openSession(session: ChatSession) {
  router.push({
    path: "/chat",
    query: { kbId: session.kbId, sessionId: session.id },
  });
}
</script>

<template>
  <ul v-if="recentSessions.length > 0" class="session-list">
    <li v-for="s in recentSessions" :key="s.id" @click="openSession(s)">
      <span class="session-icon">
        <NIcon :size="16"><ChatbubbleEllipsesOutline /></NIcon>
      </span>
      <div class="session-info">
        <p class="session-title">{{ s.title || "未命名会话" }}</p>
        <p class="session-meta">
          {{ s.kbName }} · {{ formatTime(s.updateTime) }}
        </p>
      </div>
    </li>
  </ul>
  <p v-else class="empty">还没有会话，去「智能问答」开始第一场对话吧</p>
</template>

<style scoped>
.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.session-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.session-list li:hover {
  background-color: #f1f2f8;
}

.session-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eef0fe;
  color: #6366f1;
}

.session-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.empty {
  text-align: center;
  color: #94a3b8;
  padding: 32px 0;
  font-size: 13px;
  margin: 0;
}
</style>
