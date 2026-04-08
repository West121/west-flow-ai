package com.westflow.processruntime.query;

import com.westflow.common.api.RequestContext;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import java.util.Comparator;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskQueryService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeTaskQueryService.class);

    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;
    private final RuntimeTaskAssembler runtimeTaskAssembler;

    public PageResponse<ProcessTaskListItemResponse> page(PageRequest request, String currentUserId) {
        long startedAt = System.nanoTime();
        RuntimeTaskQueryContext queryContext = RuntimeTaskQueryContext.create();
        long visibleTasksStartedAt = System.nanoTime();
        List<Task> visibleTasks = runtimeTaskVisibilityService.visibleActiveTasks(
                currentUserId,
                queryContext,
                runtimeTaskAssembler::resolvePublishedDefinitionByInstance
        );
        long visibleTasksElapsedMs = elapsedMs(visibleTasksStartedAt);
        RuntimeTaskProjectionContext projectionContext = RuntimeTaskProjectionContext.create();
        if (isDefaultTaskPageRequest(request)) {
            long pageSliceStartedAt = System.nanoTime();
            List<Task> pageTasks = pageSlice(visibleTasks, request.page(), request.pageSize());
            long pageSliceElapsedMs = elapsedMs(pageSliceStartedAt);
            long enrichStartedAt = System.nanoTime();
            List<ProcessTaskListItemResponse> records = pageTasks.stream()
                    .map(task -> runtimeTaskAssembler.toTaskListItem(task, projectionContext, queryContext))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            long enrichElapsedMs = elapsedMs(enrichStartedAt);
            PageResponse<ProcessTaskListItemResponse> response = page(records, request.page(), request.pageSize(), visibleTasks.size());
            log.info(
                    "approval-perf requestId={} path={} stage=tasks.page visibleMs={} sliceMs={} enrichMs={} totalMs={} totalVisibleTasks={} pageRecords={} page={} pageSize={} keywordBlank={}",
                    RequestContext.getOrCreateRequestId(),
                    RequestContext.currentPath(),
                    visibleTasksElapsedMs,
                    pageSliceElapsedMs,
                    enrichElapsedMs,
                    elapsedMs(startedAt),
                    visibleTasks.size(),
                    records.size(),
                    request.page(),
                    request.pageSize(),
                    request.keyword() == null || request.keyword().isBlank()
            );
            return response;
        }
        long enrichStartedAt = System.nanoTime();
        List<ProcessTaskListItemResponse> allRecords = visibleTasks.stream()
                .map(task -> runtimeTaskAssembler.toTaskListItem(task, projectionContext, queryContext))
                .filter(java.util.Objects::nonNull)
                .toList();
        long enrichElapsedMs = elapsedMs(enrichStartedAt);
        long filterStartedAt = System.nanoTime();
        List<ProcessTaskListItemResponse> filteredRecords = allRecords.stream()
                .filter(item -> matchesPredictionFilters(item, request.filters()))
                .filter(item -> runtimeTaskAssembler.matchesTaskKeyword(item, request.keyword()))
                .toList();
        long filterElapsedMs = elapsedMs(filterStartedAt);
        List<ProcessTaskListItemResponse> sortedRecords = applyPredictionSorts(filteredRecords, request.sorts());
        PageResponse<ProcessTaskListItemResponse> response = page(sortedRecords, request.page(), request.pageSize());
        log.info(
                "approval-perf requestId={} path={} stage=tasks.page visibleMs={} enrichMs={} filterMs={} totalMs={} totalVisibleTasks={} filteredRecords={} page={} pageSize={} keywordBlank={}",
                RequestContext.getOrCreateRequestId(),
                RequestContext.currentPath(),
                visibleTasksElapsedMs,
                enrichElapsedMs,
                filterElapsedMs,
                elapsedMs(startedAt),
                visibleTasks.size(),
                sortedRecords.size(),
                request.page(),
                request.pageSize(),
                request.keyword() == null || request.keyword().isBlank()
        );
        return response;
    }

    private boolean isDefaultTaskPageRequest(PageRequest request) {
        return (request.keyword() == null || request.keyword().isBlank())
                && request.filters().isEmpty()
                && request.sorts().isEmpty()
                && request.groups().isEmpty();
    }

    private boolean matchesPredictionFilters(ProcessTaskListItemResponse item, List<FilterItem> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (FilterItem filter : filters) {
            if (!matchesPredictionFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesPredictionFilter(ProcessTaskListItemResponse item, FilterItem filter) {
        if (filter == null || filter.field() == null) {
            return true;
        }
        if (!"prediction.overdueRiskLevel".equals(filter.field())) {
            return true;
        }
        String operator = filter.operator() == null ? "eq" : filter.operator().toLowerCase(Locale.ROOT);
        String riskLevel = item.prediction() == null || item.prediction().overdueRiskLevel() == null
                ? ""
                : item.prediction().overdueRiskLevel().toUpperCase(Locale.ROOT);
        if ("eq".equals(operator)) {
            String expected = filter.value() == null || filter.value().isNull()
                    ? ""
                    : filter.value().asText("").toUpperCase(Locale.ROOT);
            return riskLevel.equals(expected);
        }
        if ("in".equals(operator) && filter.value() != null && filter.value().isArray()) {
            for (int i = 0; i < filter.value().size(); i++) {
                if (riskLevel.equals(filter.value().get(i).asText("").toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private List<ProcessTaskListItemResponse> applyPredictionSorts(
            List<ProcessTaskListItemResponse> records,
            List<SortItem> sorts
    ) {
        if (sorts == null || sorts.isEmpty()) {
            return records;
        }
        List<ProcessTaskListItemResponse> sorted = records;
        for (int index = sorts.size() - 1; index >= 0; index--) {
            SortItem sort = sorts.get(index);
            Comparator<ProcessTaskListItemResponse> comparator = predictionComparator(sort.field());
            if (comparator == null) {
                continue;
            }
            if ("desc".equalsIgnoreCase(sort.direction())) {
                comparator = comparator.reversed();
            }
            sorted = sorted.stream().sorted(comparator).toList();
        }
        return sorted;
    }

    private Comparator<ProcessTaskListItemResponse> predictionComparator(String field) {
        if ("prediction.overdueRiskLevel".equals(field)) {
            return Comparator.comparingInt(item -> riskWeight(item.prediction() == null ? null : item.prediction().overdueRiskLevel()));
        }
        if ("prediction.remainingDurationMinutes".equals(field)) {
            return Comparator.comparingLong(item ->
                    item.prediction() == null || item.prediction().remainingDurationMinutes() == null
                            ? Long.MIN_VALUE
                            : item.prediction().remainingDurationMinutes());
        }
        if ("prediction.currentElapsedMinutes".equals(field)) {
            return Comparator.comparingLong(item ->
                    item.prediction() == null || item.prediction().currentElapsedMinutes() == null
                            ? Long.MIN_VALUE
                            : item.prediction().currentElapsedMinutes());
        }
        return null;
    }

    private int riskWeight(String riskLevel) {
        return switch ((riskLevel == null ? "" : riskLevel).toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private <T> PageResponse<T> page(List<T> records, int page, int pageSize) {
        return page(records, page, pageSize, records.size());
    }

    private <T> PageResponse<T> page(List<T> records, int page, int pageSize, long total) {
        long pages = total == 0 ? 0 : (total + pageSize - 1L) / pageSize;
        List<T> currentPage = total == records.size() ? pageSlice(records, page, pageSize) : records;
        return new PageResponse<>(page, pageSize, total, pages, currentPage, List.of());
    }

    private <T> List<T> pageSlice(List<T> records, int page, int pageSize) {
        long total = records.size();
        long offset = Math.max(0L, (long) (page - 1) * pageSize);
        return total == 0 ? List.of() : records.stream().skip(offset).limit(pageSize).toList();
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
