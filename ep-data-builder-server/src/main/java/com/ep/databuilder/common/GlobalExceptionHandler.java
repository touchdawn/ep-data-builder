package com.ep.databuilder.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> invalid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数错误" : fe.getField() + " " + fe.getDefaultMessage();
        return Result.error(1001, msg);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public Result<Void> conflict(ObjectOptimisticLockingFailureException e) {
        return Result.error(1002, "数据已被他人修改，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> unknown(Exception e) {
        log.error("unhandled exception", e);
        return Result.error(1999, "系统异常：" + e.getMessage());
    }
}
