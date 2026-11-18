package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class PhoneAlreadyRegisteredException extends BusinessException {
    public PhoneAlreadyRegisteredException() {
        super("PHONE_ALREADY_REGISTERED", HttpStatus.CONFLICT, "El teléfono ya se encuentra registrado en el sistema");
    }
}
