package com.westflow.plm.service;

import com.westflow.plm.api.PlmDashboardAnalyticsResponse;
import com.westflow.plm.api.PlmDashboardDistributionResponse;
import com.westflow.plm.api.PlmDashboardOwnerRankingResponse;
import com.westflow.plm.api.PlmDashboardRecentBillResponse;
import com.westflow.plm.api.PlmDashboardSummaryResponse;
import com.westflow.plm.api.PlmDashboardTaskAlertResponse;
import com.westflow.plm.api.PlmDashboardTrendResponse;
import com.westflow.plm.mapper.PlmEcrBillMapper;
import com.westflow.plm.mapper.PlmEcoBillMapper;
import com.westflow.plm.mapper.PlmImplementationTaskMapper;
import com.westflow.plm.mapper.PlmMaterialChangeBillMapper;
import com.westflow.plm.model.PlmImplementationTaskRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * PLM 仪表盘聚合服务。
 */
@Service
@RequiredArgsConstructor
public class PlmDashboardService {

    private static final int DASHBOARD_RECENT_LIMIT = 6;

    private final JdbcTemplate jdbcTemplate;
    private final PlmEcrBillMapper plmEcrBillMapper;
    private final PlmEcoBillMapper plmEcoBillMapper;
    private final PlmMaterialChangeBillMapper plmMaterialChangeBillMapper;
    private final PlmImplementationTaskMapper plmImplementationTaskMapper;

    public PlmDashboardSummaryResponse dashboardSummary(String userId) {
        return new PlmDashboardSummaryResponse(
                safeStats(plmEcrBillMapper.selectDashboardStats(userId)).totalCount()
                        + safeStats(plmEcoBillMapper.selectDashboardStats(userId)).totalCount()
                        + safeStats(plmMaterialChangeBillMapper.selectDashboardStats(userId)).totalCount(),
                safeStats(plmEcrBillMapper.selectDashboardStats(userId)).draftCount()
                        + safeStats(plmEcoBillMapper.selectDashboardStats(userId)).draftCount()
                        + safeStats(plmMaterialChangeBillMapper.selectDashboardStats(userId)).draftCount(),
                safeStats(plmEcrBillMapper.selectDashboardStats(userId)).runningCount()
                        + safeStats(plmEcoBillMapper.selectDashboardStats(userId)).runningCount()
                        + safeStats(plmMaterialChangeBillMapper.selectDashboardStats(userId)).runningCount(),
                safeStats(plmEcrBillMapper.selectDashboardStats(userId)).completedCount()
                        + safeStats(plmEcoBillMapper.selectDashboardStats(userId)).completedCount()
                        + safeStats(plmMaterialChangeBillMapper.selectDashboardStats(userId)).completedCount(),
                safeStats(plmEcrBillMapper.selectDashboardStats(userId)).rejectedCount()
                        + safeStats(plmEcoBillMapper.selectDashboardStats(userId)).rejectedCount()
                        + safeStats(plmMaterialChangeBillMapper.selectDashboardStats(userId)).rejectedCount(),
                safeStats(plmEcrBillMapper.selectDashboardStats(userId)).cancelledCount()
                        + safeStats(plmEcoBillMapper.selectDashboardStats(userId)).cancelledCount()
                        + safeStats(plmMaterialChangeBillMapper.selectDashboardStats(userId)).cancelledCount(),
                recentBills(userId)
        );
    }

