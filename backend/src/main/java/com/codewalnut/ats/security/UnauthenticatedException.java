package com.codewalnut.ats.security;

/** The request has no session, or the session's user is no longer provisioned or active. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
