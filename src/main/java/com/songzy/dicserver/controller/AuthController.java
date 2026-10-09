package com.songzy.dicserver.controller;

import com.songzy.dicserver.dto.AuthResponse;
import com.songzy.dicserver.dto.AuthUserDTO;
import com.songzy.dicserver.dto.LoginRequest;
import com.songzy.dicserver.dto.RefreshTokenRequest;
import com.songzy.dicserver.dto.RegisterRequest;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import com.songzy.dicserver.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.username().trim(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username().trim(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public AuthUserDTO me(@AuthenticationPrincipal CurrentUserPrincipal currentUser) {
        return new AuthUserDTO(currentUser.userId(), currentUser.username());
    }
}
