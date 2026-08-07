package com.jrl.ai.agent.demo.tagging.skill;

import com.jrl.ai.agent.core.skill.Skill;
import com.jrl.ai.agent.core.skill.SkillContext;
import com.jrl.ai.agent.core.skill.SkillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类目层级推断 Skill — 根据标签类目名称推断标签层级。
 *
 * <p>层级映射可通过构造函数配置，支持动态扩展。
 * 默认映射：
 * <ul>
 *   <li>视觉风格、情感氛围 → 一级标签</li>
 *   <li>场景用途、材质工艺 → 二级标签</li>
 *   <li>其他 → 三级标签</li>
 * </ul>
 */
public class CategoryLevelSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(CategoryLevelSkill.class);

    /** 类目 → 层级映射（key 为类目名，value 为层级） */
    private final Map<String, Integer> categoryLevelMapping;

    /** 默认层级（未匹配时使用） */
    private final int defaultLevel;

    /**
     * 创建带默认映射的 Skill。
     */
    public CategoryLevelSkill() {
        this(Map.of(
                "视觉风格", 1,
                "情感氛围", 1,
                "场景用途", 2,
                "材质工艺", 2,
                "颜色配色", 3,
                "主题元素", 3
        ), 3);
    }

    /**
     * 创建自定义映射的 Skill。
     *
     * @param categoryLevelMapping 类目 → 层级映射
     * @param defaultLevel         默认层级
     */
    public CategoryLevelSkill(Map<String, Integer> categoryLevelMapping, int defaultLevel) {
        this.categoryLevelMapping = categoryLevelMapping;
        this.defaultLevel = defaultLevel;
    }

    @Override
    public String name() {
        return "category_level_infer";
    }

    @Override
    public String description() {
        return "根据标签类目名称推断标签层级（1/2/3）。输入参数：category（类目名称）。";
    }

    @Override
    public Set<String> applicableAgents() {
        // 静态挂载边界：类目层级推断是打标场景专属能力，只对 tagger 可见
        return Set.of("tagger");
    }

    @Override
    public List<String> keywords() {
        return List.of("类目层级", "标签级别推断");
    }

    @Override
    public String whenToUse() {
        return "已确定标签的类目名称、需要据此推断其层级（1/2/3）时调用";
    }

    @Override
    public String whenNotToUse() {
        return "仅凭抽取结果即可直接确定层级、或任务不要求输出层级时，不要调用本工具";
    }

    @Override
    public double priority() {
        return 0.8;
    }

    @Override
    public SkillResult execute(SkillContext context) {
        long start = System.currentTimeMillis();

        String category = (String) context.parameters().getOrDefault("category", "");
        if (category.isEmpty()) {
            log.warn("[CategoryLevel] category 参数为空，使用默认层级 {}", defaultLevel);
            return SkillResult.success(name(), String.valueOf(defaultLevel), System.currentTimeMillis() - start);
        }

        int level = categoryLevelMapping.getOrDefault(category, defaultLevel);
        log.debug("[CategoryLevel] category={} → level={}", category, level);

        return SkillResult.success(name(), String.valueOf(level), System.currentTimeMillis() - start);
    }
}
