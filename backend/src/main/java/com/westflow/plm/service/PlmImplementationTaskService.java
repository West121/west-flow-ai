package com.westflow.plm.service;

import com.westflow.common.error.ContractException;
import com.westflow.plm.api.PlmImplementationTaskActionRequest;
import com.westflow.plm.api.PlmImplementationTaskResponse;
import com.westflow.plm.api.PlmImplementationTaskUpsertRequest;
import com.westflow.plm.mapper.PlmImplementationTaskMapper;
import com.westflow.plm.model.PlmImplementationTaskRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * PLM 实施任务服务。
 */
@Service
@RequiredArgsConstructor
public class PlmImplementationTaskService {

    private final PlmImplementationTaskMapper plmImplementationTaskMapper;

    public void seedDefaultTaskIfMissing(
            String businessType,
            String billId,
            String taskTitle,
            String ownerUserId
    ) {
        if (!plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).isEmpty()) {
            return;
        }
        PlmImplementationTaskRecord record = new PlmImplementationTaskRecord(
                buildId("task"),
                businessType,
                billId,
                "TASK-001",
                taskTitle == null || taskTitle.isBlank() ? "实施任务" : taskTitle.trim(),
                "IMPLEMENTATION",
                ownerUserId,
                "PENDING",
                null,
                null,
                null,
                null,
                null,
                true,
                1,
                null,
                null
        );
        plmImplementationTaskMapper.insert(record);
    }

    public void ensureReadyForValidation(String businessType, String billId) {
        List<PlmImplementationTaskRecord> tasks = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId);
        if (tasks.isEmpty()) {
            throw illegalTaskState("至少需要一条实施任务才能进入验证阶段", businessType, billId);
        }
        boolean hasIncompleteRequiredTask = tasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.verificationRequired()))
                .anyMatch(task -> !"COMPLETED".equalsIgnoreCase(task.status()));
        if (hasIncompleteRequiredTask) {
            throw illegalTaskState("存在未完成的必做实施任务", businessType, billId);
        }
    }

    public void ensureReadyForClose(String businessType, String billId) {
        List<PlmImplementationTaskRecord> tasks = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId);
        boolean hasOpenTask = tasks.stream()
                .anyMatch(task -> !"COMPLETED".equalsIgnoreCase(task.status()) && !"CANCELLED".equalsIgnoreCase(task.status()));
        if (hasOpenTask) {
            throw illegalTaskState("仍有未完成的实施任务，不能关闭", businessType, billId);
        }
    }

    public List<PlmImplementationTaskResponse> listBillTasks(String businessType, String billId) {
        return plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PlmImplementationTaskResponse upsertTask(
            String businessType,
            String billId,
            PlmImplementationTaskUpsertRequest request,
            String currentUserId
    ) {
        PlmImplementationTaskRecord existing = request.taskId() == null || request.taskId().isBlank()
                ? null
                : plmImplementationTaskMapper.selectById(request.taskId());

        PlmImplementationTaskRecord record = new PlmImplementationTaskRecord(
                existing == null ? buildId("task") : existing.id(),
                businessType,
                billId,
                normalizeValue(request.taskNo(), existing == null ? nextTaskNo(businessType, billId) : existing.taskNo()),
                normalizeValue(request.taskTitle(), existing == null ? "实施任务" : existing.taskTitle()),
                normalizeValue(request.taskType(), existing == null ? "IMPLEMENTATION" : existing.taskType()),
                normalizeValue(request.ownerUserId(), existing == null ? currentUserId : existing.ownerUserId()),
                normalizeValue(request.status(), existing == null ? "PENDING" : existing.status()),
                request.plannedStartAt(),
                request.plannedEndAt(),
                existing == null ? null : existing.startedAt(),
                existing == null ? null : existing.completedAt(),
                normalizeValue(request.resultSummary(), existing == null ? null : existing.resultSummary()),
                request.verificationRequired() == null ? (existing == null || Boolean.TRUE.equals(existing.verificationRequired())) : request.verificationRequired(),
                request.sortOrder() == null ? (existing == null ? 1 : existing.sortOrder()) : request.sortOrder(),
                existing == null ? null : existing.createdAt(),
                null
        );
        if (existing == null) {
            plmImplementationTaskMapper.insert(record);
        } else {
            plmImplementationTaskMapper.update(record);
        }
        return toResponse(plmImplementationTaskMapper.selectById(record.id()));
    }

    public PlmImplementationTaskResponse startTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "RUNNING", true, false, request);
    }

    public PlmImplementationTaskResponse completeTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "COMPLETED", false, true, request);
    }

    public PlmImplementationTaskResponse blockTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "BLOCKED", false, false, request);
    }

    public PlmImplementationTaskResponse cancelTask(String businessType, String billId, String taskId, PlmImplementationTaskActionRequest request) {
        return changeStatus(businessType, billId, taskId, "CANCELLED", false, true, request);
    }

    private PlmImplementationTaskResponse changeStatus(
            String businessType,
            String billId,
            String taskId,
            String status,
            boolean setStartedAt,
            boolean setCompletedAt,
            PlmImplementationTaskActionRequest request
    ) {
        PlmImplementationTaskRecord record = requireTask(businessType, billId, taskId);
        LocalDateTime now = LocalDateTime.now();
        PlmImplementationTaskRecord updated = new PlmImplementationTaskRecord(
                record.id(),
                record.businessType(),
                record.billId(),
                record.taskNo(),
                record.taskTitle(),
                record.taskType(),
                normalizeValue(request == null ? null : request.ownerUserId(), record.ownerUserId()),
                status,
                record.plannedStartAt(),
                record.plannedEndAt(),
                setStartedAt ? now : record.startedAt(),
                setCompletedAt ? now : record.completedAt(),
                normalizeValue(request == null ? null : request.resultSummary(), record.resultSummary()),
                record.verificationRequired(),
                record.sortOrder(),
                record.createdAt(),
                null
        );
        plmImplementationTaskMapper.update(updated);
        return toResponse(plmImplementationTaskMapper.selectById(record.id()));
    }

    private PlmImplementationTaskRecord requireTask(String businessType, String billId, String taskId) {
        PlmImplementationTaskRecord task = plmImplementationTaskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.businessType(), businessType) || !Objects.equals(task.billId(), billId)) {
            throw illegalTaskState("实施任务不存在", businessType, billId);
        }
        return task;
    }

    private String nextTaskNo(String businessType, String billId) {
        int count = plmImplementationTaskMapper.selectByBusinessTypeAndBillId(businessType, billId).size();
        return String.format("TASK-%03d", count + 1);
    }

    private PlmImplementationTaskResponse toResponse(PlmImplementationTaskRecord record) {
        return new PlmImplementationTaskResponse(
                record.id(),
                record.businessType(),
                record.billId(),
                record.taskNo(),
                record.taskTitle(),
                record.taskType(),
                record.ownerUserId(),
                record.status(),
                record.plannedStartAt(),
                record.plannedEndAt(),
                record.startedAt(),
                record.completedAt(),
                record.resultSummary(),
                record.verificationRequired(),
                record.sortOrder(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private String normalizeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private ContractException illegalTaskState(String message, String businessType, String billId) {
        return new ContractException(
                "PLM.TASK_STATE",
                HttpStatus.CONFLICT,
                message,
                java.util.Map.of(
                        "businessType", businessType,
                        "billId", billId
                )
        );
    }

    private String buildId(String prefix) {
        return prefix + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
