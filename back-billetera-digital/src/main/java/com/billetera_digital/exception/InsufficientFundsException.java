package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_CONTENT, "Fondos insuficientes para realizar la transacción");
    }
}
