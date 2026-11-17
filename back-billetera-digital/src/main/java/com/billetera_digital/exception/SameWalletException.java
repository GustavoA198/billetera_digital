package com.billetera_digital.exception;

import org.springframework.http.HttpStatus;

public class SameWalletException extends BusinessException {
    public SameWalletException() {
        super("SAME_WALLET", HttpStatus.UNPROCESSABLE_CONTENT, "No se puede realizar la transacción, la billetera de origen y destino son la misma");
    }
}
