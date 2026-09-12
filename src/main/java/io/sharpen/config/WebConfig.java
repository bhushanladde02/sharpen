package io.sharpen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * {@code /sessions/} is served by the same handler as {@code /sessions}. Spring 6 stopped doing this by
 * default, so a typed or auto-completed trailing slash would 404. Matching the slash in the router (rather
 * than redirecting) means no {@code Location} header is ever built from request data — nothing to turn into
 * an open redirect. Spring Security resolves its path rules through the same parser, so the two agree.
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

    @Override
    @SuppressWarnings("deprecation") // still supported in Spring 6.x; revisit if a later major removes it
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PathPatternParser parser = new PathPatternParser();
        parser.setMatchOptionalTrailingSeparator(true);
        configurer.setPatternParser(parser);
    }
}
