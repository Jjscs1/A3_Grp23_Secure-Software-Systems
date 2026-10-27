package com.evp.tally;

public class TallyNotFoundException extends RuntimeException {

    public TallyNotFoundException() {
        super("No published result with that id.");
    }
}