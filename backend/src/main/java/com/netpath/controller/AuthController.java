package com.netpath.controller;

import com.netpath.dto.AuthRequest;
import com.netpath.dto.AuthResponse;
import com.netpath.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        String name = request.getOrDefault("name", email.split("@")[0]);

        AuthRequest authRequest = new AuthRequest(email, password);
        return ResponseEntity.ok(authService.register(authRequest, name));
    }
}
