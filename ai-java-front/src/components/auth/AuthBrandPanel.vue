<script setup lang="ts">
import { NIcon } from "naive-ui";
import {
  ChatbubblesOutline,
  CloudUploadOutline,
  DocumentTextOutline,
  SparklesOutline,
} from "@vicons/ionicons5";

interface Feature {
  icon: typeof SparklesOutline;
  title: string;
  desc: string;
}

const features: Feature[] = [
  {
    icon: CloudUploadOutline,
    title: "文档管理",
    desc: "上传 PDF / Word / Markdown，自动解析切分",
  },
  {
    icon: DocumentTextOutline,
    title: "语义检索",
    desc: "向量化 RAG 检索，精准定位答案出处",
  },
  {
    icon: ChatbubblesOutline,
    title: "智能问答",
    desc: "基于知识库的流式问答，附引用溯源",
  },
];
</script>

<template>
  <div class="brand-panel">
    <div class="brand-head">
      <span class="brand-logo">
        <NIcon :size="20"><SparklesOutline /></NIcon>
      </span>
      <span class="brand-name">AI 知识库</span>
    </div>

    <div class="brand-slogan">
      <h1>让知识<br />触手可及</h1>
      <p>企业级 RAG 知识库问答平台</p>
    </div>

    <ul class="brand-features">
      <li v-for="f in features" :key="f.title">
        <span class="feature-icon">
          <NIcon :size="18"><component :is="f.icon" /></NIcon>
        </span>
        <div class="feature-text">
          <h4>{{ f.title }}</h4>
          <p>{{ f.desc }}</p>
        </div>
      </li>
    </ul>

    <p class="brand-footer">© 2026 AI Knowledge Base</p>
  </div>
</template>

<style scoped>
.brand-panel {
  position: relative;
  width: 42%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 40px 48px;
  box-sizing: border-box;
  background: var(--brand-gradient);
  color: #ffffff;
  overflow: hidden;
}

/* 装饰光斑 */
.brand-panel::before,
.brand-panel::after {
  content: "";
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.35;
  pointer-events: none;
}
.brand-panel::before {
  width: 300px;
  height: 300px;
  top: -80px;
  right: -100px;
  background: #a78bfa;
}
.brand-panel::after {
  width: 260px;
  height: 260px;
  bottom: -60px;
  left: -80px;
  background: #38bdf8;
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(4px);
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-slogan {
  margin: 48px 0;
}

.brand-slogan h1 {
  margin: 0;
  font-size: 40px;
  line-height: 1.25;
  font-weight: 700;
}

.brand-slogan p {
  margin: 16px 0 0;
  font-size: 15px;
  opacity: 0.85;
}

.brand-features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(4px);
}

.feature-icon {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.16);
}

.feature-text h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.feature-text p {
  margin: 2px 0 0;
  font-size: 12px;
  opacity: 0.8;
}

.brand-footer {
  margin: 48px 0 0;
  font-size: 12px;
  opacity: 0.6;
}
</style>
