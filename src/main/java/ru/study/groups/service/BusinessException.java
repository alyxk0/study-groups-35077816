package ru.study.groups.service;
import org.springframework.http.HttpStatus;
import lombok.Getter;
@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    public BusinessException(HttpStatus status, String message) { super(message); this.status = status; }
    public static BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND, "Объект не найден"); }
    public static BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }
    public static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }
}

