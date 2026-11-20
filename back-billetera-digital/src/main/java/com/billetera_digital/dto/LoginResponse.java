package com.billetera_digital.dto;

public record LoginResponse (
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
