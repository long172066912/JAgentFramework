package com.jrl.ai.agent.core.task;

import com.jrl.ai.agent.core.evaluation.trace.TraceSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行链路追踪 — 记录 Agent 执行过程中每个步骤的耗时与状态。
 *
 * <p>通用设计，不绑定具体业务场景。任何 Agent 执行均可通过
 * {@link Builder} 逐步记录执行链路，最终生成不可变的 Trace 对象。
 *
 * <p>链路分两层：
 * <ul>
 *   <li>{@code steps} — 业务视角的执行步骤（扁平、按业务语义命名）</li>
 *   <li>{@code otel} — 可观测性视角的链路快照（span 树 + 多维分析，评测时刻冻结）</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 * ExecutionTrace.Builder builder = ExecutionTrace.builder();
 * builder.step("LLM_CALL", 1523, "model=qwen3.7-flash");
 * builder.step("PARSE", 2, "parsed=5 items");
 * ExecutionTrace trace = builder.build();
 * }</pre>
 *
 * @param steps     各执行步骤
 * @param totalTime 总耗时（ms）
 * @param otel      OTel 链路快照（可选，含 span 树与多维分析）
 */
public record ExecutionTrace(
        List<Step> steps,
        long totalTime,
        TraceSnapshot otel
) {

    /**
     * 创建无链路快照的执行链路（兼容构造器）。
     *
     * @param steps     各执行步骤
     * @param totalTime 总耗时（ms）
     */
    public ExecutionTrace(List<Step> steps, long totalTime) {
        this(steps, totalTime, null);
    }
    /**
     * 执行步骤。
     *
     * @param name     步骤名称（建议大写蛇形，如 LLM_CALL）
     * @param duration 耗时（ms）
     * @param detail   步骤详情（可选）
     */
    public record Step(String name, long duration, String detail) {}

    /**
     * 创建 Builder。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 执行链路追踪 Builder — 支持逐步记录执行过程。
     */
    public static class Builder {
        private final List<Step> steps = new ArrayList<>();
        private long startTime = -1;
        private TraceSnapshot otel;

        Builder() {}

        /**
         * 记录开始时间。
         */
        public Builder start() {
            this.startTime = System.currentTimeMillis();
            return this;
        }

        /**
         * 记录一个执行步骤。
         *
         * @param name     步骤名称
         * @param duration 耗时（ms）
         * @param detail   步骤详情
         */
        public Builder step(String name, long duration, String detail) {
            steps.add(new Step(name, duration, detail));
            return this;
        }

        /**
         * 记录一个执行步骤（无详情）。
         */
        public Builder step(String name, long duration) {
            return step(name, duration, null);
        }

        /**
         * 记录一个执行步骤（从已有 Step 复制）。
         */
        public Builder step(Step step) {
            steps.add(step);
            return this;
        }

        /**
         * 附加 OTel 链路快照（span 树 + 多维分析）。
         */
        public Builder otel(TraceSnapshot otel) {
            this.otel = otel;
            return this;
        }

        /**
         * 构建 ExecutionTrace。
         * 若调用过 {@link #start()}，totalTime 为 start 到当前的时间差。
         */
        public ExecutionTrace build() {
            long total = startTime > 0 ? System.currentTimeMillis() - startTime : 0;
            return new ExecutionTrace(List.copyOf(steps), total, otel);
        }
    }
}
