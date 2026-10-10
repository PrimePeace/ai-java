import request from "./request";
import type { BaseResponse, UserVO } from "@/types/user";
import type { RoleVO } from "@/types/menu";

export function adminUserListApi(): Promise<BaseResponse<UserVO[]>> {
  return request.get("/admin/user/list");
}

export function adminUserStatusApi(data: {
  userId: number;
  status: number;
}): Promise<BaseResponse<null>> {
  return request.post("/admin/user/status", data);
}

export function adminAssignRoleApi(data: {
  userId: number;
  roleId: number;
}): Promise<BaseResponse<null>> {
  return request.post("/admin/user/assign-role", data);
}

export function roleListApi(): Promise<BaseResponse<RoleVO[]>> {
  return request.get("/role/list");
}

export function roleUpdateApi(data: {
  id: number;
  name: string;
  remark?: string;
}): Promise<BaseResponse<null>> {
  return request.post("/role/update", data);
}

export function roleAssignMenuApi(data: {
  roleId: number;
  menuIds: number[];
}): Promise<BaseResponse<null>> {
  return request.post("/role/assign-menu", data);
}

export interface AuditLogRow {
  id: number;
  username?: string;
  operationType: string;
  operationModule?: string;
  description: string;
  ipAddress?: string;
  result: string;
  createTime: string;
}

export interface AuditPage {
  records: AuditLogRow[];
  totalRow: number;
  pageNumber: number;
  pageSize: number;
}

export function auditListApi(current: number, pageSize: number): Promise<BaseResponse<AuditPage>> {
  return request.get("/audit/list", { params: { current, pageSize } });
}
