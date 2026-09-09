package io.sharpen.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.logging.Level;

/** Renders the monthly report to PDF from the {@code report-pdf} template (strict XHTML, print-sized). */
@Service
public class PdfService {

    static {
        // openhtmltopdf logs through java.util.logging with its own console handler, bypassing Spring's log
        // configuration. Route it to SLF4J so `logging.level.com.openhtmltopdf` in application.yml applies.
        XRLog.setLoggerImpl(new XRLogger() {
            @Override public void log(String where, Level level, String msg) { log(where, level, msg, null); }
            @Override public void log(String where, Level level, String msg, Throwable t) {
                Logger log = LoggerFactory.getLogger(where);
                if (level.intValue() >= Level.SEVERE.intValue()) log.error(msg, t);
                else if (level.intValue() >= Level.WARNING.intValue()) log.warn(msg, t);
                else if (level.intValue() >= Level.INFO.intValue()) log.info(msg);
                else log.debug(msg);
            }
            @Override public void setLevel(String logger, Level level) { /* levels come from application.yml */ }
            @Override public boolean isLogLevelEnabled(com.openhtmltopdf.util.Diagnostic d) {
                Logger log = LoggerFactory.getLogger(d.getLogMessageId().getWhere());
                int v = d.getLevel().intValue();
                return v >= Level.SEVERE.intValue() ? log.isErrorEnabled()
                        : v >= Level.WARNING.intValue() ? log.isWarnEnabled()
                        : v >= Level.INFO.intValue() ? log.isInfoEnabled() : log.isDebugEnabled();
            }
        });
    }

    private final TemplateEngine templates;
    private final String siteHost;

    public PdfService(TemplateEngine templates,
                      @org.springframework.beans.factory.annotation.Value("${sharpen.site-host:localhost:8080}") String siteHost) {
        this.templates = templates;
        this.siteHost = siteHost;
    }

    public byte[] monthlyReport(ReportModel model) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("r", model);
        ctx.setVariable("siteHost", siteHost);
        String html = templates.process("report-pdf", ctx);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }
}
