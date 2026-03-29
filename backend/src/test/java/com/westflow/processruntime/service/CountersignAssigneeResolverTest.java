package com.westflow.processruntime.service;

import com.westflow.processruntime.support.CountersignAssigneeResolver;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import com.westflow.system.user.mapper.SystemUserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountersignAssigneeResolverTest {

    @Mock
    private SystemUserMapper systemUserMapper;

    @Mock
    private SystemDepartmentMapper systemDepartmentMapper;

    @InjectMocks
    private CountersignAssigneeResolver countersignAssigneeResolver;

    @Test
    void shouldResolveRoleAssignmentAndDeduplicateUsers() {
        when(systemUserMapper.selectEnabledUserIdsByRoleRefs(List.of("role_manager", "OA_USER")))
                .thenReturn(List.of("usr_002", "usr_001", "usr_002"));

        List<String> resolved = countersignAssigneeResolver.resolve(
                Map.of(
                        "mode", "ROLE",
                        "roleCodes", List.of("role_manager", "OA_USER")
                ),
                Map.of()
        );

        assertThat(resolved).containsExactly("usr_002", "usr_001");
    }

    @Test
    void shouldResolveDepartmentAssignmentAndDeduplicateUsers() {
        when(systemUserMapper.selectEnabledUserIdsByDepartmentIds(List.of("dept_root")))
                .thenReturn(List.of("usr_002", "usr_001", "usr_002"));

        List<String> resolved = countersignAssigneeResolver.resolve(
                Map.of(
                        "mode", "DEPARTMENT",
                        "departmentRef", "dept_root"
                ),
                Map.of()
        );

        assertThat(resolved).containsExactly("usr_002", "usr_001");
    }

    @Test
    void shouldResolveDepartmentAndChildrenAssignment() {
        when(systemDepartmentMapper.selectDepartmentIdsByParentId("dept_root"))
                .thenReturn(List.of("dept_child"));
        when(systemDepartmentMapper.selectDepartmentIdsByParentId("dept_child"))
                .thenReturn(List.of());
        when(systemUserMapper.selectEnabledUserIdsByDepartmentIds(List.of("dept_root", "dept_child")))
                .thenReturn(List.of("usr_003", "usr_002", "usr_003"));

        List<String> resolved = countersignAssigneeResolver.resolve(
                Map.of(
                        "mode", "DEPARTMENT_AND_CHILDREN",
                        "departmentRef", "dept_root"
                ),
                Map.of()
        );

        assertThat(resolved).containsExactly("usr_003", "usr_002");
    }

    @Test
    void shouldResolveFormFieldAssignmentWithControlledFunctions() {
        List<String> resolved = countersignAssigneeResolver.resolve(
                Map.of(
                        "mode", "FORM_FIELD",
                        "formFieldKey", "candidateUserIds"
                ),
                Map.of(
                        "candidateUserIds", List.of("usr_002", "usr_003", "usr_002")
                )
        );

        assertThat(resolved).containsExactly("usr_002", "usr_003");
    }

    @Test
    void shouldResolveFormulaAssignmentWithControlledFunctions() {
        List<String> resolved = countersignAssigneeResolver.resolve(
                Map.of(
                        "mode", "FORMULA",
                        "formulaExpression", "ifElse(contains(reviewLevels, \"FINANCE\") && daysBetween(startDate, endDate) >= 4 && isBlank(extraApproverId), \"usr_002,usr_003\", extraApproverId)"
                ),
                Map.of(
                        "reviewLevels", List.of("FINANCE", "HR"),
                        "startDate", "2026-03-20",
                        "endDate", "2026-03-24",
                        "extraApproverId", ""
                )
        );

        assertThat(resolved).containsExactly("usr_002", "usr_003");
    }
}
