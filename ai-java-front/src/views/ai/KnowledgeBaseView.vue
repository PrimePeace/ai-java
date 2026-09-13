<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  NCard,
  NButton,
  NSpace,
  NInput,
  NModal,
  useDialog,
  useMessage,
} from "naive-ui";
import type {
  KnowledgeBase,
  CreateKbRequest,
  UpdateKbRequest,
} from "@/types/ai";
import { createKbApi, listKbsApi, updateKbApi, deleteKbApi } from "@/api/kb";

const router = useRouter();
const message = useMessage();
const dialog = useDialog();

const kbs = ref<KnowledgeBase[]>([]);
const showCreate = ref(false);
const editingKb = ref<KnowledgeBase | null>(null);
const kbForm = ref({ name: "", description: "" });

onMounted(fetchKbs);

async function fetchKbs() {
  try {
    const res = await listKbsApi();
    kbs.value = res.data;
  } catch (e: any) {
    message.error(e.message || "加载失败");
  }
}

function openCreate() {
  editingKb.value = null;
  kbForm.value = { name: "", description: "" };
  showCreate.value = true;
}

function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb;
  kbForm.value = { name: kb.name, description: kb.description };
  showCreate.value = true;
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
      title="知识库"
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
.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>
