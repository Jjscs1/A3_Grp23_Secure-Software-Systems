package com.evp.crypto;

/** Carries a fixed error code only: never the envelope contents or the underlying crypto message. */
public class InvalidEnvelopeException extends Exception {
    private final String code;

    public InvalidEnvelopeException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
