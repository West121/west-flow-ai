package com.westflow.approval.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 审批单业务数据查询服务。
 */
@Service
public class ApprovalSheetQueryService {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalSheetQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按业务类型和业务主键读取审批单业务数据。
     */
    public Map<String, Object> resolveBusinessData(String businessType, String businessKey) {
        if (businessType == null || businessType.isBlank() || businessKey == null || businessKey.isBlank()) {
            return Map.of();
        }

        return switch (businessType) {
            case "OA_LEAVE" -> resolveLeaveBusinessData(businessKey);
            case "OA_EXPENSE" -> resolveExpenseBusinessData(businessKey);
            case "OA_COMMON" -> resolveCommonBusinessData(businessKey);
            case "PLM_ECR" -> resolvePlmEcrBusinessData(businessKey);
            case "PLM_ECO" -> resolvePlmEcoBusinessData(businessKey);
            case "PLM_MATERIAL" -> resolvePlmMaterialBusinessData(businessKey);
            case "PLM_PROJECT" -> resolvePlmProjectBusinessData(businessKey);
            default -> Map.of();
        };
    }

    /**
     * 读取请假单业务数据。
     */
    private Map<String, Object> resolveLeaveBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, leave_type, days, reason, urgent, manager_user_id, process_instance_id, status, creator_user_id
                FROM oa_leave_bill
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("leaveType", resultSet.getObject(4));
                    data.put("days", resultSet.getObject(5));
                    data.put("reason", resultSet.getObject(6));
                    data.put("urgent", resultSet.getObject(7));
                    data.put("managerUserId", resultSet.getObject(8));
                    data.put("processInstanceId", resultSet.getObject(9));
                    data.put("status", resultSet.getObject(10));
                    data.put("creatorUserId", resultSet.getObject(11));
                    return data;
                }
        );
    }

    /**
     * 读取报销单业务数据。
     */
    private Map<String, Object> resolveExpenseBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, amount, reason, process_instance_id, status, creator_user_id
                FROM oa_expense_bill
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("amount", resultSet.getObject(4));
                    data.put("reason", resultSet.getObject(5));
                    data.put("processInstanceId", resultSet.getObject(6));
                    data.put("status", resultSet.getObject(7));
                    data.put("creatorUserId", resultSet.getObject(8));
                    return data;
                }
        );
    }

    /**
     * 读取通用申请单业务数据。
     */
    private Map<String, Object> resolveCommonBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, title, content, process_instance_id, status, creator_user_id
                FROM oa_common_request_bill
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("title", resultSet.getObject(4));
                    data.put("content", resultSet.getObject(5));
                    data.put("processInstanceId", resultSet.getObject(6));
                    data.put("status", resultSet.getObject(7));
                    data.put("creatorUserId", resultSet.getObject(8));
                    return data;
                }
        );
    }

    /**
     * 读取 PLM ECR 业务数据。
     */
    private Map<String, Object> resolvePlmEcrBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, change_title, change_reason, affected_product_code, priority_level, process_instance_id, status, creator_user_id
                FROM plm_ecr_change
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("changeTitle", resultSet.getObject(4));
                    data.put("changeReason", resultSet.getObject(5));
                    data.put("affectedProductCode", resultSet.getObject(6));
                    data.put("priorityLevel", resultSet.getObject(7));
                    data.put("processInstanceId", resultSet.getObject(8));
                    data.put("status", resultSet.getObject(9));
                    data.put("creatorUserId", resultSet.getObject(10));
                    return data;
                }
        );
    }

    /**
     * 读取 PLM ECO 业务数据。
     */
    private Map<String, Object> resolvePlmEcoBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, execution_title, execution_plan, effective_date, change_reason, process_instance_id, status, creator_user_id
                FROM plm_eco_execution
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("executionTitle", resultSet.getObject(4));
                    data.put("executionPlan", resultSet.getObject(5));
                    data.put("effectiveDate", resultSet.getObject(6));
                    data.put("changeReason", resultSet.getObject(7));
                    data.put("processInstanceId", resultSet.getObject(8));
                    data.put("status", resultSet.getObject(9));
                    data.put("creatorUserId", resultSet.getObject(10));
                    return data;
                }
        );
    }

    /**
     * 读取 PLM 物料主数据变更业务数据。
     */
    private Map<String, Object> resolvePlmMaterialBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, material_code, material_name, change_reason, change_type, process_instance_id, status, creator_user_id
                FROM plm_material_change
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("materialCode", resultSet.getObject(4));
                    data.put("materialName", resultSet.getObject(5));
                    data.put("changeReason", resultSet.getObject(6));
                    data.put("changeType", resultSet.getObject(7));
                    data.put("processInstanceId", resultSet.getObject(8));
                    data.put("status", resultSet.getObject(9));
                    data.put("creatorUserId", resultSet.getObject(10));
                    return data;
                }
        );
    }

    private Map<String, Object> resolvePlmProjectBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, project_no, initiation_scene_code, project_code, project_name, project_type,
                       owner_user_id, sponsor_user_id, initiation_process_instance_id, initiation_status, creator_user_id
                FROM plm_project
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("projectCode", resultSet.getObject(4));
                    data.put("projectName", resultSet.getObject(5));
                    data.put("projectType", resultSet.getObject(6));
                    data.put("ownerUserId", resultSet.getObject(7));
                    data.put("sponsorUserId", resultSet.getObject(8));
                    data.put("processInstanceId", resultSet.getObject(9));
                    data.put("status", resultSet.getObject(10));
                    data.put("creatorUserId", resultSet.getObject(11));
                    return data;
                }
        );
    }

    /**
     * 执行具体 SQL 查询并映射结果。
     */
    private Map<String, Object> queryBusinessData(
            String sql,
            String businessKey,
            RowMapperFunction rowMapper
    ) {
        return jdbcTemplate.query(sql, resultSet -> {
            if (!resultSet.next()) {
                return Map.of();
            }
            return rowMapper.apply(resultSet);
        }, businessKey);
    }

    /**
     * 行映射函数。
     */
    @FunctionalInterface
    private interface RowMapperFunction {
        Map<String, Object> apply(java.sql.ResultSet resultSet) throws java.sql.SQLException;
    }
}
