import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
    PromptTemplate,
    CreatePromptRequest,
    UpdatePromptRequest,
} from "@/types/ai";

/** 创建提示词模板 */
export function createPromptApi(
    data: CreatePromptRequest,
): Promise<BaseResponse<PromptTemplate>> {
    return request.post("/prompt/create", data);
}

/** 我的提示词模板列表 */
export function listPromptsApi(): Promise<BaseResponse<PromptTemplate[]>> {
    return request.get("/prompt/list");
}

/** 修改提示词模板 */
export function updatePromptApi(
    data: UpdatePromptRequest,
): Promise<BaseResponse<null>> {
    return request.post("/prompt/update", data);
}

/** 删除提示词模板 */
export function deletePromptApi(id: number): Promise<BaseResponse<null>> {
    return request.delete(`/prompt/${id}`);
}
