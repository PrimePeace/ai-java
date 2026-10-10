<template>
  <view class="page">
    <view v-if="entries.length === 0" class="empty">{{ emptyText }}</view>
    <view v-for="item in entries" :key="item.id" class="card" @click="open(item)">
      <text class="name">{{ item.name }}</text>
    </view>
    <view class="card" @click="openMine">我的</view>
  </view>
</template>

<script>
import { ensureLogin, request } from "../../common/http.js";

const pageMap = {
  "/kb": "/pages/kb/list",
  "/chat": "/pages/chat/chat",
};

export default {
  data() {
    return { entries: [], emptyText: "加载中" };
  },
  onShow() {
    if (!ensureLogin()) {
      return;
    }
    this.load();
  },
  methods: {
    async load() {
      try {
        const res = await request({ url: "/menu/nav?client=app" });
        const list = flatten(res.data || []).filter(
          (item) => item.menuType === "MENU" && item.visible === 1 && item.path && pageMap[item.path],
        );
        this.entries = list;
        this.emptyText = list.length
          ? ""
          : "当前账号没有 App 业务菜单。管理员请使用网页后台。";
      } catch (error) {
        this.emptyText = error.message || "菜单加载失败";
      }
    },
    open(item) {
      uni.navigateTo({ url: pageMap[item.path] });
    },
    openMine() {
      uni.navigateTo({ url: "/pages/mine/mine" });
    },
  },
};

function flatten(nodes, acc = []) {
  for (const node of nodes || []) {
    acc.push(node);
    if (node.children && node.children.length) {
      flatten(node.children, acc);
    }
  }
  return acc;
}
</script>

<style>
.page { padding: 32rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.name { font-size: 32rpx; }
.empty { color: #64748b; padding: 24rpx 0 40rpx; }
</style>
