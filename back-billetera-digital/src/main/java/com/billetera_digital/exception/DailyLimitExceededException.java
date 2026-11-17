package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class DailyLimitExceededException extends BusinessException {
    public DailyLimitExceededException() {
        super("DAILY_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_CONTENT, "Se ha excedido el límite diario de dinero ($2.000.000) para transacciones");
    }
}
