import { defineStore } from "pinia";
import { ref } from "vue";
import type { Router } from "vue-router";
import { menuNavApi } from "@/api/menu";
import type { MenuNode } from "@/types/menu";
import { firstVisiblePath, installDynamicRoutes, resetDynamicRoutes } from "@/router/dynamic";

export const useMenuStore = defineStore("menu", () => {
  const tree = ref<MenuNode[]>([]);
  const loaded = ref(false);
  let routerRef: Router | null = null;

  function bindRouter(router: Router) {
    routerRef = router;
  }

  async function load() {
    const res = await menuNavApi("web");
    tree.value = res.data || [];
    if (routerRef) {
      installDynamicRoutes(routerRef, tree.value);
    }
    loaded.value = true;
  }

  function reset() {
    tree.value = [];
    loaded.value = false;
    if (routerRef) {
      resetDynamicRoutes(routerRef);
    }
  }

  function homePath() {
    return firstVisiblePath(tree.value);
  }

  return { tree, loaded, bindRouter, load, reset, homePath };
});
