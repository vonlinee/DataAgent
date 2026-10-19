/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
@Component
public class RequestLogWebFilter implements WebFilter {

    private static final List<String> STATIC_SUFFIXES = List.of(
            ".css", ".js", ".map", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".ico", ".webp", ".woff", ".woff2", ".ttf", ".eot", ".html", ".htm"
    );

    private static final List<String> STATIC_PREFIXES = List.of(
            "/static/", "/public/", "/assets/", "/webjars/", "/favicon.ico"
    );

    private static final List<String> PROXY_HEADERS = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    /**
     * 各字段对齐宽度
     */
    private static final int IP_WIDTH = 15;      // IPv4 最长 15 位
    private static final int HANDLER_WIDTH = 40; // 类名#方法名常见长度

    @Override
    @NonNull
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().value();

        if (isStaticResource(path)) {
            return chain.filter(exchange);
        }

        final ServerHttpRequest request = exchange.getRequest();
        final String ip = padRight(resolveClientIp(request), IP_WIDTH);
        final String method = request.getMethod().name();

        int[] statusHolder = new int[]{200};
        ServerHttpResponseDecorator responseDecorator =
                new ServerHttpResponseDecorator(exchange.getResponse()) {
                    @Override
                    @NonNull
                    public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                        if (getStatusCode() != null) {
                            statusHolder[0] = getStatusCode().value();
                        }
                        return super.writeWith(body);
                    }

                    @Override
                    @NonNull
                    public Mono<Void> setComplete() {
                        if (getStatusCode() != null) {
                            statusHolder[0] = getStatusCode().value();
                        }
                        return super.setComplete();
                    }
                };

        ServerWebExchange newExchange = exchange.mutate().response(responseDecorator).build();

        return chain.filter(newExchange)
                .doFinally(signalType -> {
                    String handler = padRight(resolveHandler(exchange), HANDLER_WIDTH);
                    // 格式：ip status 方法 处理器 路径
                    log.info("{} {} {} {} {}",
                            ip,
                            statusHolder[0],
                            method,
                            handler,
                            path
                    );
                });
    }

    /**
     * 右填充空格到指定宽度
     */
    private String padRight(String s, int width) {
        if (s == null) {
            s = "-";
        }
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }

    private boolean isStaticResource(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String lower = path.toLowerCase();
        for (String prefix : STATIC_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        for (String suffix : STATIC_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        for (String header : PROXY_HEADERS) {
            String value = headers.getFirst(header);
            if (value != null && isValidIp(value)) {
                if ("X-Forwarded-For".equalsIgnoreCase(header) || "HTTP_X_FORWARDED_FOR".equalsIgnoreCase(header)) {
                    String first = value.split(",")[0].trim();
                    if (isValidIp(first)) {
                        return first;
                    }
                }
                return value.trim();
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "-";
    }

    private boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip.trim());
    }

    private String resolveHandler(ServerWebExchange exchange) {
        Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod hm) {
            return hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
        }
        if (handler != null) {
            return handler.getClass().getSimpleName();
        }
        return "-";
    }
}
