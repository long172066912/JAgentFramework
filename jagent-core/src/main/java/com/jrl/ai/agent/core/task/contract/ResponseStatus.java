package com.jrl.ai.agent.core.task.contract;

/**
 * 响应状态
 */
public enum ResponseStatus {
    /** 处理中（中间状态，可多次发送） */
    PROCESSING,
    /** 处理成功（终态） */
    SUCCESS,
    /** 处理失败（终态） */
    FAIL,
    /** 执行超时（终态） */
    TIMEOUT
}
