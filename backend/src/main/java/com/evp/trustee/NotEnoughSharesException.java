package com.evp.trustee;
public class NotEnoughSharesException extends ShareException {
    public NotEnoughSharesException(int threshold, int supplied) {
        super("At least " + threshold + " different trustee shares are required (" + supplied + " supplied).");
    }
}