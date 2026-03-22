package com.westflow.identity.service;

import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前登录人的数据权限解析服务。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserAccessService {

    private final FixtureAuthService fixtureAuthService;
    private final SystemDepartmentMapper systemDepartmentMapper;

    /**
     * 解析当前登录人的数据权限策略。
     */
    public AccessPolicy resolveAccessPolicy() {
        // 先把当前人的公司、部门、全局权限统一收敛成一个可复用策略对象。
        CurrentUserResponse currentUser = fixtureAuthService.currentUser();
        boolean allAccess = false;
        LinkedHashSet<String> companyIds = new LinkedHashSet<>();
        LinkedHashSet<String> departmentIds = new LinkedHashSet<>();
        String currentCompanyId = currentUser.companyId();

        for (CurrentUserResponse.DataScope dataScope : currentUser.dataScopes()) {
            switch (dataScope.scopeType()) {
                case "ALL" -> allAccess = true;
                case "DEPARTMENT" -> departmentIds.add(dataScope.scopeValue());
                case "DEPARTMENT_AND_CHILDREN" -> departmentIds.addAll(
                        resolveDepartmentIdsWithDescendants(dataScope.scopeValue())
                );
                case "COMPANY" -> companyIds.add(dataScope.scopeValue());
                default -> {
                }
            }
        }

        return new AccessPolicy(
                allAccess,
                List.copyOf(companyIds),
                List.copyOf(departmentIds),
                currentCompanyId
        );
    }

    /**
     * 递归展开指定部门及其子部门。
     */
    private List<String> resolveDepartmentIdsWithDescendants(String rootDepartmentId) {
        // 这里用广度优先把子部门全部展开，避免列表查询漏数据。
        LinkedHashSet<String> collected = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(rootDepartmentId);

        while (!queue.isEmpty()) {
            String departmentId = queue.poll();
            if (!collected.add(departmentId)) {
                continue;
            }
            queue.addAll(systemDepartmentMapper.selectDepartmentIdsByParentId(departmentId));
        }

        return List.copyOf(collected);
    }

    /**
     * 当前登录人的访问策略。
     */
    public record AccessPolicy(
            boolean allAccess,
            List<String> companyIds,
            List<String> departmentIds,
            String currentCompanyId
    ) {
        /**
         * 是否不是全量权限。
         */
        public boolean restricted() {
            return !allAccess;
        }

        /**
         * 是否没有任何可见范围。
         */
        public boolean isEmpty() {
            return companyIds.isEmpty() && departmentIds.isEmpty();
        }

        /**
         * 生成可见公司集合。
         */
        public List<String> companyViewIds() {
            if (currentCompanyId == null || currentCompanyId.isBlank()) {
                return companyIds;
            }
            if (companyIds.contains(currentCompanyId)) {
                return companyIds;
            }
            LinkedHashSet<String> visibleCompanies = new LinkedHashSet<>(companyIds);
            visibleCompanies.add(currentCompanyId);
            return List.copyOf(visibleCompanies);
        }

        /**
         * 判断是否可访问指定公司。
         */
        public boolean canAccessCompany(String companyId) {
            return allAccess
                    || companyIds.contains(companyId)
                    || (currentCompanyId != null && currentCompanyId.equals(companyId));
        }
    }
}
