package com.westflow.ai.service;

import com.westflow.system.user.mapper.SystemUserMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiUserReferenceResolverTest {

    @Test
    void shouldStripTemporalSuffixWhenResolvingTodoTargetDisplayName() {
        AiUserReferenceResolver resolver = new AiUserReferenceResolver(mock(SystemUserMapper.class));

        assertThat(resolver.resolveTodoTargetDisplayName("张三目前有几个待办")).isEqualTo("张三");
        assertThat(resolver.resolveTodoTargetDisplayName("李四当前有多少待办")).isEqualTo("李四");
    }
}
