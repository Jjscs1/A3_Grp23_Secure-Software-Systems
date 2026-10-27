package com.evp.identity.session;

public class SessionRejected extends RuntimeException {
    public SessionRejected() { super("A fresh sign-in is required."); }
}
