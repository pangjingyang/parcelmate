package com.pang.parcelmate.web;

import com.pang.parcelmate.service.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 统一异常出口。
 *
 * <p>没有它的话，Service 抛异常前端会收到一大段 Java 堆栈。
 * 有了它，前端永远只收到 {"error": "一句人话"}。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 业务错误：400，告诉用户哪里不对。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, String>> handleBiz(BizException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** 还没实现的功能（W2 的接单等）：501，前端看到"功能开发中"。 */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, String>> handleUnsupported(UnsupportedOperationException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", e.getMessage() == null ? "功能开发中" : e.getMessage()));
    }

    /** 兜底：500，但只把异常类型给前端，堆栈留在服务端日志里。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器出错了：" + e.getClass().getSimpleName()));
    }
}
