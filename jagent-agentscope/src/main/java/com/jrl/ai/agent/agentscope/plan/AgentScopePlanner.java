package com.jrl.ai.agent.agentscope.plan;

import com.jrl.ai.agent.core.context.AgentContext;
import com.jrl.ai.agent.core.plan.Goal;
import com.jrl.ai.agent.core.plan.Plan;
import com.jrl.ai.agent.core.plan.PlanStep;
import com.jrl.ai.agent.core.plan.Planner;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * AgentScope 规划器 — 桥接 jagent-core {@link Planner} 与 AgentScope PlanMode。
 *
 * <p>通过构建启用 PlanMode 的 HarnessAgent，将目标描述发送给 LLM 生成执行计划，
 * 解析响应为 {@link Plan} 对象。
 */
public class AgentScopePlanner implements Planner {

    private static final Logger log = LoggerFactory.getLogger(AgentScopePlanner.class);

    private final Path workspace;
    private final String modelRef;

    /**
     * 创建 AgentScope 规划器。
     *
     * @param workspace 工作空间路径
     * @param modelRef  模型引用（格式: "provider:model"）
     */
    public AgentScopePlanner(Path workspace, String modelRef) {
        this.workspace = workspace;
        this.modelRef = modelRef;
    }

    @Override
    public Optional<Plan> plan(Goal goal, AgentContext context, Object state) {
        log.info("开始规划: goal={}", goal.name());

        try {
            // 构建启用 PlanMode 的临时 Agent
            HarnessAgent plannerAgent = HarnessAgent.builder()
                    .agentId("planner_" + goal.name())
                    .name("Planner")
                    .sysPrompt(buildPlannerSysPrompt())
                    .workspace(workspace)
                    .model(modelRef)
                    .maxIters(5)
                    .build();

            // 发送目标描述
            RuntimeContext asCtx = RuntimeContext.builder()
                    .sessionId(context.sessionId())
                    .userId(context.userId())
                    .build();

            String goalDescription = String.format(
                    "目标: %s\n描述: %s\n当前状态: %s\n请制定执行计划。",
                    goal.name(), goal.description(),
                    state != null ? state.toString() : "未知"
            );

            Msg response = plannerAgent.call(new UserMessage(goalDescription), asCtx).block();

            if (response == null) {
                log.warn("规划器未返回响应: goal={}", goal.name());
                return Optional.empty();
            }

            // 解析响应为 Plan（简化：将整个响应作为单步计划）
            Plan plan = Plan.create(goal, java.util.List.of(
                    PlanStep.pending(response.getTextContent(), 0,
                            "goal=" + goal.name(), "goal_achieved")
            ));

            log.info("规划完成: goal={}, steps={}", goal.name(), 1);
            return Optional.of(plan);

        } catch (Exception e) {
            log.error("规划失败: goal={}", goal.name(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean needsReplan(Plan currentPlan, Object state) {
        // 简化实现：如果计划状态为 FAILED 或 NEEDS_REPLAN 则需要重新规划
        return currentPlan.status() == com.jrl.ai.agent.core.plan.PlanStatus.FAILED
                || currentPlan.status() == com.jrl.ai.agent.core.plan.PlanStatus.NEEDS_REPLAN;
    }

    private String buildPlannerSysPrompt() {
        return """
                你是一个执行规划器。根据用户提供的目标，制定详细的执行计划。
                
                输出格式：
                1. 分析当前状态与目标的差距
                2. 列出达成目标所需的步骤（每步包含：动作描述、前置条件、预期结果）
                3. 评估风险和备选方案
                
                请简洁、结构化地输出计划。
                """;
    }
}
