package com.endava.fs.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class ApiKeyInterceptor implements WebFilter {

    public static final String REQUEST_ATTRIBUTE_USER = "authenticatedUser";
    private static final String PROTECTED_PATH_PREFIX = "/fs";
    private final String requiredKey;

    public ApiKeyInterceptor(String requiredKey) {
        this.requiredKey = requiredKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PROTECTED_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String providedKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        if (requiredKey.equals(providedKey)) {
            exchange.getAttributes().put(REQUEST_ATTRIBUTE_USER, "api-key");
            return chain.filter(exchange);
        }

        byte[] payload = "{\"message\":\"Invalid or missing API key\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
