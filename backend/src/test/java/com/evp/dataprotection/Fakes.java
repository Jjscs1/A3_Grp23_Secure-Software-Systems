package com.evp.dataprotection;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

final class Fakes {
    private Fakes() { }

    static final class Out {
        int status = 200;
        final Map<String, String> headers = new HashMap<>();
        boolean chained;
    }

    static HttpServletRequest request(boolean secure, String uri, String query, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (p, m, a) -> switch (m.getName()) {
                    case "isSecure" -> secure;
                    case "getRequestURI" -> uri;
                    case "getQueryString" -> query;
                    case "getHeader" -> headers.get((String) a[0]);
                    default -> null;
                });
    }

    static HttpServletResponse response(Out out) {
        return (HttpServletResponse) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[] {HttpServletResponse.class},
                (p, m, a) -> {
                    if (m.getName().equals("setStatus")) {
                        out.status = (Integer) a[0];
                    } else if (m.getName().equals("setHeader")) {
                        out.headers.put((String) a[0], (String) a[1]);
                    }
                    return null;
                });
    }

    static FilterChain chain(Out out) {
        return (rq, rs) -> out.chained = true;
    }
}
