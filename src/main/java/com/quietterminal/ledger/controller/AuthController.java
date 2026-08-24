package com.quietterminal.ledger.controller;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.Session;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.error.RecoveryCodeInvalidException;
import com.quietterminal.ledger.repository.SessionRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;
import com.quietterminal.ledger.security.JwtService;
import com.quietterminal.ledger.security.LedgerPrincipal;
import com.quietterminal.ledger.security.RecoveryCodeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
public class AuthController {

        private final AuthenticationManager authenticationManager;
        private final UserCredentialsRepository credentialsRepository;
        private final UserRepository userRepository;
        private final SessionRepository sessionRepository;
        private final JwtService jwtService;
        private final RecoveryCodeService recoveryCodeService;

        public AuthController(AuthenticationManager authenticationManager,
                        UserCredentialsRepository credentialsRepository,
                        UserRepository userRepository,
                        SessionRepository sessionRepository,
                        JwtService jwtService,
                        RecoveryCodeService recoveryCodeService) {
                this.authenticationManager = authenticationManager;
                this.credentialsRepository = credentialsRepository;
                this.userRepository = userRepository;
                this.sessionRepository = sessionRepository;
                this.jwtService = jwtService;
                this.recoveryCodeService = recoveryCodeService;
        }

        @PostMapping("/auth/login")
        public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                        @RequestHeader(value = "User-Agent", required = false) String userAgent) {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

                UserCredentials credentials = credentialsRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));
                User user = userRepository.findById(credentials.getUserId())
                                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

                JwtService.IssuedToken issuedToken = jwtService.generateToken(user.getUUID(), credentials.getUsername(),
                                user.getRole().getName(), permissionNames(user));
                sessionRepository.save(new Session(issuedToken.sessionId(), user.getUUID(), issuedToken.issuedAt(),
                                issuedToken.expiresAt(), userAgent));

                return ResponseEntity.ok(new LoginResponse(issuedToken.token()));
        }

        @PostMapping("/auth/recovery-login")
        public ResponseEntity<LoginResponse> recoveryLogin(@Valid @RequestBody RecoveryLoginRequest request,
                        @RequestHeader(value = "User-Agent", required = false) String userAgent) {
                UserCredentials credentials = credentialsRepository.findByUsername(request.username())
                                .orElseThrow(() -> new BadCredentialsException("Invalid username or recovery code."));
                User user = userRepository.findById(credentials.getUserId())
                                .orElseThrow(() -> new BadCredentialsException("Invalid username or recovery code."));

                recoveryCodeService.redeem(user.getUUID(), request.code());

                JwtService.IssuedToken issuedToken = jwtService.generateToken(user.getUUID(), credentials.getUsername(),
                                user.getRole().getName(), permissionNames(user));
                sessionRepository.save(new Session(issuedToken.sessionId(), user.getUUID(), issuedToken.issuedAt(),
                                issuedToken.expiresAt(), userAgent));

                return ResponseEntity.ok(new LoginResponse(issuedToken.token()));
        }

        @PostMapping("/auth/logout")
        public ResponseEntity<Void> logout(@AuthenticationPrincipal LedgerPrincipal principal) {
                sessionRepository.deleteByIdAndUserId(principal.sessionId(), principal.userId());
                return ResponseEntity.noContent().build();
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<String> handleAuthenticationFailure(AuthenticationException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password.");
        }

        @ExceptionHandler(RecoveryCodeInvalidException.class)
        public ResponseEntity<String> handleRecoveryCodeInvalid(RecoveryCodeInvalidException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or recovery code.");
        }

        private static Set<String> permissionNames(User user) {
                return user.getRole().getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
        }

        public record LoginRequest(@NotBlank String username, @NotBlank String password) {
        }

        public record RecoveryLoginRequest(@NotBlank String username, @NotBlank String code) {
        }

        public record LoginResponse(String token) {
        }
}
