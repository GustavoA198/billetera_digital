package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends BusinessException {
    public IdempotencyKeyReusedException() {
        super("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT, "La clave de idempotencia ya ha sido utilizada");
    }
}
