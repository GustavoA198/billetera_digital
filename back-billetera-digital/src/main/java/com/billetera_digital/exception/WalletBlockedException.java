package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class WalletBlockedException extends BusinessException {
    public WalletBlockedException() {
        super("WALLET_BLOCKED", HttpStatus.UNPROCESSABLE_CONTENT, "La billetera está bloqueada");
    }
}
