package com.westflow.processruntime.support;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 流程设计器受控公式的统一执行入口，避免不同运行态各自维护一套函数表。
 */
public final class WorkflowFormulaEvaluator {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Pattern VARIABLE_REFERENCE_PATTERN =
            Pattern.compile("(?<![\\w\"'])\\$([A-Za-z_][\\w.]*)(?![\\w\"])");
    private static final List<FunctionMetadata> FUNCTION_METADATA = List.of(
            new FunctionMetadata(
                    "ifElse",
                    "基础函数",
                    "条件分支函数，条件为真时返回第二个参数，否则返回第三个参数。",
                    List.of(
                            new FunctionParameterMetadata("condition", "条件表达式", "boolean", true),
                            new FunctionParameterMetadata("whenTrue", "条件为真时返回的值", "any", true),
                            new FunctionParameterMetadata("whenFalse", "条件为假时返回的值", "any", true)
                    ),
                    "ifElse($days > 3, true, false)"
            ),
            new FunctionMetadata(
                    "contains",
                    "基础函数",
                    "判断字符串或集合中是否包含目标值。",
                    List.of(
                            new FunctionParameterMetadata("target", "被判断的字符串或集合", "string|collection", true),
                            new FunctionParameterMetadata("needle", "待查找的值", "any", true)
                    ),
                    "contains($roleNames, 'HR')"
            ),
            new FunctionMetadata(
                    "daysBetween",
                    "基础函数",
                    "计算两个日期之间相差的天数。",
                    List.of(
                            new FunctionParameterMetadata("left", "左侧日期", "date|string", true),
                            new FunctionParameterMetadata("right", "右侧日期", "date|string", true)
                    ),
                    "daysBetween($startDate, $endDate)"
            ),
            new FunctionMetadata(
                    "isBlank",
                    "基础函数",
                    "判断值是否为空白。",
                    List.of(
                            new FunctionParameterMetadata("value", "待判断的值", "any", true)
                    ),
                    "isBlank($comment)"
            ),
            new FunctionMetadata(
                    "isLongLeave",
                    "业务函数",
                    "根据请假天数判断是否属于长假，默认阈值为 3 天及以上。",
                    List.of(
                            new FunctionParameterMetadata("days", "请假天数", "number", true)
                    ),
                    "isLongLeave($days)"
            )
    );

    private WorkflowFormulaEvaluator() {
    }

    public static Object execute(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        registerFunctionsIfNeeded();
        return AviatorEvaluator.execute(normalizeExpression(expression), variables, true);
    }

    public static Expression compile(String expression) {
        registerFunctionsIfNeeded();
        return AviatorEvaluator.compile(normalizeExpression(expression), true);
    }

    public static void validateExpression(String expression) {
        compile(expression);
    }

    public static List<FunctionMetadata> functionMetadata() {
        return FUNCTION_METADATA;
    }

    public static String engineName() {
        return "Aviator";
    }

    public static String engineVersion() {
        return AviatorEvaluator.VERSION;
    }

    public static String normalizeExpression(String expression) {
        String normalized = expression.trim();
        if (normalized.startsWith("${") && normalized.endsWith("}")) {
            normalized = normalized.substring(2, normalized.length() - 1).trim();
        }
        return VARIABLE_REFERENCE_PATTERN.matcher(normalized).replaceAll("$1");
    }

    private static void registerFunctionsIfNeeded() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        AviatorEvaluator.addFunction(new IfElseFunction());
        AviatorEvaluator.addFunction(new ContainsFunction());
        AviatorEvaluator.addFunction(new DaysBetweenFunction());
        AviatorEvaluator.addFunction(new IsBlankFunction());
        AviatorEvaluator.addFunction(new IsLongLeaveFunction());
    }

    private static final class IfElseFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "ifElse";
        }

        @Override
        public AviatorObject call(
                Map<String, Object> env,
                AviatorObject condition,
                AviatorObject whenTrue,
                AviatorObject whenFalse
        ) {
            return FunctionUtils.getBooleanValue(condition, env) ? whenTrue : whenFalse;
        }
    }

    private static final class ContainsFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "contains";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject target, AviatorObject needle) {
            Object targetValue = target.getValue(env);
            Object needleValue = needle.getValue(env);
            if (targetValue == null || needleValue == null) {
                return AviatorBoolean.FALSE;
            }
            if (targetValue instanceof Collection<?> collection) {
                return AviatorBoolean.valueOf(
                        collection.stream().anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(needleValue)))
                );
            }
            return AviatorBoolean.valueOf(String.valueOf(targetValue).contains(String.valueOf(needleValue)));
        }
    }

    private static final class DaysBetweenFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "daysBetween";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
            LocalDate leftDate = toDate(left.getValue(env));
            LocalDate rightDate = toDate(right.getValue(env));
            if (leftDate == null || rightDate == null) {
                return AviatorLong.valueOf(0L);
            }
            return AviatorLong.valueOf(Math.abs(ChronoUnit.DAYS.between(leftDate, rightDate)));
        }

        private LocalDate toDate(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof LocalDate localDate) {
                return localDate;
            }
            if (value instanceof java.util.Date date) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static final class IsBlankFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "isBlank";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value) {
            Object resolved = value.getValue(env);
            boolean blank = resolved == null || String.valueOf(resolved).isBlank();
            return AviatorBoolean.valueOf(blank);
        }
    }

    private static final class IsLongLeaveFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "isLongLeave";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject days) {
            Number resolved = FunctionUtils.getNumberValue(days, env);
            if (resolved == null) {
                return AviatorBoolean.FALSE;
            }
            return AviatorBoolean.valueOf(resolved.doubleValue() >= 3D);
        }
    }

    public record FunctionMetadata(
            String name,
            String category,
            String description,
            List<FunctionParameterMetadata> parameters,
            String example
    ) {
    }

    public record FunctionParameterMetadata(
            String name,
            String description,
            String type,
            boolean required
    ) {
    }
}
