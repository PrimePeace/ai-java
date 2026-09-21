// 与后端 DocStatus.name() 对齐（DB 存 UPLOADED/PROCESSING/COMPLETED/FAILED）
export enum DocStatus {
  UPLOADED = "UPLOADED",
  PROCESSING = "PROCESSING",
  COMPLETED = "COMPLETED",
  FAILED = "FAILED",
}

export const DOC_STATUS_LABEL: Record<DocStatus, string> = {
  [DocStatus.UPLOADED]: "已上传",
  [DocStatus.PROCESSING]: "处理中",
  [DocStatus.COMPLETED]: "已完成",
  [DocStatus.FAILED]: "失败",
};

export interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
  promptTemplateId: number | null;
  /** 问答引擎：rag=纯 RAG；style=RAG+回答风格；auto=有回答风格走风格增强否则纯 RAG */
  chatEngine: "rag" | "style" | "auto" | null;
  /** 绑定的回答风格提示词（null=未生成/未绑定） */
  stylePrompt: string | null;
  docCount: number;
  createTime: string;
  updateTime: string;
}

export interface KnowledgeDocument {
  id: number;
  kbId: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  status: DocStatus;
  errorMessage: string | null;
  createTime: string;
  updateTime: string;
}

export interface ChatSession {
  id: number;
  kbId: number;
  kbName: string;
  title: string;
  createTime: string;
  updateTime: string;
}

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  content: string;
  citations: Citation[] | null;
  createTime: string;
}

export interface Citation {
  chunkId: number;
  docId: number;
  docName: string;
  chunkIndex: number;
  content: string;
  score: number;
}

// 提示词模板
export interface PromptTemplate {
  id: number;
  name: string;
  description: string;
  systemTemplate: string;
  userTemplate: string;
  createTime: string;
  updateTime: string;
}

export interface CreatePromptRequest {
  name: string;
  description?: string;
  systemTemplate: string;
  userTemplate: string;
}

export interface UpdatePromptRequest extends CreatePromptRequest {
  id: number;
}

// KB 创建/修改/会话创建请求
export interface CreateKbRequest {
  name: string;
  description?: string;
}

export interface UpdateKbRequest {
  id: number;
  name: string;
  description?: string;
  /** null=不修改；0=解绑（默认模板）；>0=绑定该模板 */
  promptTemplateId?: number | null;
  /** 问答引擎：不传=不修改；rag/style/auto */
  chatEngine?: "rag" | "style" | "auto";
  /** 回答风格提示词：不传=不修改；空串=清除 */
  stylePrompt?: string;
}

export interface CreateSessionRequest {
  kbId: number;
}

export interface ChatSendRequest {
  question: string;
}
