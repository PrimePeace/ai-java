<script setup lang="ts">
import { ref, watch } from "vue";
import { NInput, NModal, useMessage } from "naive-ui";
import type { PromptTemplate } from "@/types/ai";
import { createPromptApi, updatePromptApi } from "@/api/prompt";

const props = defineProps<{
  show: boolean;
  /** null = 新建；否则为编辑目标 */
  template: PromptTemplate | null;
}>();

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
  (e: "saved"): void;
}>();

const message = useMessage();

const form = ref({
  name: "",
  description: "",
  systemTemplate: "",
  userTemplate: "",
});

// 打开弹窗时初始化表单（编辑回显 / 新建预置默认用户模板）
watch(
    () => props.show,
    (show) => {
      if (!show) return;
      const tpl = props.template;
      form.value = tpl
          ? {
            name: tpl.name,
            description: tpl.description || "",
            systemTemplate: tpl.systemTemplate,
            userTemplate: tpl.userTemplate,
          }
          : {
            name: "",
            description: "",
            systemTemplate: "",
            userTemplate: "{referencesBlock}问题：{question}",
          };
    },
);

async function handleSave() {
  if (!form.value.name.trim()) {
    message.warning("名称不能为空");
    return false;
  }
  if (!form.value.systemTemplate.trim() || !form.value.userTemplate.trim()) {
    message.warning("模板内容不能为空");
    return false;
  }
  try {
    if (props.template) {
      await updatePromptApi({
        id: props.template.id,
        name: form.value.name,
        description: form.value.description,
        systemTemplate: form.value.systemTemplate,
        userTemplate: form.value.userTemplate,
      });
      message.success("已更新");
    } else {
      await createPromptApi({
        name: form.value.name,
        description: form.value.description,
        systemTemplate: form.value.systemTemplate,
        userTemplate: form.value.userTemplate,
      });
      message.success("已创建");
    }
    emit("update:show", false);
    emit("saved");
  } catch (e: any) {
    message.error(e.message || "操作失败");
  }
}
</script>

<template>
  <NModal
      :show="show"
      preset="dialog"
      :title="template ? '编辑模板' : '新建模板'"
      positive-text="保存"
      negative-text="取消"
      :on-positive-click="handleSave"
      :on-negative-click="() => emit('update:show', false)"
  >
    <NInput v-model:value="form.name" placeholder="模板名称" />
    <NInput
        v-model:value="form.description"
        placeholder="描述（可选）"
        style="margin-top: 12px"
    />
    <p class="form-label">系统提示词模板</p>
    <NInput
        v-model:value="form.systemTemplate"
        type="textarea"
        :rows="5"
        placeholder="可用变量：{kbName} {kbDescription}"
    />
    <p class="form-label">用户消息模板</p>
    <NInput
        v-model:value="form.userTemplate"
        type="textarea"
        :rows="5"
        placeholder="可用变量：{question} {references} {referencesBlock}"
    />
    <p class="form-tip">
      系统模板变量：{kbName}（知识库名称）、{kbDescription}（知识库描述）；用户模板变量：{question}（本次提问）、{references}（参考资料原文）、{referencesBlock}（无检索结果时为空，有结果时自动带"参考资料："标题）
    </p>
  </NModal>
</template>

<style scoped>
.form-label {
  margin: 12px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.form-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
</style>
