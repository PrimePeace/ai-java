import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
  EvaluationRecord,
  EvaluationSummary,
  FineTuneDataset,
  FineTuneJob,
} from "@/types/fineTune";

/** 生成问答对数据集 */
export function generateDatasetApi(data: {
  kbId: number;
  name: string;
  description?: string;
  qaPerChunk?: number;
}): Promise<BaseResponse<FineTuneDataset>> {
  return request.post("/fine-tune/dataset/generate", data);
}

/** KB 下数据集列表 */
export function listDatasetsApi(
  kbId: number,
): Promise<BaseResponse<FineTuneDataset[]>> {
  return request.get(`/fine-tune/dataset/list/${kbId}`);
}

/** 删除数据集 */
export function deleteDatasetApi(id: number): Promise<BaseResponse<null>> {
  return request.delete(`/fine-tune/dataset/${id}`);
}

/** 创建风格生成任务（从数据集蒸馏回答风格） */
export function createJobApi(data: {
  datasetId: number;
  /** 参与蒸馏的抽样条数：不传=使用全部样本 */
  sampleLimit?: number;
}): Promise<BaseResponse<FineTuneJob>> {
  return request.post("/fine-tune/job/create", data);
}

/** 数据集下任务列表 */
export function listJobsApi(
  datasetId: number,
): Promise<BaseResponse<FineTuneJob[]>> {
  return request.get(`/fine-tune/job/list/${datasetId}`);
}

/** 取消风格任务 */
export function cancelJobApi(id: number): Promise<BaseResponse<null>> {
  return request.post(`/fine-tune/job/${id}/cancel`);
}

/** 删除风格任务 */
export function deleteJobApi(id: number): Promise<BaseResponse<null>> {
  return request.delete(`/fine-tune/job/${id}`);
}

/** 发起评测（questions 为空则后端从历史对话抽取） */
export function runEvaluationApi(
  kbId: number,
  data: {
    questions?: string[];
    maxExtractCount?: number;
  },
): Promise<BaseResponse<EvaluationRecord[]>> {
  return request.post(`/fine-tune/evaluation/run/${kbId}`, data);
}

/** 评测列表 */
export function listEvaluationsApi(
  kbId: number,
): Promise<BaseResponse<EvaluationRecord[]>> {
  return request.get(`/fine-tune/evaluation/list/${kbId}`);
}

/** 手动评分 */
export function scoreEvaluationApi(
  id: number,
  ragScore?: number | null,
  styleScore?: number | null,
): Promise<BaseResponse<null>> {
  return request.post(`/fine-tune/evaluation/score/${id}`, null, {
    params: { ragScore: ragScore ?? undefined, styleScore: styleScore ?? undefined },
  });
}

/** 评测汇总 */
export function evaluationSummaryApi(
  kbId: number,
): Promise<BaseResponse<EvaluationSummary>> {
  return request.get(`/fine-tune/evaluation/summary/${kbId}`);
}
