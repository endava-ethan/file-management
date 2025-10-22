package com.endava.fs.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ATTRIBUTE_USER = "authenticatedUser";
    private final String requiredKey;

    public ApiKeyInterceptor(String requiredKey) {
        this.requiredKey = requiredKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String providedKey = request.getHeader("X-Api-Key");
        if (requiredKey.equals(providedKey)) {
            request.setAttribute(REQUEST_ATTRIBUTE_USER, "api-key");
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"Invalid or missing API key\"}");
        response.getWriter().flush();
        return false;
    }
}
