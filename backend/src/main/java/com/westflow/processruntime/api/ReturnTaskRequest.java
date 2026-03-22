package com.westflow.processruntime.api;

public record ReturnTaskRequest(
        String targetStrategy,
        String comment
) {
}
