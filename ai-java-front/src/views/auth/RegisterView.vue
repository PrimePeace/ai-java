<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { NForm, NFormItem, NInput, NButton, useMessage } from "naive-ui";
import type { FormInst, FormRules } from "naive-ui";
import { useUserStore } from "@/stores/user";
import AuthBrandPanel from "@/components/auth/AuthBrandPanel.vue";

const router = useRouter();
const message = useMessage();
const userStore = useUserStore();

const formRef = ref<FormInst | null>(null);
const loading = ref(false);

const formData = ref({
  username: "",
  password: "",
  confirmPassword: "",
  nickname: "",
  email: "",
  phone: "",
});

const rules: FormRules = {
  username: {
    required: true,
    message: "请输入用户名",
    trigger: ["input", "blur"],
  },
  password: {
    required: true,
    message: "请输入密码（至少8位）",
    trigger: ["input", "blur"],
    validator: (_rule, value: string) => {
      if (!value || value.length < 8) {
        return new Error("密码长度不能少于8位");
      }
      return true;
    },
  },
  confirmPassword: {
    required: true,
    message: "请再次输入密码",
    trigger: ["input", "blur"],
    validator: (_rule: unknown, value: string) => {
      if (value !== formData.value.password) {
        return new Error("两次输入的密码不一致");
      }
      return true;
    },
  },
  email: {
    trigger: ["input", "blur"],
    type: "email",
    message: "请输入有效的邮箱地址",
  },
  phone: {
    trigger: ["input", "blur"],
    pattern: /^1[3-9]\d{9}$/,
    message: "请输入有效的手机号",
  },
};

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (errors) => {
    if (errors) return;
    loading.value = true;
    try {
      const { confirmPassword: _confirm, ...registerData } = formData.value;
      await userStore.register(registerData);
      message.success("注册成功，请登录");
      router.push("/login");
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : "注册失败，请重试";
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
        <h2 class="auth-title">创建账号</h2>
        <p class="auth-subtitle">注册后即可构建专属知识库</p>
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
            />
          </NFormItem>
          <NFormItem label="密码" path="password">
            <NInput
              v-model:value="formData.password"
              type="password"
              placeholder="请输入密码（至少8位）"
              show-password-on="click"
            />
          </NFormItem>
          <NFormItem label="确认密码" path="confirmPassword">
            <NInput
              v-model:value="formData.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              show-password-on="click"
            />
          </NFormItem>
          <NFormItem label="昵称" path="nickname">
            <NInput
              v-model:value="formData.nickname"
              placeholder="请输入昵称（可选）"
            />
          </NFormItem>
          <NFormItem label="邮箱" path="email">
            <NInput
              v-model:value="formData.email"
              placeholder="请输入邮箱（可选）"
            />
          </NFormItem>
          <NFormItem label="手机号" path="phone">
            <NInput
              v-model:value="formData.phone"
              placeholder="请输入手机号（可选）"
            />
          </NFormItem>
          <NButton
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleSubmit"
          >
            注 册
          </NButton>
        </NForm>
        <p class="auth-switch">
          已有账号？
          <RouterLink to="/login">返回登录</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>
