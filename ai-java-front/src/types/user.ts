// 后端统一响应结构
export interface BaseResponse<T> {
  code: number;
  data: T;
  message: string;
}

// 成功响应码
export const SUCCESS_CODE = 0;

// 用户信息
export interface UserVO {
  id: number;
  username: string;
  nickname?: string;
  email?: string;
  phone?: string;
  status: number;
  createTime: string;
}

// 登录响应
export interface UserLoginVO {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserVO;
}

// 登录请求
export interface UserLoginRequest {
  username: string;
  password: string;
}

// 注册请求
export interface UserRegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  phone?: string;
}

// 刷新 Token 请求
export interface RefreshTokenRequest {
  refreshToken: string;
}
