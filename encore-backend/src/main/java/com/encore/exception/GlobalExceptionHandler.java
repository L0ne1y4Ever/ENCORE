package com.encore.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.encore.common.ApiResponse;
import com.encore.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleNotLoginException() {
        return ApiResponse.fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleNoPermissionException() {
        return ApiResponse.fail(ErrorCode.FORBIDDEN, "当前账号无访问权限");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception exception) {
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, "请求参数不合法");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误");
    }
}
