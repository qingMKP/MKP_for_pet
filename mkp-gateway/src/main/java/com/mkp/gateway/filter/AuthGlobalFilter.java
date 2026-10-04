package com.mkp.gateway.filter;
import com.mkp.common.constant.RoleConstants;
import com.mkp.common.util.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> ALLOW_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/post/list",
            "/api/posting/list",
            "/api/posting/stream",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Allow public paths
        for (String allowPath : ALLOW_PATHS) {
            if (path.startsWith(allowPath)) {
                return chain.filter(exchange);
            }
        }

        // Allow GET post detail (public viewing)
        if ("GET".equalsIgnoreCase(exchange.getRequest().getMethodValue())
                && path.matches("/api/post/\\d+$")) {
            return chain.filter(exchange);
        }

        // Check JWT token: header first, then query param (for SSE/EventSource)
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // Fallback: check query param for SSE EventSource which cannot set headers
            token = exchange.getRequest().getQueryParams().getFirst("token");
        }

        if (token == null || !JwtUtil.validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Role check for admin paths
        if (path.startsWith("/api/admin/")) {
            String role = JwtUtil.getRole(token);
            if (!RoleConstants.ADMIN.equals(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        // Forward user info to downstream services
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(JwtUtil.getUserId(token)))
                .header("X-Username", JwtUtil.getUsername(token))
                .header("X-User-Role", JwtUtil.getRole(token))
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
