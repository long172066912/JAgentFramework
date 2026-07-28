package com.jrl.ai.agent.agentscope.evaluation;

import com.jrl.ai.agent.core.agent.Agent;
import com.jrl.ai.agent.core.agent.AgentInterceptor;
import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.evaluation.*;
import com.jrl.ai.agent.core.io.ChatMessage;
import com.jrl.ai.agent.core.task.ExecutionTrace;
import com.jrl.ai.agent.core.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 评测拦截器 — Agent 执行完成后自动触发评测链。
 *
 * <p>通过配置 {@code jagent.evaluation.enabled=true} 启用，
 * 自动收集所有已注册的 {@link Evaluator} Bean 并依次执行评测。
 */
public class EvaluationInterceptor implements AgentInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EvaluationInterceptor.class);

    private final List<Evaluator> evaluators;
    private final CompositeScorer compositeScorer;
    private final EvaluationStore store;
    private final OptimizationAnalyzer optimizationAnalyzer;
    private final OptimizationReportStore optimizationReportStore;

    /**
     * 创建评测拦截器（不含优化分析）。
     *
     * @param evaluators       所有已注册的评测器
     * @param compositeScorer  复合评分器
     * @param store            评测结果存储
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store) {
        this(evaluators, compositeScorer, store, null, null);
    }

    /**
     * 创建评测拦截器（含优化分析）。
     *
     * @param evaluators              所有已注册的评测器
     * @param compositeScorer         复合评分器
     * @param store                   评测结果存储
     * @param optimizationAnalyzer    优化分析器（可选）
     * @param optimizationReportStore 优化报告存储（可选）
     */
    public EvaluationInterceptor(List<Evaluator> evaluators,
                                 CompositeScorer compositeScorer,
                                 EvaluationStore store,
                                 OptimizationAnalyzer optimizationAnalyzer,
                                 OptimizationReportStore optimizationReportStore) {
        this.evaluators = evaluators;
        this.compositeScorer = compositeScorer;
        this.store = store;
        this.optimizationAnalyzer = optimizationAnalyzer;
        this.optimizationReportStore = optimizationReportStore;
    }

    @Override
    public void afterExecute(Agent agent, ChatMessage input, AgentContext context, TaskResult result) {
        log.info("[Evaluation] afterExecute triggered for agent={}", agent.id());
        long evalStart = System.currentTimeMillis();

        try {
            // 构建评测上下文
            Map<String, Object> taskResultMap = Map.of(
                    "status", result.status() != null ? result.status().name() : "UNKNOWN",
                    "durationMs", result.durationMs()
            );

            EvaluationContext evalContext = new EvaluationContext(
                    agent.id(),
                    result.sessionId(),
                    input != null ? input.content() : null,
                    result.result() != null ? String.valueOf(result.result()) : null,
                    result.trace(),
                    taskResultMap,
                    Map.of()
            );

            // 遍历所有评测器，合并评分，记录每个评测器的执行时间
            Map<EvaluationDimension, DimensionScore> allScores = new EnumMap<>(EvaluationDimension.class);
            List<ExecutionTrace.Step> evalSteps = new ArrayList<>();

            for (Evaluator evaluator : evaluators) {
                long stepStart = System.currentTimeMillis();
                try {
                    EvaluationResult evalResult = evaluator.evaluate(evalContext);
                    long stepDuration = System.currentTimeMillis() - stepStart;
                    allScores.putAll(evalResult.scores());
                    
                    // 构建详细的 step 信息
                    String stepDetail = buildEvalStepDetail(evaluator, evalResult);
                    evalSteps.add(new ExecutionTrace.Step(
                            "EVAL_" + evaluator.getClass().getSimpleName().toUpperCase(),
                            stepDuration,
                            stepDetail
                    ));
                } catch (Exception e) {
                    long stepDuration = System.currentTimeMillis() - stepStart;
                    log.warn("[Evaluation] Evaluator {} failed: {}",
                            evaluator.getClass().getSimpleName(), e.getMessage());
                    evalSteps.add(new ExecutionTrace.Step(
                            "EVAL_" + evaluator.getClass().getSimpleName().toUpperCase() + "_FAILED",
                            stepDuration,
                            "error=" + e.getMessage()
                    ));
                }
            }

            // 计算加权总分
            long scoreStart = System.currentTimeMillis();
            double compositeScore = compositeScorer.compute(allScores);
            long scoreDuration = System.currentTimeMillis() - scoreStart;
            evalSteps.add(new ExecutionTrace.Step(
                    "COMPOSITE_SCORE",
                    scoreDuration,
                    String.format("score=%.2f,dims=%d", compositeScore, allScores.size())
            ));

            // 构建包含评测步骤的新链路
            ExecutionTrace originalTrace = result.trace();
            List<ExecutionTrace.Step> allSteps = new ArrayList<>();
            if (originalTrace != null) {
                allSteps.addAll(originalTrace.steps());
            }
            allSteps.addAll(evalSteps);
            long totalEvalTime = System.currentTimeMillis() - evalStart;
            ExecutionTrace enrichedTrace = new ExecutionTrace(List.copyOf(allSteps),
                    originalTrace != null ? originalTrace.totalTime() + totalEvalTime : totalEvalTime);

            // 将评测步骤存储到上下文中，供适配器自动合并到主链路
            context.put("jagent.evaluation.steps", evalSteps);
            context.put("jagent.evaluation.time", totalEvalTime);

            // 构建最终评测结果
            EvaluationResult finalResult = EvaluationResult.builder(agent.id())
                    .sessionId(result.sessionId())
                    .scores(allScores)
                    .compositeScore(compositeScore)
                    .trace(enrichedTrace)
                    .input(input != null ? input.content() : null)
                    .output(result.result() != null ? String.valueOf(result.result()) : null)
                    .build();

            // 持久化
            store.save(finalResult);

            log.info("[Evaluation] agent={} composite={} dims={} evalTime={}ms",
                    agent.id(), String.format("%.2f", compositeScore), allScores.size(), totalEvalTime);

            // 触发优化分析（如果配置了优化分析器）
            if (optimizationAnalyzer != null && optimizationReportStore != null) {
                long optStart = System.currentTimeMillis();
                try {
                    OptimizationReport report = optimizationAnalyzer.analyze(finalResult, evalContext);
                    optimizationReportStore.save(report);
                    long optDuration = System.currentTimeMillis() - optStart;
                    log.info("[Optimization] agent={} suggestions={} time={}ms",
                            agent.id(), report.suggestions().size(), optDuration);
                } catch (Exception ex) {
                    long optDuration = System.currentTimeMillis() - optStart;
                    log.warn("[Optimization] Failed to analyze agent={}: {} (time={}ms)",
                            agent.id(), ex.getMessage(), optDuration);
                }
            }

        } catch (Exception e) {
            log.error("[Evaluation] Failed to evaluate agent={}: {}", agent.id(), e.getMessage(), e);
        }
    }

    /**
     * 构建评测步骤的详细信息字符串。
     *
     * @param evaluator  评测器
     * @param evalResult 评测结果
     * @return 详细信息字符串
     */
    private String buildEvalStepDetail(Evaluator evaluator, EvaluationResult evalResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("dims=").append(evalResult.scores().size());
        
        // 如果是 LLM 评测器，添加模型信息
        if (evaluator instanceof LLMJudgeEvaluator llmEvaluator) {
            try {
                String modelName = llmEvaluator.getJudgeModel().modelId();
                sb.append(",model=").append(modelName);
            } catch (Exception e) {
                // 忽略获取模型名失败
            }
        }
        
        return sb.toString();
    }
}
