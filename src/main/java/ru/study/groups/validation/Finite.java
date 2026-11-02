package ru.study.groups.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FiniteValidator.class)
public @interface Finite {
    String message() default "Значение должно быть конечным числом";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

