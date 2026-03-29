package com.westflow.processruntime.support;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程设计器受控公式的统一执行入口，避免不同运行态各自维护一套函数表。
 */
public final class WorkflowFormulaEvaluator {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private WorkflowFormulaEvaluator() {
    }

    public static Object execute(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        registerFunctionsIfNeeded();
        return AviatorEvaluator.execute(normalizeExpression(expression), variables, true);
    }

    private static String normalizeExpression(String expression) {
        String normalized = expression.trim();
        if (normalized.startsWith("${") && normalized.endsWith("}")) {
            return normalized.substring(2, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static void registerFunctionsIfNeeded() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        AviatorEvaluator.addFunction(new IfElseFunction());
        AviatorEvaluator.addFunction(new ContainsFunction());
        AviatorEvaluator.addFunction(new DaysBetweenFunction());
        AviatorEvaluator.addFunction(new IsBlankFunction());
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
}
