package com.westflow.processruntime.service;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.westflow.common.error.ContractException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 包容网关启动时，根据分支优先级、默认分支和分支数策略选择本次真正激活的出边。
 */
@Service("inclusiveBranchSelectionListener")
public class InclusiveBranchSelectionListener implements ExecutionListener {

    private static final String WESTFLOW_NS = "https://westflow.dev/schema/bpmn";
    private static final String SELECTION_PREFIX = "westflowInclusiveSelected_";
    private static final String SELECTION_LIST_PREFIX = "westflowInclusiveSelectedEdges_";
    private static final String SELECTION_SUMMARY_PREFIX = "westflowInclusiveSelectionSummary_";

    static {
        AviatorEvaluator.addFunction(new IfElseFunction());
        AviatorEvaluator.addFunction(new ContainsFunction());
        AviatorEvaluator.addFunction(new DaysBetweenFunction());
        AviatorEvaluator.addFunction(new IsBlankFunction());
    }

    @Override
    public void notify(DelegateExecution execution) {
        FlowElement currentFlowElement = execution.getCurrentFlowElement();
        if (!(currentFlowElement instanceof InclusiveGateway gateway)) {
            return;
        }
        List<SequenceFlow> outgoingFlows = gateway.getOutgoingFlows();
        if (outgoingFlows == null || outgoingFlows.isEmpty()) {
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>(execution.getVariables());
        List<BranchCandidate> eligibleCandidates = outgoingFlows.stream()
                .map(flow -> toCandidate(flow, variables))
                .filter(BranchCandidate::eligible)
                .sorted(Comparator
                        .comparing(BranchCandidate::priority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BranchCandidate::edgeId))
                .toList();

        String mergePolicy = attributeValue(gateway, "branchMergePolicy");
        if (mergePolicy == null || mergePolicy.isBlank()) {
            mergePolicy = "ALL_SELECTED";
        }
        String defaultBranchId = attributeValue(gateway, "defaultBranchId");
        Integer requiredBranchCount = integerValue(attributeValue(gateway, "requiredBranchCount"));

        List<SelectedBranchDecision> selectedDecisions = selectCandidates(
                eligibleCandidates,
                outgoingFlows,
                mergePolicy,
                defaultBranchId,
                requiredBranchCount
        );

        if (selectedDecisions.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("splitNodeId", gateway.getId());
            details.put("branchMergePolicy", mergePolicy);
            details.put("defaultBranchId", defaultBranchId);
            details.put("requiredBranchCount", requiredBranchCount);
            throw new ContractException(
                    "PROCESS.INCLUSIVE_BRANCH_SELECTION_FAILED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "包容分支没有可激活的出边",
                    details
            );
        }

        List<BranchCandidate> selectedCandidates = selectedDecisions.stream()
                .map(SelectedBranchDecision::candidate)
                .toList();
        Set<String> selectedEdgeIds = new LinkedHashSet<>();
        for (BranchCandidate candidate : selectedCandidates) {
            selectedEdgeIds.add(candidate.edgeId());
        }

        for (SequenceFlow flow : outgoingFlows) {
            execution.setVariable(selectionVariableName(flow.getId()), selectedEdgeIds.contains(flow.getId()));
        }

        List<String> selectedLabels = selectedCandidates.stream()
                .map(candidate -> candidate.label() == null || candidate.label().isBlank() ? candidate.edgeId() : candidate.label())
                .toList();
        List<Integer> selectedPriorities = selectedCandidates.stream()
                .map(BranchCandidate::priority)
                .toList();
        List<String> selectedReasons = selectedDecisions.stream()
                .map(SelectedBranchDecision::reason)
                .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("splitNodeId", gateway.getId());
        summary.put("branchMergePolicy", mergePolicy);
        summary.put("defaultBranchId", defaultBranchId);
        summary.put("requiredBranchCount", requiredBranchCount);
        summary.put("totalBranchCount", outgoingFlows.size());
        summary.put("eligibleBranchCount", eligibleCandidates.size());
        summary.put("selectedBranchCount", selectedCandidates.size());
        summary.put("selectedEdgeIds", List.copyOf(selectedEdgeIds));
        summary.put("selectedLabels", selectedLabels);
        summary.put("selectedPriorities", selectedPriorities);
        summary.put("selectedDecisionReasons", selectedReasons);
        execution.setVariable(selectionListName(gateway.getId()), List.copyOf(selectedEdgeIds));
        execution.setVariable(selectionSummaryName(gateway.getId()), summary);
    }

    private List<SelectedBranchDecision> selectCandidates(
            List<BranchCandidate> eligibleCandidates,
            List<SequenceFlow> outgoingFlows,
            String mergePolicy,
            String defaultBranchId,
            Integer requiredBranchCount
    ) {
        if ("REQUIRED_COUNT".equals(mergePolicy)) {
            int count = requiredBranchCount == null || requiredBranchCount <= 0
                    ? eligibleCandidates.size()
                    : requiredBranchCount;
            List<SelectedBranchDecision> selected = new ArrayList<>(eligibleCandidates.stream()
                    .limit(count)
                    .map(candidate -> new SelectedBranchDecision(candidate, "REQUIRED_COUNT_PRIORITY"))
                    .toList());
            if (selected.isEmpty()) {
                addDefaultBranchIfPresent(selected, outgoingFlows, defaultBranchId);
            }
            return selected;
        }
        if ("DEFAULT_BRANCH".equals(mergePolicy)) {
            if (!eligibleCandidates.isEmpty()) {
                return List.of(new SelectedBranchDecision(eligibleCandidates.get(0), "DEFAULT_POLICY_PRIORITY"));
            }
            List<SelectedBranchDecision> selected = new ArrayList<>();
            addDefaultBranchIfPresent(selected, outgoingFlows, defaultBranchId);
            return selected;
        }
        List<SelectedBranchDecision> selected = new ArrayList<>(eligibleCandidates.stream()
                .map(candidate -> new SelectedBranchDecision(candidate, "ELIGIBLE_MATCH"))
                .toList());
        if (selected.isEmpty()) {
            addDefaultBranchIfPresent(selected, outgoingFlows, defaultBranchId);
        }
        return selected;
    }

    private void addDefaultBranchIfPresent(
            List<SelectedBranchDecision> selected,
            List<SequenceFlow> outgoingFlows,
            String defaultBranchId
    ) {
        if (defaultBranchId == null || defaultBranchId.isBlank()) {
            return;
        }
        outgoingFlows.stream()
                .filter(flow -> defaultBranchId.equals(flow.getId()))
                .findFirst()
                .map(flow -> new BranchCandidate(
                        flow.getId(),
                        flow.getName(),
                        integerValue(attributeValue(flow, "branchPriority")),
                        true,
                        attributeValue(flow, "branchConditionExpression")
                ))
                .map(candidate -> new SelectedBranchDecision(candidate, "DEFAULT_BRANCH_FALLBACK"))
                .ifPresent(selected::add);
    }

    private BranchCandidate toCandidate(SequenceFlow flow, Map<String, Object> variables) {
        String edgeId = flow.getId();
        String label = flow.getName();
        Integer priority = integerValue(attributeValue(flow, "branchPriority"));
        String conditionExpression = attributeValue(flow, "branchConditionExpression");
        boolean eligible = evaluateCondition(conditionExpression, variables);
        return new BranchCandidate(edgeId, label, priority, eligible, conditionExpression);
    }

    private String attributeValue(BaseElement element, String attributeName) {
        String namespaced = stringValue(element.getAttributeValue(WESTFLOW_NS, attributeName));
        if (namespaced != null) {
            return namespaced;
        }
        List<ExtensionAttribute> attributes = element.getAttributes().get(attributeName);
        if (attributes != null && !attributes.isEmpty()) {
            String attributeValue = stringValue(attributes.get(0).getValue());
            if (attributeValue != null) {
                return attributeValue;
            }
        }
        for (List<ExtensionAttribute> values : element.getAttributes().values()) {
            for (ExtensionAttribute attribute : values) {
                if (attributeName.equals(attribute.getName())) {
                    String attributeValue = stringValue(attribute.getValue());
                    if (attributeValue != null) {
                        return attributeValue;
                    }
                }
            }
        }
        return extensionValue(element, attributeName);
    }

    private String extensionValue(BaseElement element, String attributeName) {
        Map<String, List<ExtensionElement>> extensionElements = element.getExtensionElements();
        if (extensionElements == null || extensionElements.isEmpty()) {
            return null;
        }
        List<ExtensionElement> elements = extensionElements.get(attributeName);
        if (elements != null && !elements.isEmpty()) {
            ExtensionElement first = elements.get(0);
            return stringValue(first.getElementText());
        }
        for (List<ExtensionElement> values : extensionElements.values()) {
            for (ExtensionElement elementValue : values) {
                if (attributeName.equals(elementValue.getName())) {
                    String text = stringValue(elementValue.getElementText());
                    if (text != null) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private boolean evaluateCondition(String conditionExpression, Map<String, Object> variables) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("expression", conditionExpression);
            throw new ContractException(
                    "PROCESS.INCLUSIVE_BRANCH_SELECTION_FAILED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "包容分支条件不能为空",
                    details
            );
        }
        String normalizedExpression = normalizeExpression(conditionExpression);
        try {
            Object result = AviatorEvaluator.execute(normalizedExpression, variables, true);
            return toBoolean(result);
        } catch (RuntimeException exception) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("expression", conditionExpression);
            details.put("normalizedExpression", normalizedExpression);
            details.put("error", exception.getMessage());
            throw new ContractException(
                    "PROCESS.INCLUSIVE_BRANCH_SELECTION_FAILED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "包容分支条件执行失败",
                    details
            );
        }
    }

    private String normalizeExpression(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value == null) {
            return false;
        }
        return !String.valueOf(value).isBlank();
    }

    private String selectionVariableName(String edgeId) {
        return SELECTION_PREFIX + edgeId;
    }

    private String selectionListName(String splitNodeId) {
        return SELECTION_LIST_PREFIX + splitNodeId;
    }

    private String selectionSummaryName(String splitNodeId) {
        return SELECTION_SUMMARY_PREFIX + splitNodeId;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private record BranchCandidate(
            String edgeId,
            String label,
            Integer priority,
            boolean eligible,
            String conditionExpression
    ) {
    }

    private record SelectedBranchDecision(
            BranchCandidate candidate,
            String reason
    ) {
    }

    private static final class IfElseFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "ifElse";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject condition, AviatorObject whenTrue, AviatorObject whenFalse) {
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
                return AviatorBoolean.valueOf(collection.stream().anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(needleValue))));
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
