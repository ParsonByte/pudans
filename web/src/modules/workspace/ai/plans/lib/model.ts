import type { AiBilling, AiModelTier, AiUsageRatios } from "../../providers/lib/api";
import { MODEL_TIER_OPTIONS, formatNumber } from "../../providers/lib/model";
import { tierLabel } from "../../routing/lib/model";
import type {
  AiPlanSpec,
  PlanDayOfWeek,
  PlanFallbackAction,
  PlanFeature,
  PlanSubjectKind,
  PlanVoucherPeriod,
} from "./api";

export const PLAN_DAY_OF_WEEK_OPTIONS: { value: PlanDayOfWeek; label: string }[] = [
  { value: "MONDAY", label: "周一" },
  { value: "TUESDAY", label: "周二" },
  { value: "WEDNESDAY", label: "周三" },
  { value: "THURSDAY", label: "周四" },
  { value: "FRIDAY", label: "周五" },
  { value: "SATURDAY", label: "周六" },
  { value: "SUNDAY", label: "周日" },
];

export const PLAN_VOUCHER_PERIOD_OPTIONS: {
  value: PlanVoucherPeriod;
  label: string;
}[] = [
  { value: "WEEKLY", label: "每周" },
  { value: "MONTHLY", label: "每月" },
];

export const PLAN_SUBJECT_KIND_OPTIONS: { value: PlanSubjectKind; label: string }[] = [
  { value: "UserGroup", label: "用户组" },
  { value: "User", label: "用户" },
];

export const PLAN_FALLBACK_OPTIONS: { value: PlanFallbackAction; label: string }[] = [
  { value: "DENY", label: "拒绝请求" },
  { value: "DOWNGRADE", label: "降级到指定路由组" },
];

export const PLAN_FEATURE_OPTIONS: { value: PlanFeature; label: string }[] = [
  { value: "CHAT", label: "对话与生成" },
  { value: "KNOWLEDGE", label: "知识库" },
  { value: "AGENT", label: "Agent 运行时" },
  { value: "SUBAGENT", label: "子代理" },
];

