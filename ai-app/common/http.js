import { BASE_URL } from "./config.js";

export function getToken() {
  return uni.getStorageSync("accessToken") || "";
}

export function getRefreshToken() {
  return uni.getStorageSync("refreshToken") || "";
}

export function setTokens(accessToken, refreshToken) {
  uni.setStorageSync("accessToken", accessToken);
  uni.setStorageSync("refreshToken", refreshToken);
}

export function clearTokens() {
  uni.removeStorageSync("accessToken");
  uni.removeStorageSync("refreshToken");
  uni.removeStorageSync("user");
}

function requestOnce(options) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || "GET",
      data: options.data,
      header: {
        "Content-Type": "application/json",
        Authorization: getToken() ? `Bearer ${getToken()}` : "",
      },
      success: (res) => {
        const body = res.data || {};
        if (body.code === 0) {
          resolve(body);
          return;
        }
        reject(body);
      },
      fail: (err) => reject(err),
    });
  });
}

export async function request(options) {
  try {
    return await requestOnce(options);
  } catch (error) {
    if (error && (error.code === 40102 || error.code === 40100 || error.code === 40103)) {
      const refreshed = await refresh();
      if (refreshed) {
        return requestOnce(options);
      }
      clearTokens();
      uni.reLaunch({ url: "/pages/login/login" });
    }
    const message = (error && error.message) || "请求失败";
    return Promise.reject(new Error(message));
  }
}

async function refresh() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }
  try {
    const body = await requestOnce({
      url: "/user/refresh",
      method: "POST",
      data: { refreshToken },
    });
    setTokens(body.data.accessToken, body.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

export function ensureLogin() {
  if (!getToken()) {
    uni.reLaunch({ url: "/pages/login/login" });
    return false;
  }
  return true;
}
