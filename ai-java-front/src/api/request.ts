import axios from "axios";
import type {
  AxiosInstance,
  InternalAxiosRequestConfig,
  AxiosResponse,
} from "axios";
import type { Router } from "vue-router";
import type { BaseResponse } from "@/types/user";
import { SUCCESS_CODE } from "@/types/user";
import { useUserStore } from "@/stores/user";

// 错误码分类
const TOKEN_EXPIRED_CODE = 40102;
const TOKEN_INVALID_CODE = 40103;
const NOT_LOGIN_CODE = 40100;

// 防止并发刷新 token 时重复调用
let isRefreshing = false;
// 因 token 过期而挂起的请求队列
let pendingQueue: Array<() => void> = [];

let router: Router;

const request: AxiosInstance = axios.create({
  baseURL: "/api",
  timeout: 10000,
});

/**
 * 初始化拦截器（需要传入 router 实例）
 */
export function setupInterceptors(r: Router) {
  router = r;

  // 请求拦截器：附加 Authorization header
  request.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const token = localStorage.getItem("accessToken");
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error),
  );

  // 响应拦截器：统一处理业务码
  request.interceptors.response.use(
    (response: AxiosResponse<BaseResponse<unknown>>) => {
      const res = response.data;
      // 业务成功
      if (res.code === SUCCESS_CODE) {
        return res as any;
      }
      return Promise.reject(new Error(res.message || "请求失败"));
    },
    async (error) => {
      const originalRequest = error.config;
      const res = error.response?.data as BaseResponse<unknown> | undefined;
      const errorCode = res?.code;

      // Token 过期：尝试刷新
      if (errorCode === TOKEN_EXPIRED_CODE && !originalRequest._retry) {
        if (isRefreshing) {
          // 正在刷新中，将请求加入队列
          return new Promise((resolve) => {
            pendingQueue.push(() => {
              resolve(request(originalRequest));
            });
          });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const userStore = useUserStore();
          await userStore.refreshTokens();
          // 重试队列
          pendingQueue.forEach((cb) => cb());
          pendingQueue = [];
          return request(originalRequest);
        } catch {
          // 刷新失败，清除 token 跳登录
          handleAuthFailure();
          return Promise.reject(
            new Error(res?.message || "登录已过期，请重新登录"),
          );
        } finally {
          isRefreshing = false;
        }
      }

      // Token 无效 / 未登录：清除 token 跳登录
      if (errorCode === TOKEN_INVALID_CODE || errorCode === NOT_LOGIN_CODE) {
        handleAuthFailure();
        return Promise.reject(new Error(res?.message || "请先登录"));
      }

      return Promise.reject(new Error(res?.message || "网络异常"));
    },
  );
}

function handleAuthFailure() {
  const userStore = useUserStore();
  userStore.logout();
  if (router) {
    router.push({
      name: "login",
      query: { redirect: router.currentRoute.value.fullPath },
    });
  }
}

export default request;
