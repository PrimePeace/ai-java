<template>
  <view class="page">
    <text class="title">登录</text>
    <input v-model="username" class="input" placeholder="用户名" />
    <input v-model="password" class="input" password placeholder="密码" />
    <button type="primary" @click="submit">登录</button>
    <view class="link" @click="goRegister">没有账号，去注册</view>
  </view>
</template>

<script>
import { request, setTokens } from "../../common/http.js";

export default {
  data() {
    return { username: "", password: "" };
  },
  methods: {
    async submit() {
      try {
        const res = await request({
          url: "/user/login",
          method: "POST",
          data: { username: this.username, password: this.password },
        });
        setTokens(res.data.accessToken, res.data.refreshToken);
        uni.setStorageSync("user", res.data.user);
        uni.reLaunch({ url: "/pages/home/home" });
      } catch (error) {
        uni.showToast({ title: error.message || "登录失败", icon: "none" });
      }
    },
    goRegister() {
      uni.navigateTo({ url: "/pages/register/register" });
    },
  },
};
</script>

<style>
.page { padding: 48rpx; }
.title { font-size: 40rpx; font-weight: 600; margin-bottom: 32rpx; display: block; }
.input { border: 1px solid #e5e7eb; border-radius: 12rpx; padding: 20rpx; margin-bottom: 24rpx; }
.link { margin-top: 24rpx; color: #4f46e5; text-align: center; }
</style>
