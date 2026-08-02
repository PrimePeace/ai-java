import { defineStore } from "pinia";
import { ref } from "vue";
import {
  loginApi,
  registerApi,
  refreshTokenApi,
  getCurrentUserApi,
} from "@/api/user";
import type {
  UserVO,
  UserLoginVO,
  UserLoginRequest,
  UserRegisterRequest,
} from "@/types/user";

export const useUserStore = defineStore("user", () => {
  const accessToken = ref<string>(localStorage.getItem("accessToken") || "");
  const refreshToken = ref<string>(localStorage.getItem("refreshToken") || "");
  const user = ref<UserVO | null>(null);

  /**
   * 登录
   */
  async function login(req: UserLoginRequest) {
    const res = await loginApi(req);
    const data = res.data as UserLoginVO;
    setTokens(data.accessToken, data.refreshToken);
    user.value = data.user;
  }

  /**
   * 注册
   */
  async function register(req: UserRegisterRequest) {
    return registerApi(req);
  }

  /**
   * 退出登录
   */
  function logout() {
    clearTokens();
    user.value = null;
  }

  /**
   * 获取当前用户信息
   */
  async function fetchCurrentUser() {
    if (!accessToken.value) return;
    try {
      const res = await getCurrentUserApi();
      user.value = res.data as UserVO;
    } catch {
      clearTokens();
      user.value = null;
    }
  }

  /**
   * 刷新 Token（内部调用，不改变外部 state）
   */
  async function refreshTokens() {
    if (!refreshToken.value) throw new Error("No refresh token");
    const res = await refreshTokenApi({ refreshToken: refreshToken.value });
    const data = res.data as UserLoginVO;
    setTokens(data.accessToken, data.refreshToken);
    if (data.user) {
      user.value = data.user;
    }
  }

  /**
   * 初始化认证状态（页面刷新时从 localStorage 恢复）
   */
  async function initAuth() {
    if (accessToken.value) {
      await fetchCurrentUser();
    }
  }

  function setTokens(access: string, refresh: string) {
    accessToken.value = access;
    refreshToken.value = refresh;
    localStorage.setItem("accessToken", access);
    localStorage.setItem("refreshToken", refresh);
  }

  function clearTokens() {
    accessToken.value = "";
    refreshToken.value = "";
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
  }

  return {
    accessToken,
    refreshToken,
    user,
    login,
    register,
    logout,
    fetchCurrentUser,
    refreshTokens,
    initAuth,
  };
});
