package io.sharpen.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code /sessions/} is served by the same handler as {@code /sessions}. Spring 6 stopped matching a trailing
 * slash by default and Spring Framework 7 removed the parser option that restored it, so the framework's own
 * {@link UrlHandlerFilter} does the job now: it <em>wraps</em> the request with the slash trimmed — no redirect,
 * no {@code Location} header built from request data, nothing to turn into an open redirect (the reason the
 * hand-written filter was removed on day 4). It runs before Spring Security so the path rules see the same
 * trimmed path the controllers do.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PageViewInterceptor pageViews;

    public WebConfig(PageViewInterceptor pageViews) {
        this.pageViews = pageViews;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pageViews);
    }

    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        UrlHandlerFilter filter = UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build();
        FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);   // ahead of the security filter chain (-100)
        registration.addUrlPatterns("/*");
        return registration;
    }
}
