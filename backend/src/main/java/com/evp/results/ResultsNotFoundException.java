package com.evp.results;

/** SEC-18: no publication for that election / sequence (HTTP 404). */
public class ResultsNotFoundException extends RuntimeException {

    public ResultsNotFoundException() {
        super("No results publication found.");
    }
}