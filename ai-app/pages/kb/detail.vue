<template>
  <view class="page">
    <button type="primary" @click="choose">上传文档</button>
    <view v-for="doc in docs" :key="doc.id" class="card">
      <text class="name">{{ doc.fileName }}</text>
      <text class="status">{{ doc.status }} {{ doc.errorMessage || "" }}</text>
    </view>
  </view>
</template>

<script>
import { BASE_URL } from "../../common/config.js";
import { ensureLogin, getToken, request } from "../../common/http.js";

export default {
  data() {
    return { kbId: "", docs: [], timer: null };
  },
  onLoad(query) {
    this.kbId = query.id;
    if (query.name) {
      uni.setNavigationBarTitle({ title: decodeURIComponent(query.name) });
    }
  },
  onShow() {
    if (!ensureLogin()) {
      return;
    }
    this.load();
    this.timer = setInterval(this.load, 3000);
  },
  onHide() {
    clearInterval(this.timer);
  },
  onUnload() {
    clearInterval(this.timer);
  },
  methods: {
    async load() {
      const res = await request({ url: `/kb/${this.kbId}/document/list` });
      this.docs = res.data || [];
    },
    choose() {
      uni.chooseFile({
        count: 1,
        extension: [".pdf", ".docx", ".md", ".txt"],
        success: (res) => {
          const file = res.tempFiles[0];
          uni.uploadFile({
            url: `${BASE_URL}/kb/${this.kbId}/document/upload`,
            filePath: file.path,
            name: "file",
            header: { Authorization: `Bearer ${getToken()}` },
            success: () => this.load(),
            fail: () => uni.showToast({ title: "上传失败", icon: "none" }),
          });
        },
        fail: () => uni.showToast({ title: "未能选择文件", icon: "none" }),
      });
    },
  },
};
</script>

<style>
.page { padding: 32rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-top: 16rpx; }
.name { display: block; font-size: 30rpx; }
.status { color: #64748b; font-size: 24rpx; }
</style>
