package com.billetera_digital.dto;

import com.billetera_digital.entity.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phone,
        String fullName,
        Role role
) {
}
