<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import type { EChartsType } from "echarts/core";
import { NEmpty } from "naive-ui";

// 按需注册，减小打包体积
echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

interface ChartItem {
  name: string;
  value: number;
}

const props = defineProps<{ data: ChartItem[] }>();

const chartRef = ref<HTMLDivElement | null>(null);
let chart: EChartsType | null = null;
let retryTimer: number | null = null;
let retryCount = 0;

async function renderChart() {
  if (props.data.length === 0) return;
  await nextTick();
  if (!chartRef.value) return;
  if (chartRef.value.clientWidth === 0 || chartRef.value.clientHeight === 0) {
    if (retryCount >= 20) return;
    retryCount += 1;
    if (retryTimer) window.clearTimeout(retryTimer);
    retryTimer = window.setTimeout(renderChart, 80);
    return;
  }
  retryCount = 0;
  if (!chart) {
    chart = echarts.init(chartRef.value);
  }
  chart.setOption({
    grid: { left: 8, right: 8, top: 28, bottom: 0, containLabel: true },
    tooltip: { trigger: "axis" },
    xAxis: {
      type: "category",
      data: props.data.map((i) => i.name),
      axisLabel: {
        color: "#64748b",
        // 名称过长时截断
        formatter: (v: string) => (v.length > 6 ? v.slice(0, 6) + "…" : v),
      },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: "#94a3b8" },
      splitLine: { lineStyle: { color: "#eef0f6" } },
    },
    series: [
      {
        type: "bar",
        name: "文档数",
        data: props.data.map((i) => i.value),
        barWidth: 28,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "#8b5cf6" },
            { offset: 1, color: "#6366f1" },
          ]),
        },
      },
    ],
  });
}

function handleResize() {
  chart?.resize();
}

onMounted(() => {
  renderChart();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  if (retryTimer) window.clearTimeout(retryTimer);
  window.removeEventListener("resize", handleResize);
  chart?.dispose();
  chart = null;
});

watch(() => props.data, renderChart);
</script>

<template>
  <NEmpty
    v-if="data.length === 0"
    description="暂无知识库数据"
    class="chart-empty"
  />
  <div v-if="data.length > 0" ref="chartRef" class="chart-box" />
</template>

<style scoped>
.chart-box {
  height: 280px;
  width: 100%;
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
