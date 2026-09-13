<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NCard, NButton, NSpace, useMessage } from "naive-ui";
import DocumentTable from "@/components/kb/DocumentTable.vue";
import UploadDialog from "@/components/kb/UploadDialog.vue";
import { useDocumentPolling } from "@/composables/useDocumentPolling";

const route = useRoute();
const router = useRouter();
const message = useMessage();

const kbId = Number(route.params.id);
const kbIdRef = ref(kbId);
const showUpload = ref(false);
const { documents, isLoading, startPolling } = useDocumentPolling(kbIdRef);

onMounted(startPolling);

function handleUploaded() {
  startPolling();
}

function goBack() {
  router.push("/kb");
}

function startChat() {
  router.push({ path: "/chat", query: { kbId } });
}
</script>

<template>
  <div class="detail-page">
    <NCard :title="'文档管理'" class="detail-card">
      <template #header-extra>
        <NSpace>
          <NButton size="small" @click="goBack">返回</NButton>
          <NButton type="primary" size="small" @click="startChat"
            >开始问答</NButton
          >
          <NButton size="small" @click="showUpload = true">上传文档</NButton>
        </NSpace>
      </template>
      <DocumentTable :documents="documents" @refresh="startPolling" />
      <p v-if="documents.length === 0 && !isLoading" class="empty">
        暂无文档，请上传
      </p>
    </NCard>
    <UploadDialog
      v-if="showUpload"
      :kb-id="kbId"
      :show="showUpload"
      @close="showUpload = false"
      @uploaded="handleUploaded"
    />
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
}
.detail-card {
  min-height: 400px;
}
.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>
