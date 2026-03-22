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
            default -> Map.of();
        };
    }

    /**
     * 读取请假单业务数据。
     */
    private Map<String, Object> resolveLeaveBusinessData(String businessKey) {
        return queryBusinessData(
                """
                SELECT id, bill_no, scene_code, days, reason, process_instance_id, status, creator_user_id
                FROM oa_leave_bill
                WHERE id = ?
                """,
                businessKey,
                resultSet -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("billId", resultSet.getObject(1));
                    data.put("billNo", resultSet.getObject(2));
                    data.put("sceneCode", resultSet.getObject(3));
                    data.put("days", resultSet.getObject(4));
                    data.put("reason", resultSet.getObject(5));
                    data.put("processInstanceId", resultSet.getObject(6));
                    data.put("status", resultSet.getObject(7));
                    data.put("creatorUserId", resultSet.getObject(8));
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
