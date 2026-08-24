package com.quietterminal.ledger.security;

import java.time.Instant;
import java.util.List;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.quietterminal.ledger.repository.SessionRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionRepository sessionRepository;

    public JwtAuthenticationFilter(JwtService jwtService, SessionRepository sessionRepository) {
        this.jwtService = jwtService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                UUID sessionId = UUID.fromString(claims.getId());

                if (sessionRepository.existsByIdAndExpiresAtAfter(sessionId, Instant.now())) {
                    UUID userId = UUID.fromString(claims.get("uid", String.class));
                    String username = claims.getSubject();
                    String role = claims.get("role", String.class);
                    @SuppressWarnings("unchecked")
                    List<String> permissions = claims.get("permissions", List.class);
                    Set<String> permissionSet = permissions == null ? Set.of() : Set.copyOf(permissions);
                    LedgerPrincipal principal = new LedgerPrincipal(userId, username, sessionId, role,
                            permissionSet);

                    List<SimpleGrantedAuthority> authorities = permissionSet.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException | NullPointerException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
