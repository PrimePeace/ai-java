import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
  ChatSession,
  ChatMessage,
  CreateSessionRequest,
} from "@/types/ai";

/** 创建会话 */
export function createSessionApi(
  data: CreateSessionRequest,
): Promise<BaseResponse<ChatSession>> {
  return request.post("/chat/session/create", data);
}

/** 会话列表 */
export function listSessionsApi(
  kbId?: number,
): Promise<BaseResponse<ChatSession[]>> {
  return request.get("/chat/session/list", { params: kbId ? { kbId } : {} });
}

/** 历史消息 */
export function listMessagesApi(
  sessionId: number,
): Promise<BaseResponse<ChatMessage[]>> {
  return request.get(`/chat/session/${sessionId}/messages`);
}

/** 删除会话 */
export function deleteSessionApi(
  sessionId: number,
): Promise<BaseResponse<null>> {
  return request.delete(`/chat/session/${sessionId}`);
}
