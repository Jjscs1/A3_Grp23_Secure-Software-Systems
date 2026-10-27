package com.evp.secrecy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny servlet fakes (no Mockito) so the filters can be exercised without Spring. */
final class ServletFakes {
    private ServletFakes() { }

    /** A request whose body and parameters MUST NOT be read: touching them fails the test. */
    static HttpServletRequest request(String method, String uri, Map<String, String> headers, String remoteAddr) {
        Map<String, String> h = new LinkedHashMap<>(headers);
        return (HttpServletRequest) Proxy.newProxyInstance(ServletFakes.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getRemoteAddr", "getRemoteHost" -> remoteAddr;
                    case "getRemotePort" -> 54321;
                    case "getHeader" -> h.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(String.valueOf(a[0])))
                            .map(Map.Entry::getValue).findFirst().orElse(null);
                    case "getHeaders" -> Collections.enumeration(h.entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase(String.valueOf(a[0]))).map(Map.Entry::getValue).toList());
                    case "getHeaderNames" -> Collections.enumeration(h.keySet());
                    case "getInputStream", "getReader", "getParameterMap", "getParameter", "getParameterNames",
                         "getParameterValues", "getQueryString" ->
                            throw new AssertionError("the body, parameters and query string must never be read: " + m.getName());
                    default -> null;
                });
    }

    static final class Response {
        int status = 200;
        boolean committed;
        final StringWriter out = new StringWriter();
        final HttpServletResponse proxy = (HttpServletResponse) Proxy.newProxyInstance(ServletFakes.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "setStatus" -> status = (Integer) a[0];
                        case "getStatus" -> {
                            return status;
                        }
                        case "isCommitted" -> {
                            return committed;
                        }
                        case "getWriter" -> {
                            return new PrintWriter(out, true);
                        }
                        default -> { }
                    }
                    return null;
                });
    }
}
