import { watch, type Ref } from "vue";

function isScrollable(el: HTMLElement) {
  const overflow = getComputedStyle(el).overflowY;
  return (
    (overflow === "auto" || overflow === "scroll") &&
    el.scrollHeight > el.clientHeight + 1
  );
}

/** 从当前节点向上，把所有纵向滚动容器拉到最底部。 */
function scrollToBottom(start: HTMLElement) {
  let el: HTMLElement | null = start;
  while (el) {
    if (isScrollable(el)) {
      el.scrollTop = el.scrollHeight;
    }
    el = el.parentElement;
  }
}

/**
 * 消息条数或流式正文变化后，把最新内容留在可视区域底部。
 */
export function useFollowBottom(
  listRef: Ref<HTMLElement | null>,
  source: () => unknown,
) {
  watch(
    source,
    () => {
      requestAnimationFrame(() => {
        const el = listRef.value;
        if (el) scrollToBottom(el);
      });
    },
    { flush: "post" },
  );
}
