<script setup lang="ts">
import { onMounted, ref } from "vue";
import { NButton, NCard, NCheckbox, NCheckboxGroup, NInput, useMessage } from "naive-ui";
import { menuListApi } from "@/api/menu";
import { roleAssignMenuApi, roleListApi, roleUpdateApi } from "@/api/admin";
import type { MenuNode, RoleVO } from "@/types/menu";

const message = useMessage();
const roles = ref<RoleVO[]>([]);
const menus = ref<MenuNode[]>([]);
const selected = ref<Record<number, number[]>>({});
const names = ref<Record<number, string>>({});

async function load() {
  const [roleRes, menuRes] = await Promise.all([roleListApi(), menuListApi()]);
  roles.value = roleRes.data || [];
  menus.value = menuRes.data || [];
  for (const role of roles.value) {
    selected.value[role.id] = [...(role.menuIds || [])];
    names.value[role.id] = role.name;
  }
}

async function saveName(role: RoleVO) {
  try {
    await roleUpdateApi({ id: role.id, name: names.value[role.id], remark: role.remark });
    message.success("名称已保存");
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "保存失败");
  }
}

async function saveMenus(role: RoleVO) {
  try {
    await roleAssignMenuApi({ roleId: role.id, menuIds: selected.value[role.id] || [] });
    message.success("菜单已保存，该角色重新进入后生效");
  } catch (error: unknown) {
    message.error(error instanceof Error ? error.message : "保存失败");
  }
}

onMounted(load);
</script>

<template>
  <div class="role-list">
    <NCard v-for="role in roles" :key="role.id" :title="`${role.name}（${role.code}）`">
      <div class="name-row">
        <NInput v-model:value="names[role.id]" />
        <NButton @click="saveName(role)">保存名称</NButton>
      </div>
      <NCheckboxGroup v-model:value="selected[role.id]">
        <NCheckbox v-for="menu in menus" :key="menu.id" :value="menu.id">
          {{ menu.name }}<span v-if="menu.permission"> · {{ menu.permission }}</span>
        </NCheckbox>
      </NCheckboxGroup>
      <NButton type="primary" class="save-menu" @click="saveMenus(role)">保存菜单</NButton>
    </NCard>
  </div>
</template>

<style scoped>
.role-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.name-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.save-menu {
  margin-top: 12px;
}
</style>
