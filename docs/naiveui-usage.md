# Naive UI 使用文档

> 本文档基于 Naive UI 官方网站内容整理。
> - 介绍页：<https://www.naiveui.com/zh-CN/light/docs/introduction>
> - Avatar 组件页：<https://www.naiveui.com/zh-CN/light/components/avatar>

---

## 一、Naive UI 简介

![Naive UI 介绍页](./naiveui-introduction.png)

Naive UI 是一个 **Vue 3 的组件库**，由图森未来（TuSimple）团队开发维护，使用 MIT 许可证书。

### 核心特性

| 特性 | 说明 |
|------|------|
| **比较完整** | 超过 90 个组件，全面覆盖中后台常见场景，全部支持 treeshaking |
| **主题可调** | 提供基于 TypeScript 的类型安全主题系统，只需提供样式覆盖对象即可。**不需要** less / sass / css 变量 / webpack loaders |
| **使用 TypeScript** | 全量使用 TypeScript 编写，与 TS 项目无缝衔接。**不需要** 导入任何 CSS 就能让组件正常工作 |
| **快** | Select、Tree、Transfer、Table、Cascader 等均支持虚拟列表 |

### 安装

详细安装步骤参见 [安装文档](https://www.naiveui.com/zh-CN/light/docs/installation)。基本使用：

```bash
# npm
npm i -D naive-ui

# yarn
yarn add -D naive-ui

# pnpm
pnpm add -D naive-ui
```

### 基础使用

```ts
// main.ts
import { create } from 'naive-ui'

const naive = create({
  // 主题配置（可选）
})

app.use(naive)
```

### 字体依赖

Naive UI 默认使用 `vfonts` 字体，按需安装：

```bash
npm i -D vfonts
```

### 社区资源

- GitHub：<https://github.com/tusen-ai/naive-ui>
- Discord：<https://discord.gg/Pqv7Mev5Dd>
- 图标库：<https://www.xicons.org>
- 设计资源：[Sketch 设计稿](https://naive-ui.oss-accelerate.aliyuncs.com/NaiveUI-Design-Library-zh-CN.sketch)

---

## 二、Avatar 头像组件

![Avatar 组件页](./naiveui-avatar.png)

> 在互联网上，没有人知道你是 *** 。

Avatar 用于代表用户或实体，可展示图片、文字、图标。

### 1. 基础用法

```vue
<script setup lang="ts">
import { NAvatar } from 'naive-ui'
</script>

<template>
  <NAvatar src="https://example.com/avatar.png" />
</template>
```

### 2. 尺寸（size）

内置 `small` / `medium` / `large` 三档，也支持自定义数字像素值。

```vue
<NAvatar size="small" src="..." />
<NAvatar size="medium" src="..." />
<NAvatar size="large" src="..." />
<NAvatar :size="64" src="..." />
```

| 取值 | 说明 |
|------|------|
| `small` | 小尺寸 |
| `medium` | 默认尺寸 |
| `large` | 大尺寸 |
| `number` | 自定义像素大小 |

### 3. 形状（round）

默认为方形，设置 `round` 为圆形。

```vue
<NAvatar round src="..." />
```

### 4. 颜色（color）

设置头像背景色，常用于纯文字或图标头像。

```vue
<NAvatar color="#18a058">N</NAvatar>
```

### 5. 图标头像

通过默认插槽填充图标。

```vue
<NAvatar>
  <NIcon><UserIcon /></NIcon>
</NAvatar>
```

### 6. 字号自适应

当内容为文字时，字号会根据头像尺寸和文字长度自动调整。

```vue
<NAvatar>Oasis</NAvatar>
```

### 7. 加载失败处理（fallback）

图片加载失败时，可指定备用图片或自定义渲染。

```vue
<!-- 备用图片地址 -->
<NAvatar
  src="可能失效的图片地址"
  fallback-src="https://07akioni.oss-cn-beijing.aliyuncs.com/07akioni.jpeg"
/>

<!-- 自定义渲染函数 -->
<NAvatar
  src="可能失效的图片地址"
  :render-fallback="() => h('span', 'N')"
/>
```

### 8. 头像组（AvatarGroup）

多个头像堆叠显示，可控制最大数量。

```vue
<script setup lang="ts">
import { NAvatar, NAvatarGroup } from 'naive-ui'
</script>

<template>
  <NAvatarGroup :max="3" :size="40">
    <NAvatar src="user1.png" />
    <NAvatar src="user2.png" />
    <NAvatar src="user3.png" />
    <NAvatar src="user4.png" />
    <NAvatar src="user5.png" />
  </NAvatarGroup>
  <!-- 渲染 3 个头像 + "+2" 溢出标识 -->
</template>
```

**泛型组件 `NGAvatarGroup`（推荐，自 2.43.0）**

```vue
<!-- Vue >= 3.3 才能用 -->
<script setup lang="ts">
import { NGAvatarGroup } from 'naive-ui/generic'
</script>
```

> 说明：`NGAvatarGroup` 与 `NAvatarGroup` 功能基本相同，差异在于它提供 generic `options` prop，让 slots 和 props 在 `.vue` 文件中类型更精确。如果不支持 Vue 泛型组件，请用 `NAvatarGroup`。

### 9. 懒加载（lazy）

两种方式：

```vue
<!-- 方式一：原生 loading 属性 -->
<NAvatar lazy src="..." />

<!-- 方式二：配合 IntersectionObserver 配置 -->
<NAvatar
  lazy
  :intersection-observer-options="{
    root: null,
    rootMargin: '0px',
    threshold: 0.1
  }"
  src="..."
/>
```

### 10. 与 Badge 组合

```vue
<NBadge :value="999" :max="99">
  <NAvatar src="..." />
</NBadge>
```

---

## 三、Avatar API 参考

### Avatar Props

| 名称 | 类型 | 默认值 | 说明 | 版本 |
|------|------|--------|------|------|
| `bordered` | `boolean` | `false` | 头像是否带边框 | - |
| `color` | `string` | `undefined` | 头像的背景色 | - |
| `fallback-src` | `string` | `undefined` | 加载失败时显示的图片地址 | - |
| `img-props` | `ImgHTMLAttributes` | `undefined` | 组件中 img 元素的属性 | 2.34.0 |
| `intersection-observer-options` | `{ root?, rootMargin?, threshold? }` | `undefined` | `lazy=true` 时 IntersectionObserver 的观测配置 | 2.31.0 |
| `lazy` | `boolean` | `false` | 是否懒加载（原生属性或配合 observer） | 2.31.0 |
| `object-fit` | `'fill' \| 'contain' \| 'cover' \| 'none' \| 'scale-down'` | `'fill'` | 图片在容器内的适应类型 | - |
| `render-fallback` | `() => VNodeChild` | `undefined` | 加载失败的渲染函数 | 2.33.4 |
| `render-placeholder` | `() => VNodeChild` | `undefined` | 占位的渲染函数 | 2.33.4 |
| `round` | `boolean` | `false` | 头像是否圆形 | - |
| `size` | `'small' \| 'medium' \| 'large' \| number` | `'medium'` | 头像的尺寸 | - |
| `src` | `string` | `undefined` | 头像图片地址 | - |
| `on-error` | `(e: Event) => void` | `undefined` | 图片加载失败时的回调 | - |

### Avatar Slots

| 名称 | 参数 | 说明 | 版本 |
|------|------|------|------|
| `default` | `()` | 头像内填充的内容 | - |
| `fallback` | `()` | 加载失败的内容 | 2.33.4 |
| `placeholder` | `()` | 图像未加载完成时的占位 | 2.31.0 |

---

## 四、AvatarGroup API 参考

> 泛型 `<T extends AvatarGroupOption = AvatarGroupOption>`，自 `2.43.0` 起可用。
>
> ```ts
> interface AvatarGroupOption {
>   src: string
> }
> ```

### AvatarGroup Props

| 名称 | 类型 | 默认值 | 说明 | 版本 |
|------|------|--------|------|------|
| `expand-on-hover` | `boolean` | `false` | 悬停时展开 | 2.37.0 |
| `max` | `number` | `undefined` | 组内头像显示的最大个数 | - |
| `max-style` | `Object \| string` | `undefined` | 溢出标识的样式 | - |
| `options` | `Array<T>` | `[]` | 头像组的选项（泛型组件） | - |
| `size` | `'small' \| 'medium' \| 'large' \| number` | `'medium'` | 头像尺寸 | 2.43.0 |
| `vertical` | `boolean` | `false` | 组内头像是否垂直排列 | - |

### AvatarGroup Slots

| 名称 | 参数 | 说明 |
|------|------|------|
| `avatar` | `(info: { option: { src: string } })` | 头像组头像 |
| `default` | `()` | 头像组内填充的内容 |
| `rest` | `(info: { options: Array<{ src: string }>, rest: number })` | 头像组溢出容器 |

---

## 五、最佳实践提示

1. **类型安全**：在 `.vue` 文件中且 Vue ≥ 3.3 时优先使用 `NGAvatarGroup`（从 `naive-ui/generic` 导入），可获得精确的 slots/props 类型推导。
2. **懒加载推荐**：长列表场景务必开启 `lazy`，配合 `intersection-observer-options` 控制触发时机。
3. **失败兜底**：线上场景建议同时配置 `fallback-src` 与 `on-error`，避免图片源失效导致空白。
4. **treeshaking**：按需 `import { NAvatar } from 'naive-ui'` 即可，未使用的组件不会进入打包产物。
