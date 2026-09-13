import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import "./styles/global.css";
import { setupInterceptors } from "./api/request";
import { useUserStore } from "./stores/user";

const app = createApp(App);

app.use(createPinia());
app.use(router);

// 初始化 HTTP 拦截器（需要 router 实例用于自动跳转）
setupInterceptors(router);

// 页面刷新时恢复认证状态
const userStore = useUserStore();
await userStore.initAuth();

app.mount("#app");
