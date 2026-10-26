package com.evp.web;

/** Error body for every API error: a fixed code, and sometimes a short detail. Never a stack trace. */
public record ApiError(String error, String detail) {

    public static ApiError of(String error) {
        return new ApiError(error, null);
    }
}
