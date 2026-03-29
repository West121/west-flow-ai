package com.westflow.processruntime.support;

import com.westflow.identity.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeParticipantDirectoryService {

    private final AuthUserMapper authUserMapper;
    private final JdbcTemplate jdbcTemplate;

    public String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }
        AuthUserMapper.AuthUserRecord user = authUserMapper.selectByUserId(userId);
        return user == null || user.displayName() == null || user.displayName().isBlank()
                ? userId
                : user.displayName();
    }

    public String resolveGroupDisplayName(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return groupId;
        }
        String name = jdbcTemplate.query(
                "SELECT department_name FROM wf_department WHERE id = ?",
                rs -> rs.next() ? rs.getString("department_name") : null,
                groupId
        );
        return name == null || name.isBlank() ? groupId : name;
    }
}
