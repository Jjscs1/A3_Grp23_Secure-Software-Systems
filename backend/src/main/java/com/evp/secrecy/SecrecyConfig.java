package com.evp.secrecy;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Wiring only. The ballot DataSources are created inside the bean methods and are NOT exposed as beans, so
 * Spring Boot's main (identity) DataSource and JPA setup are not affected.
 */
@Configuration
@PropertySource("classpath:secrecy.properties")
public class SecrecyConfig {
    private static final System.Logger LOG = System.getLogger("evp.access");

    @Bean
    FilterRegistrationBean<NetworkMetadataStripFilter> networkMetadataStripFilter() {
        FilterRegistrationBean<NetworkMetadataStripFilter> r = new FilterRegistrationBean<>(new NetworkMetadataStripFilter());
        r.addUrlPatterns("/api/ballots", "/api/ballots/*", "/api/ballotbox/*");
        r.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return r;
    }

    @Bean
    FilterRegistrationBean<PrivacyAccessLogFilter> privacyAccessLogFilter() {
        FilterRegistrationBean<PrivacyAccessLogFilter> r = new FilterRegistrationBean<>(
                new PrivacyAccessLogFilter(line -> LOG.log(System.Logger.Level.INFO, line)));
        r.addUrlPatterns("/api/*");
        r.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
        return r;
    }

    private static DriverManagerDataSource source(String url, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    @ConditionalOnProperty("evp.ballot-db.url")
    BallotWriter secretBallotWriter(@Value("${evp.ballot-db.url}") String url,
                              @Value("${evp.ballot-db.writer-user}") String user,
                              @Value("${evp.ballot-db.writer-password}") String password) {
        return new BallotWriter(source(url, user, password), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty("evp.ballot-db.url")
    BallotReader secretBallotReader(@Value("${evp.ballot-db.url}") String url,
                              @Value("${evp.ballot-db.counter-user}") String user,
                              @Value("${evp.ballot-db.counter-password}") String password) {
        return new BallotReader(source(url, user, password));
    }
}