    public PlmDashboardAnalyticsResponse dashboardAnalytics(String userId) {
        PlmDashboardSummaryResponse summary = dashboardSummary(userId);
        List<BillSnapshot> bills = loadBillSnapshots(userId);
        Map<String, BillSnapshot> billIndex = new LinkedHashMap<>();
        for (BillSnapshot bill : bills) {
            billIndex.put(bill.businessType() + "#" + bill.billId(), bill);
        }

        List<PlmDashboardDistributionResponse> typeDistribution = countDistribution(
                bills.stream().map(BillSnapshot::businessType).toList(),
                Map.of(
                        "PLM_ECR", "ECR 变更申请",
                        "PLM_ECO", "ECO 变更执行",
                        "PLM_MATERIAL", "物料主数据变更申请"
                )
        );
        List<PlmDashboardDistributionResponse> stageDistribution = countDistribution(
                bills.stream().map(BillSnapshot::status).toList(),
                Map.of(
                        "DRAFT", "草稿",
                        "RUNNING", "运行中",
                        "IMPLEMENTING", "实施中",
                        "VALIDATING", "验证中",
                        "COMPLETED", "已完成",
                        "REJECTED", "已拒绝",
                        "CANCELLED", "已取消",
                        "CLOSED", "已关闭"
                )
        );
        List<PlmDashboardTrendResponse> trendSeries = trendSeries(bills);
        List<PlmDashboardTaskAlertResponse> taskAlerts = taskAlerts(billIndex);
        List<PlmDashboardOwnerRankingResponse> ownerRanking = ownerRanking(billIndex);
        return new PlmDashboardAnalyticsResponse(
                summary,
                typeDistribution,
                stageDistribution,
                trendSeries,
                taskAlerts,
                ownerRanking
        );
    }

