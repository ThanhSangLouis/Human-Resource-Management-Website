package org.example.hrmsystem.exception;

import java.lang.reflect.UndeclaredThrowableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Dữ liệu không hợp lệ", "errors", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    /** Tháng/ngày sai định dạng (vd. GET /api/payroll?month=...) — tránh 500 chung. */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateTimeParse(DateTimeParseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Tháng hoặc ngày không hợp lệ (dùng định dạng yyyy-MM)."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Forbidden"));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Không có quyền truy cập"));
    }

    /**
     * @PreAuthorize / method-security có thể bọc {@link AccessDeniedException} trong proxy
     * ({@link UndeclaredThrowableException}). Handler {@code Exception} toàn cục trước đây biến thành 500.
     */
    @ExceptionHandler(UndeclaredThrowableException.class)
    public ResponseEntity<Map<String, String>> handleUndeclaredFromProxy(UndeclaredThrowableException ex) {
        Throwable t = ex.getUndeclaredThrowable();
        for (; t != null; t = t.getCause()) {
            if (t instanceof AccessDeniedException ade) {
                return handleAccessDenied(ade);
            }
            if (t instanceof AuthorizationDeniedException) {
                return handleAuthorizationDenied((AuthorizationDeniedException) t);
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
    }

    /** Fallback: lỗi chưa khai báo riêng; ưu tiên nhận diện từ chuỗi cause (method-security / proxy). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        // HIGHEST_PRECEDENCE + Exception.class được chọn trước AuthExceptionHandler cho BadCredentialsException.
        if (ex instanceof BadCredentialsException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
        }
        if (ex instanceof DisabledException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Account is inactive"));
        }
        if (ex instanceof AuthenticationServiceException) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message",
                            "Authentication service is temporarily unavailable. Check database connection."));
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof AccessDeniedException ade) {
                return handleAccessDenied(ade);
            }
            if (t instanceof AuthorizationDeniedException ade) {
                return handleAuthorizationDenied(ade);
            }
            if (t instanceof BadCredentialsException) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid username or password"));
            }
            if (t instanceof DisabledException) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Account is inactive"));
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
    }
}
