<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { NSelect, useMessage } from "naive-ui";
import SessionList from "@/components/chat/SessionList.vue";
import { listKbsApi } from "@/api/kb";
import type { ChatSession, KnowledgeBase } from "@/types/ai";

const props = defineProps<{ kbId: number }>();
const emit = defineEmits<{
  (e: "select", session: ChatSession): void;
  (e: "change-kb", kbId: number): void;
}>();

const message = useMessage();
const kbs = ref<KnowledgeBase[]>([]);

const kbOptions = computed(() =>
  kbs.value.map((kb) => ({ label: kb.name, value: kb.id })),
);

onMounted(async () => {
  try {
    const res = await listKbsApi();
    kbs.value = res.data ?? [];
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : "加载知识库失败";
    message.error(msg);
  }
});
</script>

<template>
  <div class="side-pane">
    <NSelect
      :value="props.kbId || null"
      :options="kbOptions"
      placeholder="选择知识库"
      size="small"
      @update:value="(v: number) => emit('change-kb', v)"
    />
    <SessionList
      v-if="props.kbId"
      :kb-id="props.kbId"
      @select="emit('select', $event)"
    />
    <p v-else class="hint">请先选择知识库，再新建会话</p>
  </div>
</template>

<style scoped>
.side-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  gap: 8px;
}

.hint {
  text-align: center;
  color: #94a3b8;
  padding: 24px 8px;
  font-size: 13px;
}
</style>
