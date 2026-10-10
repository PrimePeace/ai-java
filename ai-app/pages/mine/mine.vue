<template>
  <view class="page">
    <text class="name">{{ displayName }}</text>
    <text class="role">{{ roleName }}</text>
    <button type="warn" @click="logout">退出登录</button>
  </view>
</template>

<script>
import { clearTokens, request } from "../../common/http.js";

export default {
  data() {
    return { displayName: "", roleName: "" };
  },
  onShow() {
    const user = uni.getStorageSync("user") || {};
    this.displayName = user.nickname || user.username || "";
    this.roleName = user.roleName || "";
  },
  methods: {
    async logout() {
      try {
        await request({ url: "/user/logout", method: "POST" });
      } catch {
        // 仍然清除本地登录态
      }
      clearTokens();
      uni.reLaunch({ url: "/pages/login/login" });
    },
  },
};
</script>

<style>
.page { padding: 48rpx; }
.name { font-size: 36rpx; font-weight: 600; display: block; }
.role { color: #64748b; margin: 16rpx 0 40rpx; display: block; }
</style>
