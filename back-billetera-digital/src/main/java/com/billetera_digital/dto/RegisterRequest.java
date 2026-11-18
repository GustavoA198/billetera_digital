package com.billetera_digital.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 255, message = "El correo no puede superar 255 caracteres")
        String email,

        @Pattern(
                regexp = "^3\\d{9}$",
                message = "El teléfono debe comenzar con 3 y tener 10 dígitos"
        )
        @NotBlank(message = "El teléfono es obligatorio")
        String phone,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        String fullName,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72,message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password

) {
}
