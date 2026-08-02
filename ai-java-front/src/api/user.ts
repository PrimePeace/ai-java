import request from "./request";
import type {
  BaseResponse,
  UserVO,
  UserLoginVO,
  UserLoginRequest,
  UserRegisterRequest,
  RefreshTokenRequest,
} from "@/types/user";

/**
 * 用户注册
 */
export function registerApi(
  data: UserRegisterRequest,
): Promise<BaseResponse<UserVO>> {
  return request.post("/user/register", data);
}

/**
 * 用户登录
 */
export function loginApi(
  data: UserLoginRequest,
): Promise<BaseResponse<UserLoginVO>> {
  return request.post("/user/login", data);
}

/**
 * 刷新 Token
 */
export function refreshTokenApi(
  data: RefreshTokenRequest,
): Promise<BaseResponse<UserLoginVO>> {
  return request.post("/user/refresh", data);
}

/**
 * 获取当前用户信息
 */
export function getCurrentUserApi(): Promise<BaseResponse<UserVO>> {
  return request.get("/user/current");
}
