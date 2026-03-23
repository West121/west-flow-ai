package com.westflow.processruntime.service;

import com.westflow.processruntime.service.append.DynamicBuildAppendRuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowableDynamicBuilderDelegateTest {

    @Mock
    private DynamicBuildAppendRuntimeService dynamicBuildAppendRuntimeService;

    @Mock
    private DelegateExecution delegateExecution;

    @InjectMocks
    private FlowableDynamicBuilderDelegate flowableDynamicBuilderDelegate;

    @Test
    void shouldDelegateDynamicBuilderExecutionToAppendRuntimeService() {
        when(delegateExecution.getProcessInstanceId()).thenReturn("instance_1");
        when(delegateExecution.getCurrentActivityId()).thenReturn("dynamic_builder_1");

        flowableDynamicBuilderDelegate.execute(delegateExecution);

        verify(dynamicBuildAppendRuntimeService).executeDynamicBuilder("instance_1", "dynamic_builder_1");
    }

    @Test
    void shouldIgnoreBlankExecutionContext() {
        when(delegateExecution.getProcessInstanceId()).thenReturn("");
        when(delegateExecution.getCurrentActivityId()).thenReturn(" ");

        flowableDynamicBuilderDelegate.execute(delegateExecution);

        verifyNoInteractions(dynamicBuildAppendRuntimeService);
    }
}
