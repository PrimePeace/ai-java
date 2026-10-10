<template>
  <view class="page">
    <picker :range="kbNames" @change="onPick">
      <view class="picker">知识库：{{ currentName || "请选择" }}</view>
    </picker>
    <scroll-view scroll-y class="messages">
      <view v-for="(msg, index) in messages" :key="index" class="msg">
        <text class="role">{{ msg.role === "user" ? "我" : "回答" }}</text>
        <text class="content">{{ msg.content }}</text>
      </view>
    </scroll-view>
    <view class="bar">
      <input v-model="question" class="input" placeholder="输入问题" />
      <button size="mini" type="primary" @click="send">发送</button>
    </view>
  </view>
</template>

<script>
import { BASE_URL } from "../../common/config.js";
import { ensureLogin, getToken, request } from "../../common/http.js";

export default {
  data() {
    return { kbs: [], kbIndex: 0, sessionId: null, messages: [], question: "" };
  },
  computed: {
    kbNames() {
      return this.kbs.map((kb) => kb.name);
    },
    currentName() {
      return this.kbs[this.kbIndex] ? this.kbs[this.kbIndex].name : "";
    },
  },
  onShow() {
    if (ensureLogin()) {
      this.loadKbs();
    }
  },
  methods: {
    async loadKbs() {
      const res = await request({ url: "/kb/list" });
      this.kbs = res.data || [];
      this.sessionId = null;
      this.messages = [];
    },
    onPick(event) {
      this.kbIndex = Number(event.detail.value);
      this.sessionId = null;
      this.messages = [];
    },
    async send() {
      const kb = this.kbs[this.kbIndex];
      const question = this.question.trim();
      if (!kb || !question) {
        return;
      }
      if (!this.sessionId) {
        const created = await request({
          url: "/chat/session/create",
          method: "POST",
          data: { kbId: kb.id },
        });
        this.sessionId = created.data.id;
      }
      this.messages.push({ role: "user", content: question });
      this.question = "";
      const answer = { role: "assistant", content: "" };
      this.messages.push(answer);
      await streamAnswer(this.sessionId, question, (text) => {
        answer.content += text;
      });
    },
  },
};

function streamAnswer(sessionId, question, onText) {
  return new Promise((resolve) => {
    let buffer = "";
    const task = uni.request({
      url: `${BASE_URL}/chat/session/${sessionId}/send`,
      method: "POST",
      enableChunked: true,
      header: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${getToken()}`,
      },
      data: { question },
      success: (res) => {
        if (!buffer && typeof res.data === "string") {
          consume(res.data, onText);
        }
        resolve();
      },
      fail: () => resolve(),
    });
    if (task && task.onChunkReceived) {
      task.onChunkReceived((chunk) => {
        const text = decodeChunk(chunk.data);
        buffer += text;
        const parts = buffer.split("\n\n");
        buffer = parts.pop() || "";
        parts.forEach((part) => consume(part, onText));
      });
    }
  });
}

function consume(block, onText) {
  const lines = block.split("\n");
  let event = "message";
  const data = [];
  for (const line of lines) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      data.push(line.slice(5).trim());
    }
  }
  if (event === "message" && data.length) {
    onText(data.join("\n"));
  }
}

function decodeChunk(data) {
  if (typeof data === "string") {
    return data;
  }
  if (typeof TextDecoder !== "undefined") {
    return new TextDecoder("utf-8").decode(data);
  }
  return "";
}
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.picker { padding: 24rpx; background: #fff; }
.messages { flex: 1; padding: 24rpx; }
.msg { margin-bottom: 20rpx; }
.role { color: #64748b; font-size: 24rpx; display: block; }
.content { font-size: 30rpx; white-space: pre-wrap; }
.bar { display: flex; gap: 12rpx; padding: 16rpx; background: #fff; }
.input { flex: 1; border: 1px solid #e5e7eb; border-radius: 12rpx; padding: 12rpx; }
</style>
