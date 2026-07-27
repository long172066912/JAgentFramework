package com.jrl.ai.agent.core.skill;

/**
 * Skill 执行拦截器 — 在技能执行的前置、后置、环绕阶段插入自定义逻辑。
 *
 * <p>典型用途包括：反馈采集、执行日志、权限校验、耗时监控等。
 * 所有方法均提供默认空实现，实现方按需覆写。
 *
 * <p>环绕方法 {@link #aroundExecute} 可控制是否继续执行：
 * <ul>
 *   <li>调用 {@code chain.proceed()} 继续执行</li>
 *   <li>不调用则短路返回自定义结果</li>
 * </ul>
 *
 * @see Skill
 */
public interface SkillInterceptor {

    /**
     * 技能执行前调用（前置通知）。
     *
     * @param skill   即将执行的技能
     * @param context 执行上下文
     */
    default void beforeExecute(Skill skill, SkillContext context) {}

    /**
     * 技能执行成功后调用（后置通知）。
     *
     * @param skill   已执行完成的技能
     * @param context 执行上下文
     * @param result  执行结果
     */
    default void afterExecute(Skill skill, SkillContext context, SkillResult result) {}

    /**
     * 技能执行异常时调用（异常通知）。
     *
     * @param skill   执行失败的技能
     * @param context 执行上下文
     * @param error   异常信息
     */
    default void onError(Skill skill, SkillContext context, Throwable error) {}

    /**
     * 技能执行环绕通知 — 可控制是否继续执行下游链路。
     *
     * <p>默认实现直接调用 {@code chain.proceed()}。
     *
     * @param skill   目标技能
     * @param context 执行上下文
     * @param chain   执行链，调用 {@code proceed()} 继续执行
     * @return 执行结果
     */
    default SkillResult aroundExecute(Skill skill, SkillContext context, ExecutionChain chain) {
        return chain.proceed(context);
    }

    /**
     * 执行链抽象 — 代表拦截器链中的下一个环节。
     */
    @FunctionalInterface
    interface ExecutionChain {
        /**
         * 继续执行下游链路。
         *
         * @param context 执行上下文
         * @return 执行结果
         */
        SkillResult proceed(SkillContext context);
    }
}
