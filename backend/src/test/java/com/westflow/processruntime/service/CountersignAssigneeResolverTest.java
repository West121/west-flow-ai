package com.westflow.processruntime.service;

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

@ExtendWith(MockitoExtension.class)
class CountersignAssigneeResolverTest {

    @Mock
    private SystemUserMapper systemUserMapper;

    @Mock
    private SystemDepartmentMapper systemDepartmentMapper;

    @InjectMocks
    private CountersignAssigneeResolver countersignAssigneeResolver;

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
