<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import {
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NInput,
  NInputNumber,
  NRate,
  NSpace,
  useMessage,
} from "naive-ui";
import type { EvaluationRecord, EvaluationSummary } from "@/types/fineTune";
import {
  evaluationSummaryApi,
  listEvaluationsApi,
  runEvaluationApi,
  scoreEvaluationApi,
} from "@/api/fineTune";

const props = defineProps<{ kbId: number }>();
const message = useMessage();

const records = ref<EvaluationRecord[]>([]);
const summary = ref<EvaluationSummary | null>(null);
const questionsText = ref("");
const maxExtractCount = ref(10);
const running = ref(false);

const hasPending = computed(() =>
  records.value.some((r) => r.ragAnswer === null || r.ftAnswer === null),
);

async function load() {
  try {
    const [listRes, sumRes] = await Promise.all([
      listEvaluationsApi(props.kbId),
      evaluationSummaryApi(props.kbId),
    ]);
    records.value = listRes.data;
    summary.value = sumRes.data;
  } catch (e: any) {
    message.error(e.message || "评测记录加载失败");
  }
}

async function handleRun() {
  const questions = questionsText.value
    .split("\n")
    .map((q) => q.trim())
    .filter((q) => q.length > 0);
  running.value = true;
  try {
    await runEvaluationApi(props.kbId, {
      questions: questions.length > 0 ? questions : undefined,
      maxExtractCount: maxExtractCount.value,
    });
    message.success("评测已发起，正在并行执行双链路");
    questionsText.value = "";
    await load();
  } catch (e: any) {
    message.error(e.message || "评测发起失败");
  } finally {
    running.value = false;
  }
}

async function handleScore(r: EvaluationRecord, field: "rag" | "ft", score: number) {
  try {
    await scoreEvaluationApi(
      r.id,
      field === "rag" ? score : r.ragScore,
      field === "ft" ? score : r.ftScore,
    );
    if (field === "rag") r.ragScore = score;
    else r.ftScore = score;
    message.success("评分已保存");
    const sumRes = await evaluationSummaryApi(props.kbId);
    summary.value = sumRes.data;
  } catch (e: any) {
    message.error(e.message || "评分失败");
  }
}

// 单套轮询：存在未完成的评测记录时每 5s 刷新
let timer: ReturnType<typeof setInterval> | null = null;
watch(hasPending, (v) => {
  if (v && !timer) {
    timer = setInterval(load, 5000);
  } else if (!v && timer) {
    clearInterval(timer);
    timer = null;
  }
});
watch(() => props.kbId, load, { immediate: true });
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div>
    <div class="panel-bar">
      <NInput
          v-model:value="questionsText"
          type="textarea"
          placeholder="每行一个测试问题；留空则自动从历史对话抽取"
          :autosize="{ minRows: 2, maxRows: 5 }"
          style="flex: 1"
      />
      <NSpace vertical size="small" style="margin-left: 12px">
        <NInputNumber v-model:value="maxExtractCount" :min="1" :max="50" size="small" />
        <NButton type="primary" size="small" :loading="running" @click="handleRun">
          发起评测
        </NButton>
      </NSpace>
    </div>
    <p v-if="summary && summary.total > 0" class="summary">
      共 {{ summary.total }} 条 · 已评分 {{ summary.scoredCount }} 条 ·
      RAG 均分 {{ summary.avgRagScore }} · 微调均分 {{ summary.avgFtScore }} ·
      微调胜 {{ summary.ftWins }} / RAG 胜 {{ summary.ragWins }} / 平 {{ summary.ties }}
    </p>
    <p v-if="records.length === 0" class="empty">暂无评测记录</p>
    <NCollapse v-else>
      <NCollapseItem
          v-for="r in records"
          :key="r.id"
          :title="r.question"
          :name="r.id"
      >
        <div class="answers">
          <div class="answer-col">
            <p class="answer-title">RAG 链路（自动 {{ r.autoScoreRag ?? "-" }}）</p>
            <p class="answer-text">{{ r.ragAnswer ?? "生成中..." }}</p>
            <NRate
                :value="r.ragScore ?? 0"
                @update:value="(v: number) => handleScore(r, 'rag', v)"
            />
          </div>
          <div class="answer-col">
            <p class="answer-title">微调模型（自动 {{ r.autoScoreFt ?? "-" }}）</p>
            <p class="answer-text">{{ r.ftAnswer ?? "生成中..." }}</p>
            <NRate
                :value="r.ftScore ?? 0"
                @update:value="(v: number) => handleScore(r, 'ft', v)"
            />
          </div>
        </div>
        <p v-if="r.evaluatorComment" class="comment">裁判评语：{{ r.evaluatorComment }}</p>
      </NCollapseItem>
    </NCollapse>
  </div>
</template>

<style scoped>
.panel-bar {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
}
.summary {
  font-size: 12px;
  color: #64748b;
  margin: 0 0 12px;
}
.answers {
  display: flex;
  gap: 16px;
}
.answer-col {
  flex: 1;
  min-width: 0;
}
.answer-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  margin: 0 0 4px;
}
.answer-text {
  font-size: 13px;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0 0 8px;
}
.comment {
  font-size: 12px;
  color: #94a3b8;
  margin: 8px 0 0;
}
.empty {
  text-align: center;
  color: #999;
  padding: 30px 0;
}
</style>
