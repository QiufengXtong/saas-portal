package com.xtong.saas.system.auth.filter;

import com.xtong.saas.system.auth.handler.RestAuthenticationEntryPoint;
import com.xtong.saas.system.auth.model.AuthSession;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.auth.session.SessionStore;
import com.xtong.saas.system.auth.token.AccessTokenService;
import com.xtong.saas.system.tenant.context.TenantContextHolder;
import com.xtong.saas.system.tenant.context.TenantScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/** 校验 Bearer JWT 与 Redis 会话身份，并在受控租户和安全上下文中执行请求。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;
    private final SessionStore sessionStore;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            AccessTokenService accessTokenService,
            SessionStore sessionStore,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.accessTokenService = accessTokenService;
        this.sessionStore = sessionStore;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        try {
            if (authorization == null) {
                filterChain.doFilter(request, response);
                return;
            }
            Optional<String> token = bearerToken(authorization);
            if (token.isEmpty()) {
                unauthorized(request, response);
                return;
            }
            authenticateAndContinue(token.get(), request, response, filterChain);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAndContinue(
            String token,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws IOException, ServletException {
        AuthenticatedUser tokenClaims;
        try {
            tokenClaims = accessTokenService.parse(token);
        } catch (JwtException exception) {
            unauthorized(request, response);
            return;
        }
        if (tokenClaims == null) {
            unauthorized(request, response);
            return;
        }
        AuthSession session = sessionStore.find(tokenClaims.sessionId())
                .filter(found -> hasSameIdentity(found, tokenClaims))
                .orElse(null);
        if (session == null) {
            unauthorized(request, response);
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                session.tenantId(),
                session.userId(),
                session.sessionId(),
                session.username(),
                session.permissions());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.permissions().stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            TenantScope.run(principal.tenantId(), () -> invokeChain(filterChain, request, response));
        } catch (FilterChainException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof ServletException servletException) {
                throw servletException;
            }
            throw exception;
        }
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        authenticationEntryPoint.commence(
                request, response, new BadCredentialsException("Authentication is invalid"));
    }

    private static Optional<String> bearerToken(String authorization) {
        if (authorization.length() <= BEARER_PREFIX.length()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        return token.isBlank() || !token.equals(token.strip()) ? Optional.empty() : Optional.of(token);
    }

    private static boolean hasSameIdentity(AuthSession session, AuthenticatedUser tokenClaims) {
        return session.tenantId() == tokenClaims.tenantId()
                && session.userId() == tokenClaims.userId()
                && session.sessionId().equals(tokenClaims.sessionId())
                && session.username().equals(tokenClaims.username());
    }

    private static void invokeChain(
            FilterChain filterChain,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException exception) {
            throw new FilterChainException(exception);
        }
    }

    /** 仅用于跨越 TenantScope 的无受检异常函数边界并恢复 Servlet 异常语义。 */
    private static final class FilterChainException extends RuntimeException {

        private FilterChainException(Exception cause) {
            super(cause);
        }
    }
}
