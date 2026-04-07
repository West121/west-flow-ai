package com.westflow.ai.service;

import com.westflow.system.user.mapper.SystemUserMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 解析 AI 文本中的人员指向并映射为系统用户。
 */
@Service
public class AiUserReferenceResolver {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("\\b(usr_[A-Za-z0-9_-]+)\\b");
    private static final List<Pattern> MANAGER_NAME_PATTERNS = List.of(
            Pattern.compile("(?:直属负责人|负责人)\\s*(?:是|为|选|选择)?\\s*([\\p{IsHan}]{2,8})"),
            Pattern.compile("给\\s*([\\p{IsHan}]{2,8})(?:办理|审批|处理|确认)?"),
            Pattern.compile("由\\s*([\\p{IsHan}]{2,8})(?:审批|处理|确认|负责|办理)")
    );
    private static final List<Pattern> TODO_TARGET_NAME_PATTERNS = List.of(
            Pattern.compile("([\\p{IsHan}]{2,8})\\s*(?:目前|现在)?\\s*有(?:几个|多少)待办"),
            Pattern.compile("(?:帮我)?(?:看看|查询|统计)?\\s*([\\p{IsHan}]{2,8})\\s*(?:目前|现在)?\\s*有(?:几个|多少)待办"),
            Pattern.compile("([\\p{IsHan}]{2,8})\\s*当前\\s*有(?:几个|多少)待办")
    );
    private static final List<Pattern> PROFILE_TARGET_NAME_PATTERNS = List.of(
            Pattern.compile("([\\p{IsHan}]{2,8})\\s*(?:是哪个|是什么|在什么|在哪个|属于哪个|属于什么)?\\s*(?:部门|岗位|职位|公司|角色|手机号|手机|邮箱)"),
            Pattern.compile("([\\p{IsHan}]{2,8})\\s*的?\\s*(?:部门|岗位|职位|公司|角色|手机号|手机|邮箱)\\s*(?:是|为)?(?:什么|哪个)?"),
            Pattern.compile("(?:查询|看看|告诉我)?\\s*([\\p{IsHan}]{2,8})\\s*(?:信息|资料|详情)")
    );
    private static final List<Pattern> FOLLOW_UP_NAME_PATTERNS = List.of(
            Pattern.compile("^(?:那|那就|那再看下|那再看看)?\\s*([\\p{IsHan}]{2,8})\\s*呢[？?]?$"),
            Pattern.compile("^([\\p{IsHan}]{2,8})\\s*呢[？?]?$"),
            Pattern.compile("^(?:那|那就)?\\s*([\\p{IsHan}]{2,8})\\s*(?:怎么样|如何|情况呢)[？?]?$")
    );

    private final SystemUserMapper systemUserMapper;

    public AiUserReferenceResolver(SystemUserMapper systemUserMapper) {
        this.systemUserMapper = systemUserMapper;
    }

    /**
     * 从文本中解析直属负责人用户 ID。
     */
    public String resolveManagerUserId(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return "";
        }
        Matcher userIdMatcher = USER_ID_PATTERN.matcher(normalized);
        if (userIdMatcher.find()) {
            return userIdMatcher.group(1);
        }
        String displayName = extractManagerDisplayName(normalized);
        if (displayName.isBlank()) {
            return "";
        }
        String userId = systemUserMapper.selectEnabledUserIdByDisplayName(displayName);
        return userId == null ? "" : userId;
    }

    /**
     * 从文本中解析待办查询目标用户 ID。
     */
    public String resolveTodoTargetUserId(String content) {
        String displayName = resolveTodoTargetDisplayName(content);
        if (displayName.isBlank()) {
            return "";
        }
        String userId = systemUserMapper.selectEnabledUserIdByDisplayName(displayName);
        return userId == null ? "" : userId;
    }

    /**
     * 从文本中解析待办查询目标姓名。
     */
    public String resolveTodoTargetDisplayName(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank() || !normalized.contains("待办")) {
            return "";
        }
        for (Pattern pattern : TODO_TARGET_NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            if (!matcher.find()) {
                continue;
            }
            String candidate = matcher.group(1);
            if (candidate != null && !candidate.isBlank()) {
                return normalizeDisplayNameCandidate(candidate);
            }
        }
        return "";
    }

    /**
     * 从文本中解析资料查询目标用户 ID。
     */
    public String resolveProfileTargetUserId(String content, String currentUserId) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (referencesCurrentUser(normalized)) {
            return currentUserId == null ? "" : currentUserId.trim();
        }
        String displayName = resolveProfileTargetDisplayName(normalized);
        if (displayName.isBlank()) {
            return "";
        }
        String userId = systemUserMapper.selectEnabledUserIdByDisplayName(displayName);
        return userId == null ? "" : userId;
    }

    /**
     * 从文本中解析资料查询目标姓名。
     */
    public String resolveProfileTargetDisplayName(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank() || !containsProfileKeyword(normalized)) {
            return "";
        }
        if (referencesCurrentUser(normalized)) {
            return "";
        }
        for (Pattern pattern : PROFILE_TARGET_NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            if (!matcher.find()) {
                continue;
            }
            String candidate = matcher.group(1);
            if (candidate != null && !candidate.isBlank()) {
                return normalizeDisplayNameCandidate(candidate);
            }
        }
        return "";
    }

    /**
     * 从简短追问中提取姓名，例如“李四呢”“那王五呢”。
     */
    public String resolveFollowUpDisplayName(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return "";
        }
        for (Pattern pattern : FOLLOW_UP_NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            if (!matcher.find()) {
                continue;
            }
            String candidate = matcher.group(1);
            if (candidate != null && !candidate.isBlank()) {
                return normalizeDisplayNameCandidate(candidate);
            }
        }
        return "";
    }

    /**
     * 从自然语言里提取负责人姓名。
     */
    String extractManagerDisplayName(String content) {
        for (Pattern pattern : MANAGER_NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) {
                continue;
            }
            String candidate = matcher.group(1);
            if (candidate != null && !candidate.isBlank()) {
                return normalizeDisplayNameCandidate(candidate);
            }
        }
        return "";
    }

    private String normalizeDisplayNameCandidate(String candidate) {
        String normalized = candidate == null ? "" : candidate.trim();
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized.replaceFirst("(目前|现在|当前)$", "");
        normalized = normalized.replaceFirst("^(帮我|请帮我|麻烦帮我)", "");
        normalized = normalized.replaceFirst("(是什么|是哪个|在什么|在哪个|属于什么|属于哪个).*$", "");
        normalized = normalized.replaceFirst("(部门|岗位|职位|公司|角色|手机|手机号|邮箱|资料|信息|详情).*$", "");
        return normalized.trim();
    }

    private boolean referencesCurrentUser(String content) {
        return content.contains("我")
                && containsProfileKeyword(content)
                && !content.contains("给我")
                && !content.contains("帮我");
    }

    private boolean containsProfileKeyword(String content) {
        return content.contains("部门")
                || content.contains("岗位")
                || content.contains("职位")
                || content.contains("公司")
                || content.contains("角色")
                || content.contains("手机")
                || content.contains("手机号")
                || content.contains("邮箱")
                || content.contains("资料")
                || content.contains("信息")
                || content.contains("详情");
    }
}
