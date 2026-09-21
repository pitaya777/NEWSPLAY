package edu.neusoft.newsplay;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long started = System.currentTimeMillis();
    try {
      chain.doFilter(request, response);
    } finally {
      String path = request.getRequestURI();
      if (path.startsWith("/api/") && !path.startsWith("/api/media/")) {
        log.info("HTTP {} {} status={} durationMs={} client={}", request.getMethod(), path,
            response.getStatus(), System.currentTimeMillis() - started, clientIp(request));
      }
    }
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
  }
}
