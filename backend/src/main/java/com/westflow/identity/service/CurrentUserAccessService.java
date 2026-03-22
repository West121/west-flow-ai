package com.westflow.identity.service;

import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserAccessService {

    private final FixtureAuthService fixtureAuthService;
    private final SystemDepartmentMapper systemDepartmentMapper;

    public CurrentUserAccessService(
            FixtureAuthService fixtureAuthService,
            SystemDepartmentMapper systemDepartmentMapper
    ) {
        this.fixtureAuthService = fixtureAuthService;
        this.systemDepartmentMapper = systemDepartmentMapper;
    }

    public AccessPolicy resolveAccessPolicy() {
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

    private List<String> resolveDepartmentIdsWithDescendants(String rootDepartmentId) {
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

    public record AccessPolicy(
            boolean allAccess,
            List<String> companyIds,
            List<String> departmentIds,
            String currentCompanyId
    ) {
        public boolean restricted() {
            return !allAccess;
        }

        public boolean isEmpty() {
            return companyIds.isEmpty() && departmentIds.isEmpty();
        }

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

        public boolean canAccessCompany(String companyId) {
            return allAccess
                    || companyIds.contains(companyId)
                    || (currentCompanyId != null && currentCompanyId.equals(companyId));
        }
    }
}
