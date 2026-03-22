package com.westflow.common.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldRejectPageLowerThanOne() {
        PageRequest request = new PageRequest(0, 20, null, List.of(), List.of(), List.of());

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("page");
    }

    @Test
    void shouldRejectUnsupportedPageSize() {
        PageRequest request = new PageRequest(1, 15, null, List.of(), List.of(), List.of());

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("pageSize");
    }

    @Test
    void shouldAllowMissingKeyword() {
        PageRequest request = new PageRequest(1, 20, null, List.of(), List.of(), List.of());

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectBetweenFilterWithoutTwoValues() throws Exception {
        String payload = """
                {
                  "page": 1,
                  "pageSize": 20,
                  "filters": [
                    {
                      "field": "createdAt",
                      "operator": "between",
                      "value": ["2026-03-01 00:00:00"]
                    }
                  ]
                }
                """;

        PageRequest request = objectMapper.readValue(payload, PageRequest.class);

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("between filters require exactly two values");
    }
}
