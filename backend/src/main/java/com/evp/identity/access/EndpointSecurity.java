package com.evp.identity.access;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

/** A mapped method must explicitly opt in to method security, including public endpoints. */
@Configuration
public class EndpointSecurity {
    @Bean
    @org.springframework.context.annotation.Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor denyUnannotatedEndpoints() {
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                // Restrict this advisor to application handlers; never intercept Boot's error handler.
                return targetClass.getPackageName().startsWith("com.evp.")
                    && AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
                    && !AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                    && !AnnotatedElementUtils.hasAnnotation(targetClass, PreAuthorize.class);
            }
        };
        var advisor = new DefaultPointcutAdvisor(pointcut, (MethodInterceptor) invocation -> {
            throw new AccessDeniedException("Endpoint requires an explicit access policy");
        });
        advisor.setOrder(0);
        return advisor;
    }
}
