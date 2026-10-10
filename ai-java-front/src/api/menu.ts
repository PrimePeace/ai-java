import request from "./request";
import type { BaseResponse } from "@/types/user";
import type { MenuNode, MenuSaveRequest } from "@/types/menu";

export function menuNavApi(client: "web" | "app"): Promise<BaseResponse<MenuNode[]>> {
  return request.get("/menu/nav", { params: { client } });
}

export function menuListApi(): Promise<BaseResponse<MenuNode[]>> {
  return request.get("/menu/list");
}

export function menuSaveApi(data: MenuSaveRequest): Promise<BaseResponse<null>> {
  return request.post("/menu/save", data);
}
