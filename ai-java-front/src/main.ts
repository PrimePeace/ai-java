import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import "./styles/global.css";
import { setupInterceptors } from "./api/request";
import { useUserStore } from "./stores/user";
import { useMenuStore } from "./stores/menu";

const app = createApp(App);

app.use(createPinia());
app.use(router);

setupInterceptors(router);
useMenuStore().bindRouter(router);

const userStore = useUserStore();
await userStore.initAuth();
if (userStore.accessToken) {
  await useMenuStore().load();
}

app.mount("#app");
