<template>
  <view class="page">
    <view class="row">
      <input v-model="name" class="input" placeholder="新知识库名称" />
      <button size="mini" type="primary" @click="create">创建</button>
    </view>
    <view v-for="kb in list" :key="kb.id" class="card">
      <text class="name" @click="open(kb)">{{ kb.name }}</text>
      <text class="del" @click="remove(kb)">删除</text>
    </view>
  </view>
</template>

<script>
import { ensureLogin, request } from "../../common/http.js";

export default {
  data() {
    return { name: "", list: [] };
  },
  onShow() {
    if (ensureLogin()) {
      this.load();
    }
  },
  methods: {
    async load() {
      const res = await request({ url: "/kb/list" });
      this.list = res.data || [];
    },
    async create() {
      if (!this.name.trim()) {
        return;
      }
      await request({ url: "/kb/create", method: "POST", data: { name: this.name.trim(), description: "" } });
      this.name = "";
      this.load();
    },
    open(kb) {
      uni.navigateTo({ url: `/pages/kb/detail?id=${kb.id}&name=${encodeURIComponent(kb.name)}` });
    },
    remove(kb) {
      uni.showModal({
        title: "删除知识库",
        content: kb.name,
        success: async (res) => {
          if (!res.confirm) {
            return;
          }
          await request({ url: `/kb/${kb.id}`, method: "DELETE" });
          this.load();
        },
      });
    },
  },
};
</script>

<style>
.page { padding: 32rpx; }
.row { display: flex; gap: 16rpx; margin-bottom: 24rpx; align-items: center; }
.input { flex: 1; border: 1px solid #e5e7eb; border-radius: 12rpx; padding: 16rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 16rpx; display: flex; justify-content: space-between; }
.name { font-size: 30rpx; }
.del { color: #dc2626; }
</style>
