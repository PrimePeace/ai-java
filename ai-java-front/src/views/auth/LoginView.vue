<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NSpace,
  useMessage,
} from "naive-ui";
import type { FormInst, FormRules } from "naive-ui";
import { useUserStore } from "@/stores/user";

const router = useRouter();
const route = useRoute();
const message = useMessage();
const userStore = useUserStore();

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
      message.success("登录成功");
      const redirect = (route.query.redirect as string) || "/dashboard";
      router.push(redirect);
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : "登录失败，请重试";
      message.error(msg);
    } finally {
      loading.value = false;
    }
  });
}

function goToRegister() {
  router.push("/register");
}
</script>

<template>
  <div class="auth-page">
    <NCard title="登录" class="auth-card">
      <NForm
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-placement="left"
        label-width="80"
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
        <NFormItem>
          <NSpace>
            <NButton type="primary" :loading="loading" @click="handleSubmit">
              登录
            </NButton>
            <NButton @click="goToRegister"> 注册账号 </NButton>
          </NSpace>
        </NFormItem>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f5f5;
}

.auth-card {
  width: 400px;
}
</style>
