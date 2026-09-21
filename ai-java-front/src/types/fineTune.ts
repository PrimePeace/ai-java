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
  [JobStatus.SUBMITTING]: "提交中",
  [JobStatus.TRAINING]: "训练中",
  [JobStatus.SUCCEEDED]: "已成功",
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

export interface FineTuneJob {
  id: number;
  datasetId: number;
  baseModel: string;
  modelName: string;
  zhipuJobId: string;
  zhipuModelId: string;
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
  ragAnswer: string | null;
  ftAnswer: string | null;
  ragScore: number | null;
  ftScore: number | null;
  autoScoreRag: number | null;
  autoScoreFt: number | null;
  evaluatorComment: string | null;
  createTime: string;
  updateTime: string;
}

export interface EvaluationSummary {
  total: number;
  scoredCount: number;
  avgRagScore: number;
  avgFtScore: number;
  ftWins: number;
  ragWins: number;
  ties: number;
}

/** 智谱可微调基座（与已购资源包对齐：默认 glm-4-flash） */
export const BASE_MODEL_OPTIONS = [
  { label: "glm-4-flash（已购资源包 / LoRA·全参）", value: "glm-4-flash" },
  { label: "glm-4-air-250414", value: "glm-4-air-250414" },
  { label: "glm-4.5-air", value: "glm-4.5-air" },
];

/** 创建微调任务时的默认基座 */
export const DEFAULT_BASE_MODEL = "glm-4-flash";
