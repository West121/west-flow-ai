package com.westflow.common.query;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * 分页大小约束校验器。
 */
public class AllowedPageSizeValidator implements ConstraintValidator<AllowedPageSize, Integer> {

    private static final Set<Integer> ALLOWED_VALUES = Set.of(10, 20, 50, 100);

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value != null && ALLOWED_VALUES.contains(value);
    }
}
