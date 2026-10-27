package com.evp.dataprotection;

/** Deliberately vague: never includes plaintext, keys or ciphertext. */
public class DataProtectionException extends RuntimeException {
    public DataProtectionException(String message) {
        super(message);
    }
}
