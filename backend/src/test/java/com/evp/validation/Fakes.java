package com.evp.validation;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Tiny servlet fakes (no Mockito needed) for exercising the filter. */
final class Fakes {
    private Fakes() { }

    static HttpServletRequest request(String method, String uri, String contentType, byte[] body,
                                      Map<String, String[]> params, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(Fakes.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContentType" -> contentType;
                    case "getContentLengthLong" -> body == null ? -1L : (long) body.length;
                    case "getContentLength" -> body == null ? -1 : body.length;
                    case "getHeader" -> headers.get(String.valueOf(a[0]));
                    case "getParameterMap" -> params;
                    case "getInputStream" -> stream(body == null ? new byte[0] : body);
                    default -> null;
                });
    }

    static ServletInputStream stream(byte[] bytes) {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override public int read() { return in.read(); }
            @Override public boolean isFinished() { return in.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) { }
        };
    }

    static final class Response {
        int status = 200;
        final StringWriter out = new StringWriter();
        final Map<String, String> headers = new HashMap<>();
        final HttpServletResponse proxy = (HttpServletResponse) Proxy.newProxyInstance(Fakes.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "setStatus" -> status = (Integer) a[0];
                        case "setHeader" -> headers.put(String.valueOf(a[0]), String.valueOf(a[1]));
                        case "getWriter" -> {
                            return new PrintWriter(out, true);
                        }
                        default -> { }
                    }
                    return null;
                });
    }
}
