package edu.neusoft.newsplay;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> bad(IllegalArgumentException e, HttpServletRequest request) {
    log.warn("请求参数或业务校验失败 path={} message={}", request.getRequestURI(), e.getMessage());
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }
  @ExceptionHandler(SecurityException.class)
  ResponseEntity<?> forbidden(SecurityException e, HttpServletRequest request) {
    log.warn("未登录访问 path={} client={}", request.getRequestURI(), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录"));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e, HttpServletRequest request) {
    log.error("接口处理异常 method={} path={}", request.getMethod(), request.getRequestURI(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "系统处理失败，请查看后台日志"));
  }
}