export function splitPlanList(value: string | null | undefined): string[] {
  return (value ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function joinPlanList(values: string[] | null | undefined): string {
  return (values ?? []).join(", ");
}

export function planRuntimeSummary(spec: AiPlanSpec): string {
  const concurrency = spec.rateLimit?.maxConcurrency ?? 0;
  const rpm = spec.rateLimit?.maxRpm ?? 0;
  const concurrencyLabel = concurrency > 0 ? String(concurrency) : "不限";
  const rpmLabel = rpm > 0 ? String(rpm) : "不限";
  const fallback = spec.fallback?.onFiveHourExhausted ?? "DENY";
  const fallbackLabel =
    fallback === "DOWNGRADE" ? `降级至 ${spec.fallback?.downgradeRouteGroup || "未配置"}` : "拒绝";
  return `并发 ${concurrencyLabel} · RPM ${rpmLabel} · 5h 用尽${fallbackLabel}`;
}

export type RatioField = {
  key: keyof AiUsageRatios;
  label: string;
  hint: string;
};

export const RATIO_FIELDS: RatioField[] = [
  { key: "input", label: "输入", hint: "每个输入 Token 折算的标准权重。" },
  { key: "output", label: "输出", hint: "每个输出 Token 折算的标准权重。" },
  { key: "cacheRead", label: "缓存读取", hint: "每个缓存读取 Token 折算的标准权重。" },
  { key: "cacheWrite", label: "缓存写入", hint: "每个缓存写入 Token 折算的标准权重。" },
  { key: "imagePerItem", label: "图像", hint: "每张生成图像折算的标准权重。" },
  { key: "videoPerSecond", label: "视频", hint: "每秒视频折算的标准权重。" },
  { key: "audioPerSecond", label: "音频", hint: "每秒音频折算的标准权重。" },
  { key: "speechPerKiloChar", label: "语音", hint: "每千字符语音折算的标准权重。" },
  { key: "toolCall", label: "工具调用", hint: "每次工具调用折算的标准权重。" },
];

const DEFAULT_RATIOS: Required<AiUsageRatios> = {
  input: 1,
  output: 4,
  cacheRead: 0.25,
  cacheWrite: 1.25,
  imagePerItem: 500,
  videoPerSecond: 2000,
  audioPerSecond: 1000,
  speechPerKiloChar: 200,
  toolCall: 100,
};

export const DEFAULT_TIER_MULTIPLIERS: Record<AiModelTier, number> = {
  CHEAP: 0.5,
  MEDIUM: 1,
  HIGH: 3,
  ULTRA: 9,
};

export function planSubjectKindLabel(kind: PlanSubjectKind): string {
  return kind === "User" ? "用户" : "用户组";
}

function planDayOfWeekLabel(day: PlanDayOfWeek | null | undefined): string {
  return PLAN_DAY_OF_WEEK_OPTIONS.find((option) => option.value === day)?.label ?? "周一";
}

function planVoucherPeriodLabel(period: PlanVoucherPeriod | null | undefined): string {
  return PLAN_VOUCHER_PERIOD_OPTIONS.find((option) => option.value === period)?.label ?? "每月";
}

export function formatUnits(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) {
    return "—";
  }
  return `${formatNumber(value, 0)} Token`;
}

export function planWeeklyResetLabel(spec: AiPlanSpec): string {
  const day = planDayOfWeekLabel(spec.quota?.weekly?.resetDayOfWeek);
  const hour = spec.quota?.weekly?.resetHour ?? 0;
  return `${day} ${String(hour).padStart(2, "0")}:00（北京时间）`;
}

export function planVoucherSummary(spec: AiPlanSpec): string {
  const vouchers = spec.vouchers;
  if (!vouchers) {
    return "未配置";
  }
  return `5h ${formatNumber(vouchers.fiveHour ?? 0, 0)} 张 · 周 ${formatNumber(
    vouchers.weekly ?? 0,
    0,
  )} 张 / ${planVoucherPeriodLabel(vouchers.period)}`;
}

export function tierMultiplierLabel(tier: AiModelTier): string {
  return tierLabel(tier) ?? tier;
}

export function billingRatios(billing: AiBilling | undefined): Required<AiUsageRatios> {
  return {
    input: billing?.ratios?.input ?? DEFAULT_RATIOS.input,
    output: billing?.ratios?.output ?? DEFAULT_RATIOS.output,
    cacheRead: billing?.ratios?.cacheRead ?? DEFAULT_RATIOS.cacheRead,
    cacheWrite: billing?.ratios?.cacheWrite ?? DEFAULT_RATIOS.cacheWrite,
    imagePerItem: billing?.ratios?.imagePerItem ?? DEFAULT_RATIOS.imagePerItem,
    videoPerSecond: billing?.ratios?.videoPerSecond ?? DEFAULT_RATIOS.videoPerSecond,
    audioPerSecond: billing?.ratios?.audioPerSecond ?? DEFAULT_RATIOS.audioPerSecond,
    speechPerKiloChar: billing?.ratios?.speechPerKiloChar ?? DEFAULT_RATIOS.speechPerKiloChar,
    toolCall: billing?.ratios?.toolCall ?? DEFAULT_RATIOS.toolCall,
  };
}

export function billingTierMultipliers(
  billing: AiBilling | undefined,
): Record<AiModelTier, number> {
  const result = { ...DEFAULT_TIER_MULTIPLIERS };
  for (const option of MODEL_TIER_OPTIONS) {
    const configured = billing?.tierMultipliers?.[option.value];
    if (configured != null && Number.isFinite(configured)) {
      result[option.value] = configured;
    }
  }
  return result;
}

export function billingModelMultipliers(billing: AiBilling | undefined): [string, number][] {
  return Object.entries(billing?.modelMultipliers ?? {})
    .filter(([, value]) => value != null && Number.isFinite(value))
    .sort(([left], [right]) => left.localeCompare(right));
}
