package com.ticketflow.exception;

import org.springframework.http.HttpStatus;

public class UserDisabledException extends BusinessException {

    public UserDisabledException(String message) {
        super(HttpStatus.BAD_REQUEST, "USER_DISABLED", message);
    }
}
