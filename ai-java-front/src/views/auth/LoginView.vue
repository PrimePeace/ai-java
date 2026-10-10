<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { NForm, NFormItem, NInput, NButton, useMessage } from "naive-ui";
import type { FormInst, FormRules } from "naive-ui";
import { useUserStore } from "@/stores/user";
import { useMenuStore } from "@/stores/menu";
import AuthBrandPanel from "@/components/auth/AuthBrandPanel.vue";

const router = useRouter();
const route = useRoute();
const message = useMessage();
const userStore = useUserStore();
const menuStore = useMenuStore();

const formRef = ref<FormInst | null>(null);
const loading = ref(false);

const formData = ref({
  username: "",
  password: "",
});

const rules: FormRules = {
  username: {
    required: true,
    message: "请输入用户名",
    trigger: ["input", "blur"],
  },
  password: {
    required: true,
    message: "请输入密码",
    trigger: ["input", "blur"],
  },
};

onMounted(() => {
  // 如果有 redirect 参数且用户已登录，直接跳转
  if (userStore.accessToken && route.query.redirect) {
    router.push(route.query.redirect as string);
  }
});

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (errors) => {
    if (errors) return;
    loading.value = true;
    try {
      await userStore.login(formData.value);
      await menuStore.load();
      message.success("登录成功");
      const redirect = (route.query.redirect as string) || menuStore.homePath() || "/403";
      router.push(redirect);
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : "登录失败，请重试";
      message.error(msg);
    } finally {
      loading.value = false;
    }
  });
}
</script>

<template>
  <div class="auth-page">
    <AuthBrandPanel class="auth-brand" />
    <div class="auth-main">
      <div class="auth-card">
        <h2 class="auth-title">欢迎回来</h2>
        <p class="auth-subtitle">登录你的账号，继续探索知识</p>
        <NForm
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-placement="top"
          size="large"
        >
          <NFormItem label="用户名" path="username">
            <NInput
              v-model:value="formData.username"
              placeholder="请输入用户名"
              @keyup.enter="handleSubmit"
            />
          </NFormItem>
          <NFormItem label="密码" path="password">
            <NInput
              v-model:value="formData.password"
              type="password"
              placeholder="请输入密码"
              show-password-on="click"
              @keyup.enter="handleSubmit"
            />
          </NFormItem>
          <NButton
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleSubmit"
          >
            登 录
          </NButton>
        </NForm>
        <p class="auth-switch">
          还没有账号？
          <RouterLink to="/register">立即注册</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>
