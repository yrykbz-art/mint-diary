package local.mintdiary;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;

    public LocalOriginFilter(@Value("${app.allowed-origins}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        String origin = request.getHeader("Origin");
        boolean writes = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (writes && origin != null && !allowedOrigins.contains(origin)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"不允许此页面修改本地日记\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
