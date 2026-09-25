package com.codewalnut.ats.security;

public class LoginRejectedException extends RuntimeException {

    public LoginRejectedException(String message) {
        super(message);
    }
}
