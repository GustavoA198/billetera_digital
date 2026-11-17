package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends BusinessException {
    public EmailAlreadyRegisteredException() {
        super("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT, "El correo electrónico ya está registrado");
    }
}
