<script setup lang="ts">
import { ref, watch } from "vue";
import {
  NButton,
  NSpace,
  NList,
  NListItem,
  NThing,
  useDialog,
  useMessage,
} from "naive-ui";
import type { ChatSession } from "@/types/ai";
import {
  createSessionApi,
  listSessionsApi,
  deleteSessionApi,
} from "@/api/chat";

interface Props {
  kbId: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "select", session: ChatSession): void;
  (e: "refresh"): void;
}>();

const message = useMessage();
const dialog = useDialog();
const sessions = ref<ChatSession[]>([]);

watch(() => props.kbId, fetchSessions, { immediate: true });

async function fetchSessions() {
  try {
    const res = await listSessionsApi(props.kbId);
    sessions.value = res.data;
  } catch (e: any) {
    message.error(e.message || "加载会话失败");
  }
}

async function createSession() {
  try {
    const res = await createSessionApi({ kbId: props.kbId });
    await fetchSessions();
    emit("select", res.data);
  } catch (e: any) {
    message.error(e.message || "创建失败");
  }
}

function handleDelete(session: ChatSession) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除会话「${session.title}」吗？`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteSessionApi(session.id);
        message.success("已删除");
        await fetchSessions();
        emit("refresh");
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}
</script>

<template>
  <div class="session-list">
    <NSpace justify="space-between" align="center">
      <h4>会话列表</h4>
      <NButton size="small" @click="createSession">新建</NButton>
    </NSpace>
    <NList hoverable>
      <NListItem v-for="s in sessions" :key="s.id" @click="emit('select', s)">
        <NThing
          :title="s.title"
          :description="new Date(s.updateTime).toLocaleString()"
        />
        <template #suffix>
          <NButton
            size="tiny"
            type="error"
            quaternary
            @click.stop="handleDelete(s)"
            >删除</NButton
          >
        </template>
      </NListItem>
    </NList>
    <p v-if="sessions.length === 0" class="empty">暂无会话</p>
  </div>
</template>

<style scoped>
.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
h4 {
  margin: 0;
}
.empty {
  text-align: center;
  color: #999;
  padding: 20px 0;
}
</style>
