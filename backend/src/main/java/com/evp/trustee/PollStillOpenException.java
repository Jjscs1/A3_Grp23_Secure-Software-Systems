package com.evp.trustee;
public class PollStillOpenException extends RuntimeException {
    public PollStillOpenException() {
        super("The poll is still open. Ballots can only be decrypted after it closes.");
    }
}