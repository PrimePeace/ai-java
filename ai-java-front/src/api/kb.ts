import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
  CreateKbRequest,
  UpdateKbRequest,
  KnowledgeBase,
  KnowledgeDocument,
} from "@/types/ai";

/** 创建知识库 */
export function createKbApi(
  data: CreateKbRequest,
): Promise<BaseResponse<KnowledgeBase>> {
  return request.post("/kb/create", data);
}

/** 我的知识库列表 */
export function listKbsApi(): Promise<BaseResponse<KnowledgeBase[]>> {
  return request.get("/kb/list");
}

/** 修改知识库 */
export function updateKbApi(
  data: UpdateKbRequest,
): Promise<BaseResponse<null>> {
  return request.post("/kb/update", data);
}

/** 删除知识库 */
export function deleteKbApi(id: number): Promise<BaseResponse<null>> {
  return request.delete(`/kb/${id}`);
}

/** 上传文档 */
export function uploadDocumentApi(
  kbId: number,
  file: File,
): Promise<BaseResponse<number>> {
  const formData = new FormData();
  formData.append("file", file);
  return request.post(`/kb/${kbId}/document/upload`, formData, {
    timeout: 120000,
  });
}

/** 文档列表 */
export function listDocumentsApi(
  kbId: number,
): Promise<BaseResponse<KnowledgeDocument[]>> {
  return request.get(`/kb/${kbId}/document/list`);
}

/** 删除文档 */
export function deleteDocumentApi(docId: number): Promise<BaseResponse<null>> {
  return request.delete(`/kb/document/${docId}`);
}
