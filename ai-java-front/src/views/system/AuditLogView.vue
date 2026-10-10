<script setup lang="ts">
import { onMounted, ref } from "vue";
import { NDataTable, useMessage } from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import { auditListApi, type AuditLogRow } from "@/api/admin";

const message = useMessage();
const loading = ref(false);
const rows = ref<AuditLogRow[]>([]);

const columns: DataTableColumns<AuditLogRow> = [
  { title: "时间", key: "createTime", width: 180 },
  { title: "用户", key: "username" },
  { title: "模块", key: "operationModule" },
  { title: "描述", key: "description" },
  { title: "结果", key: "result", width: 100 },
  { title: "IP", key: "ipAddress", width: 140 },
];

async function load() {
  loading.value = true;
  try {
    const res = await auditListApi(1, 20);
    rows.value = res.data?.records || [];
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <NDataTable :columns="columns" :data="rows" :loading="loading" :bordered="false" />
</template>
