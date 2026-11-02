package ru.study.groups.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class FiniteValidator implements ConstraintValidator<Finite, Number> {
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        return value == null || Double.isFinite(value.doubleValue());
    }
}

