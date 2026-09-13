import type { GlobalThemeOverrides } from "naive-ui";

/**
 * 品牌主题：indigo → violet 渐变 AI 风格
 * 通过 NConfigProvider 的 themeOverrides 生效，禁止 CSS 覆盖组件内部样式
 */
export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "#6366f1",
    primaryColorHover: "#4f46e5",
    primaryColorPressed: "#4338ca",
    primaryColorSuppl: "#8b5cf6",
    borderRadius: "8px",
    borderRadiusSmall: "6px",
    bodyColor: "#f5f6fa",
    cardColor: "#ffffff",
  },
  Card: {
    borderRadius: "12px",
  },
  Button: {
    fontWeight: "500",
  },
};

/** 品牌渐变（横幅 / 头像 / 图标底色复用） */
export const BRAND_GRADIENT =
  "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)";

/** 品牌主色（echarts / 自定义元素用） */
export const BRAND_COLORS = {
  indigo: "#6366f1",
  violet: "#8b5cf6",
  cyan: "#06b6d4",
};
