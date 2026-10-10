<template>
  <view class="page">
    <text class="title">注册</text>
    <input v-model="username" class="input" placeholder="4-32 位字母、数字或下划线" />
    <input v-model="password" class="input" password placeholder="至少 8 位，含字母和数字" />
    <button type="primary" @click="submit">注册</button>
  </view>
</template>

<script>
import { request } from "../../common/http.js";

export default {
  data() {
    return { username: "", password: "" };
  },
  methods: {
    async submit() {
      try {
        await request({
          url: "/user/register",
          method: "POST",
          data: { username: this.username, password: this.password },
        });
        uni.showToast({ title: "注册成功", icon: "none" });
        setTimeout(() => uni.navigateBack(), 500);
      } catch (error) {
        uni.showToast({ title: error.message || "注册失败", icon: "none" });
      }
    },
  },
};
</script>

<style>
.page { padding: 48rpx; }
.title { font-size: 40rpx; font-weight: 600; margin-bottom: 32rpx; display: block; }
.input { border: 1px solid #e5e7eb; border-radius: 12rpx; padding: 20rpx; margin-bottom: 24rpx; }
</style>
