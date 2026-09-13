import { ref, onBeforeUnmount, type Ref } from "vue";
import { listDocumentsApi } from "@/api/kb";
import { DocStatus, type KnowledgeDocument } from "@/types/ai";

/**
 * 文档状态轮询 composable
 * - 3s 轮询，全部终态（已完成/失败）停止
 * - 页面不可见时暂停，可见时立即刷新一次
 */
export function useDocumentPolling(kbId: Ref<number>) {
  const documents = ref<KnowledgeDocument[]>([]);
  const isLoading = ref(false);
  let timer: ReturnType<typeof setInterval> | null = null;
  let isVisible = true;

  async function fetchDocs() {
    isLoading.value = true;
    try {
      const res = await listDocumentsApi(kbId.value);
      documents.value = res.data;
      // 检查是否全部终态
      const allTerminal = documents.value.every(
        (d) =>
          d.status === DocStatus.COMPLETED || d.status === DocStatus.FAILED,
      );
      if (allTerminal) {
        stopPolling();
      }
    } catch {
      // 忽略
    } finally {
      isLoading.value = false;
    }
  }

  function startPolling() {
    stopPolling();
    fetchDocs();
    timer = setInterval(() => {
      if (isVisible) {
        fetchDocs();
      }
    }, 3000);
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  onBeforeUnmount(() => {
    stopPolling();
    document.removeEventListener("visibilitychange", handleVisibilityChange);
  });

  function handleVisibilityChange() {
    isVisible = !document.hidden;
    if (!document.hidden) {
      fetchDocs();
    }
  }

  document.addEventListener("visibilitychange", handleVisibilityChange);

  return { documents, isLoading, startPolling, stopPolling };
}
