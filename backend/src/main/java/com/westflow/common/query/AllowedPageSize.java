package com.westflow.common.query;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制分页大小只能使用预设值。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AllowedPageSizeValidator.class)
public @interface AllowedPageSize {

    String message() default "pageSize only accepts 10, 20, 50 or 100";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
