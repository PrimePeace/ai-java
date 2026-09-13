import { ref, type Ref } from "vue";
import type { Citation } from "@/types/ai";

export interface UseChatStreamOptions {
  onMessage: (delta: string) => void;
  onCitations: (citations: Citation[]) => void;
  onEnd: () => void;
  onError: (error: string) => void;
}

export interface UseChatStreamReturn {
  send: (sessionId: number, question: string) => Promise<void>;
  abort: () => void;
  isStreaming: Ref<boolean>;
  assistantContent: Ref<string>;
}

/**
 * SSE 流式解析（POST + fetch + ReadableStream）
 * 遵循 SSE 规范：多行 data: 合并 \n，\r\n 兼容，忽略注释行/id:/retry:
 */
export function useChatStream(
  options: UseChatStreamOptions,
): UseChatStreamReturn {
  const isStreaming = ref(false);
  const assistantContent = ref("");
  let abortController: AbortController | undefined;

  async function send(sessionId: number, question: string) {
    abortController = new AbortController();
    isStreaming.value = true;
    assistantContent.value = "";

    const token = localStorage.getItem("accessToken") || "";
    const response = await fetch(`/api/chat/session/${sessionId}/send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ question }),
      signal: abortController.signal,
    });

    const contentType = response.headers.get("content-type") || "";
    // 业务异常常以 HTTP 200 + application/json 返回，不能当 SSE 解析
    if (!response.ok || contentType.includes("application/json")) {
      let errMsg = `HTTP ${response.status}`;
      try {
        const json = await response.json();
        errMsg = json.message || errMsg;
      } catch {
        // 忽略
      }
      isStreaming.value = false;
      if (options.onError) options.onError(errMsg);
      return;
    }

    const reader = response.body?.getReader();
    if (!reader) {
      isStreaming.value = false;
      if (options.onError) options.onError("无法读取响应流");
      return;
    }

    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let sep;
        while ((sep = buffer.indexOf("\n\n")) !== -1) {
          const frame = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          const parsed = parseFrame(frame);
          if (parsed) {
            consumeEvent(parsed.event, parsed.data, options);
          }
        }
      }
    } catch (e: any) {
      if (e.name === "AbortError") return;
      isStreaming.value = false;
      if (options.onError) options.onError(e.message || "流读取异常");
    } finally {
      isStreaming.value = false;
    }
  }

  function abort() {
    abortController?.abort();
    isStreaming.value = false;
  }

  return { send, abort, isStreaming, assistantContent };
}

function parseFrame(frame: string): { event: string; data: string } | null {
  const trimmed = frame.trim();
  if (!trimmed) return null;
  let event = "message";
  const dataLines: string[] = [];
  for (const line of trimmed.split(/\r?\n/)) {
    if (
      line.startsWith(":") ||
      line.startsWith("id:") ||
      line.startsWith("retry:")
    ) {
      continue;
    }
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    }
  }
  if (dataLines.length === 0 && event !== "end") {
    if (event === "end") return { event, data: "" };
    return null;
  }
  return { event, data: dataLines.join("\n") };
}

function consumeEvent(
  event: string,
  data: string,
  options: UseChatStreamOptions,
) {
  switch (event) {
    case "message":
      if (options.onMessage) options.onMessage(data);
      break;
    case "citations":
      try {
        const citations = JSON.parse(data) as Citation[];
        if (options.onCitations) options.onCitations(citations);
      } catch {
        // 忽略
      }
      break;
    case "end":
      if (options.onEnd) options.onEnd();
      break;
    case "error":
      if (options.onError) options.onError(data);
      break;
  }
}
