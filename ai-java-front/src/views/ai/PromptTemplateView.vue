<script setup lang="ts">
import { ref, onMounted } from "vue";
import { NCard, NButton, NSpace, useDialog, useMessage } from "naive-ui";
import type { PromptTemplate } from "@/types/ai";
import { listPromptsApi, deletePromptApi } from "@/api/prompt";
import PromptEditModal from "@/components/PromptEditModal.vue";

const message = useMessage();
const dialog = useDialog();

const templates = ref<PromptTemplate[]>([]);
const showEdit = ref(false);
const editingTpl = ref<PromptTemplate | null>(null);

onMounted(fetchTemplates);

async function fetchTemplates() {
  try {
    const res = await listPromptsApi();
    templates.value = res.data;
  } catch (e: any) {
    message.error(e.message || "加载失败");
  }
}

function openCreate() {
  editingTpl.value = null;
  showEdit.value = true;
}

function openEdit(tpl: PromptTemplate) {
  editingTpl.value = tpl;
  showEdit.value = true;
}

function handleSaved() {
  fetchTemplates();
}

function handleDelete(tpl: PromptTemplate) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除模板「${tpl.name}」吗？已绑定它的知识库将回退为默认模板。`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deletePromptApi(tpl.id);
        message.success("已删除");
        await fetchTemplates();
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}
</script>

<template>
  <div class="prompt-page">
    <NCard title="提示词模板" class="prompt-card">
      <template #header-extra>
        <NButton type="primary" size="small" @click="openCreate"
        >新建模板</NButton
        >
      </template>
      <NSpace vertical size="large">
        <NCard v-for="tpl in templates" :key="tpl.id" hoverable>
          <div class="tpl-item">
            <div class="tpl-info">
              <h3>{{ tpl.name }}</h3>
              <p>{{ tpl.description || "暂无描述" }}</p>
              <span class="tpl-meta">更新于 {{ tpl.updateTime }}</span>
            </div>
            <NSpace>
              <NButton size="small" @click="openEdit(tpl)">编辑</NButton>
              <NButton size="small" type="error" @click="handleDelete(tpl)"
              >删除</NButton
              >
            </NSpace>
          </div>
        </NCard>
        <p v-if="templates.length === 0" class="empty">
          暂无模板，点击上方「新建」开始
        </p>
      </NSpace>
    </NCard>
    <PromptEditModal
        v-model:show="showEdit"
        :template="editingTpl"
        @saved="handleSaved"
    />
  </div>
</template>

<style scoped>
.prompt-page {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.prompt-card {
  min-height: 400px;
}
.tpl-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.tpl-info h3 {
  margin: 0 0 4px;
}
.tpl-info p {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
}
.tpl-meta {
  font-size: 12px;
  color: #999;
}
.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>
