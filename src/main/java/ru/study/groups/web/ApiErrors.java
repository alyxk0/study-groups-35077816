package ru.study.groups.web;

import jakarta.persistence.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import ru.study.groups.service.BusinessException;
import java.util.*;

@RestControllerAdvice
public class ApiErrors {
    private ResponseEntity<?> error(HttpStatus status, String message, Map<String,String> fields) {
        return ResponseEntity.status(status).body(Map.of("message", message, "fields", fields));
    }
    @ExceptionHandler(BusinessException.class) ResponseEntity<?> business(BusinessException ex) { return error(ex.getStatus(), ex.getMessage(), Map.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var fields = new LinkedHashMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Проверьте значения полей", fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<?> invalid(Exception ex) { return error(HttpStatus.BAD_REQUEST, "Некорректные данные: проверьте числа, даты и обязательные поля", Map.of()); }
    @ExceptionHandler(ConstraintViolationException.class) ResponseEntity<?> constraint(ConstraintViolationException ex) {
        var fields = new LinkedHashMap<String,String>();
        ex.getConstraintViolations().forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        return error(HttpStatus.BAD_REQUEST, "Нарушены ограничения объекта", fields);
    }
    @ExceptionHandler({DataIntegrityViolationException.class, org.springframework.transaction.TransactionSystemException.class})
    ResponseEntity<?> integrity(Exception ex) { return error(HttpStatus.CONFLICT, "Операция отменена: объект используется, логин занят или нарушено ограничение базы данных", Map.of()); }
    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    ResponseEntity<?> stale(Exception ex) { return error(HttpStatus.CONFLICT, "Объект изменён другим пользователем. Обновите данные.", Map.of()); }
    @ExceptionHandler({PessimisticLockException.class, CannotAcquireLockException.class})
    ResponseEntity<?> locked(Exception ex) { return error(HttpStatus.CONFLICT, "Объект сейчас изменяется. Повторите операцию.", Map.of()); }
    @ExceptionHandler(ArithmeticException.class) ResponseEntity<?> overflow(ArithmeticException ex) { return error(HttpStatus.BAD_REQUEST, "Операция превысит допустимый диапазон числового поля", Map.of()); }
}
