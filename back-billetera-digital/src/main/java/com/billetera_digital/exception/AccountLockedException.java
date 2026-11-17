package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends BusinessException {

    public AccountLockedException() {
        super("ACCOUNT_LOCKED", HttpStatus.LOCKED, "La cuenta está bloqueada temporalmente");
    }
}
