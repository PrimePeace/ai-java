<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  NCard,
  NButton,
  NSpace,
  NInput,
  NModal,
  NSelect,
  useDialog,
  useMessage,
} from "naive-ui";
import type {
  KnowledgeBase,
  CreateKbRequest,
  UpdateKbRequest,
  PromptTemplate,
} from "@/types/ai";
import { createKbApi, listKbsApi, updateKbApi, deleteKbApi } from "@/api/kb";
import { listPromptsApi } from "@/api/prompt";

const router = useRouter();
const message = useMessage();
const dialog = useDialog();

const kbs = ref<KnowledgeBase[]>([]);
const templates = ref<PromptTemplate[]>([]);
const showCreate = ref(false);
const editingKb = ref<KnowledgeBase | null>(null);
const kbForm = ref({
  name: "",
  description: "",
  promptTemplateId: 0,
  chatEngine: "rag" as "rag" | "style" | "auto",
});

const isEdit = computed(() => editingKb.value !== null);

// 下拉选项：默认模板（0） + 我的模板
const templateOptions = computed(() => [
  { label: "默认模板", value: 0 },
  ...templates.value.map((t) => ({ label: t.name, value: t.id })),
]);

// 问答引擎选项
const engineOptions = [
  { label: "纯 RAG 检索增强（默认）", value: "rag" },
  { label: "风格增强（RAG + 回答风格，需已生成风格）", value: "style" },
  { label: "自动（有回答风格走风格增强，否则纯 RAG）", value: "auto" },
];

/** 风格摘要（编辑弹窗展示用，截断 40 字） */
function styleSummary(sp: string) {
  return sp.length > 40 ? sp.slice(0, 40) + "…" : sp;
}

onMounted(() => {
  fetchKbs();
  fetchTemplates();
});

async function fetchKbs() {
  try {
    const res = await listKbsApi();
    kbs.value = res.data;
  } catch (e: any) {
    message.error(e.message || "加载失败");
  }
}

async function fetchTemplates() {
  try {
    const res = await listPromptsApi();
    templates.value = res.data;
  } catch (e: any) {
    message.error(e.message || "模板加载失败");
  }
}

function openCreate() {
  editingKb.value = null;
  kbForm.value = { name: "", description: "", promptTemplateId: 0, chatEngine: "rag" };
  showCreate.value = true;
}

function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb;
  kbForm.value = {
    name: kb.name,
    description: kb.description,
    // null=默认模板，下拉统一用 0 表示
    promptTemplateId: kb.promptTemplateId ?? 0,
    chatEngine: kb.chatEngine ?? "rag",
  };
  showCreate.value = true;
}

/** 清除回答风格（stylePrompt 置空，引擎回退 rag） */
async function handleClearStyle() {
  if (!editingKb.value) return;
  try {
    await updateKbApi({
      id: editingKb.value.id,
      name: kbForm.value.name,
      description: kbForm.value.description,
      stylePrompt: "",
      chatEngine: "rag",
    });
    editingKb.value.stylePrompt = null;
    kbForm.value.chatEngine = "rag";
    message.success("已清除回答风格");
    await fetchKbs();
  } catch (e: any) {
    message.error(e.message || "清除失败");
  }
}

async function handleSave() {
  if (!kbForm.value.name.trim()) {
    message.warning("名称不能为空");
    return false;
  }
  try {
    if (editingKb.value) {
      const data: UpdateKbRequest = {
        id: editingKb.value.id,
        name: kbForm.value.name,
        description: kbForm.value.description,
        promptTemplateId: kbForm.value.promptTemplateId,
        chatEngine: kbForm.value.chatEngine,
      };
      await updateKbApi(data);
      message.success("已更新");
    } else {
      const data: CreateKbRequest = {
        name: kbForm.value.name,
        description: kbForm.value.description,
      };
      await createKbApi(data);
      message.success("已创建");
    }
    showCreate.value = false;
    await fetchKbs();
  } catch (e: any) {
    message.error(e.message || "操作失败");
  }
}

function handleDelete(kb: KnowledgeBase) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除知识库「${kb.name}」吗？该库下的文档、切片、向量、会话将一并被删除。`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteKbApi(kb.id);
        message.success("已删除");
        await fetchKbs();
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}

function enterDetail(kb: KnowledgeBase) {
  router.push(`/kb/${kb.id}`);
}
</script>

<template>
  <div class="kb-page">
    <NCard title="知识库管理" class="kb-card">
      <template #header-extra>
        <NButton type="primary" size="small" @click="openCreate"
        >新建知识库</NButton
        >
      </template>
      <NSpace vertical size="large">
        <NCard v-for="kb in kbs" :key="kb.id" hoverable>
          <div class="kb-item">
            <div class="kb-info">
              <h3>{{ kb.name }}</h3>
              <p>{{ kb.description || "暂无描述" }}</p>
              <span class="kb-meta">{{ kb.docCount }} 个文档</span>
            </div>
            <NSpace>
              <NButton size="small" @click="enterDetail(kb)">进入</NButton>
              <NButton size="small" @click="openEdit(kb)">编辑</NButton>
              <NButton size="small" type="error" @click="handleDelete(kb)"
              >删除</NButton
              >
            </NSpace>
          </div>
        </NCard>
        <p v-if="kbs.length === 0" class="empty">
          暂无知识库，点击上方「新建」开始
        </p>
      </NSpace>
    </NCard>
    <NModal
        v-model:show="showCreate"
        preset="dialog"
        :title="isEdit ? '编辑知识库' : '新建知识库'"
        positive-text="保存"
        negative-text="取消"
        :on-positive-click="handleSave"
        :on-negative-click="() => (showCreate = false)"
    >
      <NInput v-model:value="kbForm.name" placeholder="知识库名称" />
      <NInput
          v-model:value="kbForm.description"
          type="textarea"
          placeholder="描述（可选）"
          style="margin-top: 12px"
      />
      <template v-if="isEdit">
        <p class="kb-form-label">提示词模板</p>
        <NSelect
            v-model:value="kbForm.promptTemplateId"
            :options="templateOptions"
            placeholder="选择提示词模板"
        />
        <p class="kb-form-label">问答引擎</p>
        <NSelect
            v-model:value="kbForm.chatEngine"
            :options="engineOptions"
            placeholder="选择问答引擎"
        />
        <template v-if="editingKb?.stylePrompt">
          <p class="kb-form-label">已生成回答风格</p>
          <div class="style-row">
            <span class="style-text">{{ styleSummary(editingKb.stylePrompt) }}</span>
            <NButton size="tiny" tertiary type="error" @click="handleClearStyle">
              清除
            </NButton>
          </div>
        </template>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.kb-page {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.kb-card {
  min-height: 400px;
}
.kb-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kb-info h3 {
  margin: 0 0 4px;
}
.kb-info p {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
}
.kb-meta {
  font-size: 12px;
  color: #999;
}
.kb-form-label {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.style-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.style-text {
  font-size: 12px;
  color: #64748b;
  word-break: break-all;
}
.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>
