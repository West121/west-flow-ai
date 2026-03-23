package com.westflow.aiadmin.support;

import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * AI 管理后台的公共支持工具。
 */
public final class AiAdminSupport {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private AiAdminSupport() {
    }

    /**
     * 去掉字符串前后空白，空白串视为 null。
     */
    public static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 去掉字符串前后空白。
     */
    public static String normalize(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "" : normalized;
    }

    /**
     * 生成带前缀的业务主键。
     */
    public static String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 将本地时间转换成带时区的时间，方便接口直接返回给前端。
     */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(TIME_ZONE).toOffsetDateTime();
    }

    /**
     * 将启用状态转换成展示字符串。
     */
    public static String toStatus(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }

    /**
     * 按通用分页规则切片。
     */
    public static <T> PageResponse<T> toPage(PageRequest request, List<T> records) {
        long total = records.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(records.size(), fromIndex + request.pageSize());
        List<T> pageRecords = fromIndex >= records.size() ? List.of() : records.subList(fromIndex, toIndex);
        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    /**
     * 合并并去重多个字符串列表，保留首次出现顺序。
     */
    public static List<String> mergeDistinctStrings(List<List<String>> parts) {
        Set<String> values = new LinkedHashSet<>();
        for (List<String> part : parts) {
            if (part == null) {
                continue;
            }
            for (String value : part) {
                String normalized = normalizeNullable(value);
                if (normalized != null) {
                    values.add(normalized);
                }
            }
        }
        return List.copyOf(values);
    }
}
