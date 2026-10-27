package com.evp.secrecy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Request view with the client's network address and proxy/client-identifying headers removed. */
final class StrippedRequest extends HttpServletRequestWrapper {
    static final Set<String> STRIPPED_HEADERS = Set.of(
            "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port", "x-real-ip", "forwarded",
            "via", "cf-connecting-ip", "true-client-ip", "x-client-ip", "x-cluster-client-ip", "fastly-client-ip",
            "user-agent");

    StrippedRequest(HttpServletRequest request) {
        super(request);
    }

    private static boolean stripped(String name) {
        return name != null && STRIPPED_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    @Override public String getRemoteAddr() { return ""; }
    @Override public String getRemoteHost() { return ""; }
    @Override public int getRemotePort() { return 0; }

    @Override
    public String getHeader(String name) {
        return stripped(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return stripped(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> kept = Collections.list(super.getHeaderNames()).stream()
                .filter(n -> !stripped(n)).collect(Collectors.toList());
        return Collections.enumeration(kept);
    }
}
