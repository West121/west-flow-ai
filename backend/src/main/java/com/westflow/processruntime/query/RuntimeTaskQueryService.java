package com.westflow.processruntime.query;

import com.westflow.common.api.RequestContext;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import java.time.Duration;
import java.util.List;
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
                .filter(item -> runtimeTaskAssembler.matchesTaskKeyword(item, request.keyword()))
                .toList();
        long filterElapsedMs = elapsedMs(filterStartedAt);
        PageResponse<ProcessTaskListItemResponse> response = page(filteredRecords, request.page(), request.pageSize());
        log.info(
                "approval-perf requestId={} path={} stage=tasks.page visibleMs={} enrichMs={} filterMs={} totalMs={} totalVisibleTasks={} filteredRecords={} page={} pageSize={} keywordBlank={}",
                RequestContext.getOrCreateRequestId(),
                RequestContext.currentPath(),
                visibleTasksElapsedMs,
                enrichElapsedMs,
                filterElapsedMs,
                elapsedMs(startedAt),
                visibleTasks.size(),
                filteredRecords.size(),
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
