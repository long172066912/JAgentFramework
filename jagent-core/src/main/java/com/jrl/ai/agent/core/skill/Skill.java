package com.jrl.ai.agent.core.skill;

import java.util.List;
import java.util.Set;

/**
 * Skill — Agent 可具备的技能抽象。
 *
 * <p>Skill 是 Agent 执行能力的封装，每个 Skill 拥有名称、描述和执行逻辑。
 * 通过 {@link SkillRegistry} 注册后可被 Agent 动态调用，
 * 通过 {@link SkillInterceptor} 可在执行前后插入拦截逻辑。
 *
 * @see SkillContext
 * @see SkillResult
 */
public interface Skill {

    /**
     * 获取技能名称。
     *
     * @return 技能唯一名称
     */
    String name();

    /**
     * 获取技能描述（供 LLM 理解技能用途）。
     *
     * @return 技能描述文本
     */
    String description();

    /**
     * 执行技能。
     *
     * @param context 技能执行上下文
     * @return 执行结果
     */
    SkillResult execute(SkillContext context);

    /**
     * 技能是否可用。
     *
     * <p>默认返回 {@code true}，实现方可根据资源状态、
     * 权限等条件判断是否可用。
     *
     * @return 若技能当前可执行则返回 {@code true}
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 挂载边界（静态）— 声明本技能适用于哪些 Agent。
     *
     * <p>Skill 自描述的第一层调用边界：框架挂载工具时按 agentKey 过滤，
     * 边界外的 Agent 根本看不到此工具（不注入工具定义，省 prompt tokens）。
     * 与配置侧 {@code skills} 白名单取交集（双重限制）。
     *
     * <p>默认返回 {@code null}，表示对所有 Agent 开放（兼容存量实现）。
     *
     * @return 适用的 agentKey 集合；null 或空集表示不限制
     */
    default Set<String> applicableAgents() {
        return null;
    }

    /**
     * 调用边界（动态）— 运行时判断当前是否允许执行。
     *
     * <p>Skill 自描述的第二层调用边界：即使工具已挂载且被 LLM 选中，
     * 执行前仍会检查此方法；拒绝时模型会收到提示信息而非真实执行。
     * 实现方可基于会话、用户、参数等上下文条件决策（如仅付费用户、
     * 仅特定执行模式、参数合法性等）。
     *
     * <p>默认返回 {@code true}（不限制）。
     *
     * @param context 技能执行上下文（含 Agent 运行时上下文与调用参数）
     * @return 若允许本次调用则返回 {@code true}
     */
    default boolean canInvoke(SkillContext context) {
        return true;
    }

    /**
     * 调用关键词（LLM 初筛用）— 描述本技能相关的语义锚点。
     *
     * <p>框架会将关键词拼入注入给 LLM 的工具描述，帮助模型在 ReAct
     * 推理中快速判断当前任务是否与本技能相关。例如向量查重技能的
     * 关键词可以是：标签去重、相似检索。
     *
     * <p>默认返回空列表（不注入）。
     *
     * @return 关键词列表
     */
    default List<String> keywords() {
        return List.of();
    }

    /**
     * 适用场景（LLM 初筛用）— 描述什么情况下应该调用本技能。
     *
     * <p>与 {@link #whenNotToUse()} 一起构成 Skill 自描述的调用边界：
     * 不同于 {@code applicableAgents()} 的硬过滤，这里只是提示文本，
     * 由 LLM 自行判断是否调用，适合“何时能调/何时不能调”的软约束。
     *
     * <p>默认返回 {@code null}（不注入）。
     *
     * @return 适用场景描述，或 null
     */
    default String whenToUse() {
        return null;
    }

    /**
     * 禁止场景（LLM 初筛用）— 描述什么情况下不应调用本技能。
     *
     * <p>默认返回 {@code null}（不注入）。
     *
     * @return 禁止场景描述，或 null
     */
    default String whenNotToUse() {
        return null;
    }
}
