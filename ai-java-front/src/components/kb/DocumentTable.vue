<script setup lang="ts">
import { h } from "vue";
import {
  NDataTable,
  NButton,
  NSpace,
  NTag,
  useDialog,
  useMessage,
} from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import { deleteDocumentApi } from "@/api/kb";
import {
  DOC_STATUS_LABEL,
  DocStatus,
  type KnowledgeDocument,
} from "@/types/ai";

interface Props {
  documents: KnowledgeDocument[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "refresh"): void;
}>();

const message = useMessage();
const dialog = useDialog();

function statusTag(status: DocStatus) {
  const typeMap: Record<DocStatus, "info" | "warning" | "success" | "error"> = {
    [DocStatus.UPLOADED]: "info",
    [DocStatus.PROCESSING]: "warning",
    [DocStatus.COMPLETED]: "success",
    [DocStatus.FAILED]: "error",
  };
  return h(
    NTag,
    { type: typeMap[status] },
    { default: () => DOC_STATUS_LABEL[status] ?? status },
  );
}

function handleDelete(doc: KnowledgeDocument) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除文档「${doc.fileName}」吗？删除后无法恢复。`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteDocumentApi(doc.id);
        message.success("已删除");
        emit("refresh");
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}

const columns: DataTableColumns<KnowledgeDocument> = [
  { title: "文件名", key: "fileName", ellipsis: { tooltip: true } },
  { title: "类型", key: "fileType", width: 60 },
  {
    title: "大小",
    key: "fileSize",
    width: 80,
    render(row) {
      return (row.fileSize / 1024).toFixed(1) + " KB";
    },
  },
  {
    title: "状态",
    key: "status",
    width: 80,
    render(row) {
      return statusTag(row.status);
    },
  },
  { title: "更新时间", key: "updateTime", width: 160 },
  {
    title: "操作",
    key: "actions",
    width: 80,
    render(row) {
      return h(
        NSpace,
        {},
        {
          default: () => [
            h(
              NButton,
              {
                size: "small",
                type: "error",
                onClick: () => handleDelete(row),
              },
              { default: () => "删除" },
            ),
          ],
        },
      );
    },
  },
];
</script>

<template>
  <NDataTable v-if="documents.length" :columns="columns" :data="documents" />
</template>
