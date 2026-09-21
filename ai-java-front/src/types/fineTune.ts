// 与后端 DatasetStatus / JobStatus 枚举名对齐
export enum DatasetStatus {
  GENERATING = "GENERATING",
  READY = "READY",
  FAILED = "FAILED",
}

export enum JobStatus {
  SUBMITTING = "SUBMITTING",
  TRAINING = "TRAINING",
  SUCCEEDED = "SUCCEEDED",
  FAILED = "FAILED",
  CANCELLED = "CANCELLED",
}

export const DATASET_STATUS_LABEL: Record<DatasetStatus, string> = {
  [DatasetStatus.GENERATING]: "生成中",
  [DatasetStatus.READY]: "已就绪",
  [DatasetStatus.FAILED]: "失败",
};

export const JOB_STATUS_LABEL: Record<JobStatus, string> = {
  [JobStatus.SUBMITTING]: "排队中",
  [JobStatus.TRAINING]: "生成中",
  [JobStatus.SUCCEEDED]: "已生成",
  [JobStatus.FAILED]: "失败",
  [JobStatus.CANCELLED]: "已取消",
};

export interface FineTuneDataset {
  id: number;
  kbId: number;
  name: string;
  description: string;
  format: string;
  sampleCount: number;
  status: DatasetStatus;
  errorMessage: string | null;
  createTime: string;
  updateTime: string;
}

/** 风格生成任务：从数据集蒸馏回答风格提示词 */
export interface FineTuneJob {
  id: number;
  datasetId: number;
  /** 蒸馏出的回答风格提示词（成功后写入并绑定到知识库） */
  stylePrompt: string | null;
  status: JobStatus;
  errorMessage: string | null;
  progress: number;
  createTime: string;
  updateTime: string;
}

export interface EvaluationRecord {
  id: number;
  kbId: number;
  question: string;
  /** 纯 RAG 链路回答 */
  ragAnswer: string | null;
  /** RAG+风格 链路回答 */
  styleAnswer: string | null;
  ragScore: number | null;
  styleScore: number | null;
  autoScoreRag: number | null;
  autoScoreStyle: number | null;
  evaluatorComment: string | null;
  createTime: string;
  updateTime: string;
}

export interface EvaluationSummary {
  total: number;
  scoredCount: number;
  avgRagScore: number;
  avgStyleScore: number;
  styleWins: number;
  ragWins: number;
  ties: number;
}
