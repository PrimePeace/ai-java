<script setup lang="ts">
import { h, onMounted, ref } from "vue";
import { NButton, NDataTable, NSelect, useMessage } from "naive-ui";
import type { DataTableColumns, SelectOption } from "naive-ui";
import { adminAssignRoleApi, adminUserListApi, adminUserStatusApi, roleListApi } from "@/api/admin";
import type { UserVO } from "@/types/user";

const message = useMessage();
const loading = ref(false);
const users = ref<UserVO[]>([]);
const roleOptions = ref<SelectOption[]>([]);

const columns: DataTableColumns<UserVO> = [
  { title: "用户名", key: "username" },
  { title: "昵称", key: "nickname" },
  { title: "当前角色", key: "roleName" },
  {
    title: "状态",
    key: "status",
    render: (row) => (row.status === 1 ? "正常" : "已封禁"),
  },
  {
    title: "调整角色",
    key: "roleId",
    render: (row) =>
      h(NSelect, {
        value: row.roleId,
        options: roleOptions.value,
        style: "width: 140px",
        onUpdateValue: (value: number) => changeRole(row.id, value),
      }),
  },
  {
    title: "操作",
    key: "actions",
    render: (row) =>
      h(
        NButton,
        { size: "small", onClick: () => changeStatus(row) },
        { default: () => (row.status === 1 ? "封禁" : "解封") },
      ),
  },
];

async function load() {
  loading.value = true;
  try {
    const [userRes, roleRes] = await Promise.all([adminUserListApi(), roleListApi()]);
    users.value = userRes.data || [];
    roleOptions.value = (roleRes.data || []).map((role) => ({ label: role.name, value: role.id }));
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

async function changeStatus(row: UserVO) {
  try {
    await adminUserStatusApi({ userId: row.id, status: row.status === 1 ? 2 : 1 });
    message.success("状态已更新");
    await load();
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "更新失败");
  }
}

async function changeRole(userId: number, roleId: number) {
  try {
    await adminAssignRoleApi({ userId, roleId });
    message.success("角色已调整");
    await load();
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "调整失败");
  }
}

onMounted(load);
</script>

<template>
  <NDataTable :columns="columns" :data="users" :loading="loading" :bordered="false" />
</template>