    private List<PlmDashboardRecentBillResponse> recentBills(String userId) {
        List<PlmDashboardRecentBillResponse> recentBills = new ArrayList<>();
        recentBills.addAll(plmEcrBillMapper.selectRecentByCreator(userId, DASHBOARD_RECENT_LIMIT));
        recentBills.addAll(plmEcoBillMapper.selectRecentByCreator(userId, DASHBOARD_RECENT_LIMIT));
        recentBills.addAll(plmMaterialChangeBillMapper.selectRecentByCreator(userId, DASHBOARD_RECENT_LIMIT));
        return recentBills.stream()
                .sorted(Comparator.comparing(PlmDashboardRecentBillResponse::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(DASHBOARD_RECENT_LIMIT)
                .toList();
    }

    private List<BillSnapshot> loadBillSnapshots(String userId) {
        return jdbcTemplate.query(
                """
                SELECT 'PLM_ECR' AS business_type,
                       id AS bill_id,
                       bill_no,
                       scene_code,
                       change_title AS title,
                       status,
                       creator_user_id,
                       created_at,
                       updated_at
                FROM plm_ecr_change
                WHERE creator_user_id = ?
                UNION ALL
                SELECT 'PLM_ECO' AS business_type,
                       id AS bill_id,
                       bill_no,
                       scene_code,
                       execution_title AS title,
                       status,
                       creator_user_id,
                       created_at,
                       updated_at
                FROM plm_eco_execution
                WHERE creator_user_id = ?
                UNION ALL
                SELECT 'PLM_MATERIAL' AS business_type,
                       id AS bill_id,
                       bill_no,
                       scene_code,
                       material_name AS title,
                       status,
                       creator_user_id,
                       created_at,
                       updated_at
                FROM plm_material_change
                WHERE creator_user_id = ?
                """,
                (rs, rowNum) -> new BillSnapshot(
                        rs.getString("business_type"),
                        rs.getString("bill_id"),
                        rs.getString("bill_no"),
                        rs.getString("scene_code"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("creator_user_id"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                userId,
                userId,
                userId
        );
    }

    private List<PlmDashboardDistributionResponse> countDistribution(List<String> values, Map<String, String> labels) {
        Map<String, Long> counts = new LinkedHashMap<>();
        labels.forEach((code, label) -> counts.put(code, 0L));
        for (String value : values) {
            if (value == null) {
                continue;
            }
            counts.computeIfPresent(value, (key, current) -> current + 1);
        }
        List<PlmDashboardDistributionResponse> responses = new ArrayList<>();
        counts.forEach((code, count) -> responses.add(new PlmDashboardDistributionResponse(code, labels.get(code), count)));
        return responses;
    }

    private List<PlmDashboardTrendResponse> trendSeries(List<BillSnapshot> bills) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int offset = 29; offset >= 0; offset--) {
            counts.put(today.minusDays(offset), 0L);
        }
        for (BillSnapshot bill : bills) {
            counts.computeIfPresent(bill.createdAt().atZone(ZoneId.systemDefault()).toLocalDate(), (date, count) -> count + 1);
        }
        List<PlmDashboardTrendResponse> responses = new ArrayList<>();
        counts.forEach((date, count) -> responses.add(new PlmDashboardTrendResponse(date, count)));
        return responses;
    }

    private List<PlmDashboardTaskAlertResponse> taskAlerts(Map<String, BillSnapshot> billIndex) {
        LocalDateTime now = LocalDateTime.now();
        return plmImplementationTaskMapper.selectAll().stream()
                .filter(task -> isCurrentUsersBill(task, billIndex))
                .filter(task -> task.plannedEndAt() != null && task.plannedEndAt().isBefore(now))
                .filter(task -> !List.of("COMPLETED", "CANCELLED").contains(normalize(task.status())))
                .sorted(Comparator.comparing(PlmImplementationTaskRecord::plannedEndAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(10)
                .map(task -> {
                    BillSnapshot bill = billIndex.get(task.businessType() + "#" + task.billId());
                    long overdueDays = task.plannedEndAt() == null
                            ? 0
                            : Math.max(0, java.time.Duration.between(task.plannedEndAt(), now).toDays());
                    return new PlmDashboardTaskAlertResponse(
                            task.billId(),
                            bill == null ? null : bill.billNo(),
                            task.id(),
                            task.taskTitle(),
                            task.ownerUserId(),
                            task.status(),
                            task.plannedEndAt(),
                            overdueDays
                    );
                })
                .toList();
    }

    private List<PlmDashboardOwnerRankingResponse> ownerRanking(Map<String, BillSnapshot> billIndex) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, OwnerRanking> rankings = new LinkedHashMap<>();
        for (PlmImplementationTaskRecord task : plmImplementationTaskMapper.selectAll()) {
            if (!isCurrentUsersBill(task, billIndex)) {
                continue;
            }
            String owner = task.ownerUserId() == null || task.ownerUserId().isBlank() ? "UNASSIGNED" : task.ownerUserId();
            OwnerRanking ranking = rankings.computeIfAbsent(owner, key -> new OwnerRanking());
            ranking.taskCount++;
            if ("COMPLETED".equalsIgnoreCase(task.status())) {
                ranking.completedCount++;
            }
            if (task.plannedEndAt() != null
                    && task.plannedEndAt().isBefore(now)
                    && !List.of("COMPLETED", "CANCELLED").contains(normalize(task.status()))) {
                ranking.overdueCount++;
            }
        }
        return rankings.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue().taskCount, left.getValue().taskCount))
                .map(entry -> new PlmDashboardOwnerRankingResponse(
                        entry.getKey(),
                        entry.getValue().taskCount,
                        entry.getValue().completedCount,
                        entry.getValue().overdueCount
                ))
                .toList();
    }

    private boolean isCurrentUsersBill(PlmImplementationTaskRecord task, Map<String, BillSnapshot> billIndex) {
        return billIndex.containsKey(task.businessType() + "#" + task.billId());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private com.westflow.plm.model.PlmDashboardStatsRecord safeStats(com.westflow.plm.model.PlmDashboardStatsRecord stats) {
        if (stats == null) {
            return new com.westflow.plm.model.PlmDashboardStatsRecord(0, 0, 0, 0, 0, 0);
        }
        return stats;
    }

    private record BillSnapshot(
            String businessType,
            String billId,
            String billNo,
            String sceneCode,
            String title,
            String status,
            String creatorUserId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    private static final class OwnerRanking {
        long taskCount;
        long completedCount;
        long overdueCount;
    }
}
