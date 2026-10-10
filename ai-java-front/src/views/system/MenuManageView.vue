<script setup lang="ts">
import { onMounted, ref } from "vue";
import { NButton, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect, useMessage } from "naive-ui";
import type { DataTableColumns, SelectOption } from "naive-ui";
import { menuListApi, menuSaveApi } from "@/api/menu";
import type { MenuNode, MenuSaveRequest } from "@/types/menu";

const message = useMessage();
const menus = ref<MenuNode[]>([]);
const show = ref(false);
const form = ref<MenuSaveRequest>(emptyForm());

const clientOptions: SelectOption[] = [
  { label: "全部", value: "ALL" },
  { label: "网页", value: "WEB" },
  { label: "App", value: "APP" },
];
const typeOptions: SelectOption[] = [
  { label: "页面", value: "MENU" },
  { label: "目录", value: "CATALOG" },
  { label: "权限点", value: "BUTTON" },
];
const visibleOptions: SelectOption[] = [
  { label: "显示", value: 1 },
  { label: "隐藏", value: 0 },
];

const columns: DataTableColumns<MenuNode> = [
  { title: "名称", key: "name" },
  { title: "路径", key: "path" },
  { title: "权限", key: "permission" },
  { title: "端", key: "client" },
  { title: "可见", key: "visible", render: (row) => (row.visible === 1 ? "是" : "否") },
];

function emptyForm(): MenuSaveRequest {
  return {
    parentId: 0, name: "", path: "", component: "", icon: "list",
    sort: 0, menuType: "MENU", permission: "", visible: 1, client: "WEB", status: 1,
  };
}

function openEdit(row: MenuNode) {
  form.value = {
    id: row.id,
    parentId: row.parentId ?? 0,
    name: row.name,
    path: row.path || "",
    component: row.component || "",
    icon: row.icon || "",
    sort: row.sort ?? 0,
    menuType: row.menuType,
    permission: row.permission || "",
    visible: row.visible,
    client: row.client,
    status: row.status,
  };
  show.value = true;
}

async function load() {
  const res = await menuListApi();
  menus.value = res.data || [];
}

async function save() {
  try {
    await menuSaveApi(form.value);
    message.success("已保存");
    show.value = false;
    await load();
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "保存失败");
  }
}

onMounted(load);
</script>

<template>
  <NButton type="primary" style="margin-bottom: 12px" @click="form = emptyForm(); show = true">新增菜单</NButton>
  <NDataTable
    :columns="columns"
    :data="menus"
    :bordered="false"
    :row-props="(row: MenuNode) => ({ style: 'cursor: pointer', onClick: () => openEdit(row) })"
  />
  <NModal v-model:show="show" preset="card" title="菜单" style="width: 520px">
    <NForm label-placement="left" label-width="72">
      <NFormItem label="名称"><NInput v-model:value="form.name" /></NFormItem>
      <NFormItem label="排序"><NInputNumber v-model:value="form.sort" /></NFormItem>
      <NFormItem label="可见"><NSelect v-model:value="form.visible" :options="visibleOptions" /></NFormItem>
      <NFormItem label="所属端"><NSelect v-model:value="form.client" :options="clientOptions" /></NFormItem>
      <NFormItem label="类型"><NSelect v-model:value="form.menuType" :options="typeOptions" /></NFormItem>
      <NFormItem label="路径"><NInput v-model:value="form.path" /></NFormItem>
      <NFormItem label="组件键"><NInput v-model:value="form.component" /></NFormItem>
      <NFormItem label="权限码"><NInput v-model:value="form.permission" /></NFormItem>
      <NButton type="primary" @click="save">保存</NButton>
    </NForm>
  </NModal>
</template>
