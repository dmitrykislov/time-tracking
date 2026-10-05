package com.kislov.timesheets;

import com.kislov.timesheets.service.TimesheetService;
import com.kislov.timesheets.storage.TimesheetStore;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
@EnableConfigurationProperties(TimesheetsProperties.class)
public class TimesheetsApplication {

    private static final Logger log = LoggerFactory.getLogger(TimesheetsApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TimesheetsApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    TimesheetStore timesheetStore(TimesheetsProperties properties) {
        return new TimesheetStore(properties.dataFile());
    }

    @Bean
    TimesheetService timesheetService(TimesheetStore store, Clock clock) {
        return new TimesheetService(store, clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    void ready(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        var store = context.getBean(TimesheetStore.class);
        var properties = context.getBean(TimesheetsProperties.class);
        var url = "http://localhost:" + context.getEnvironment().getProperty("local.server.port", context.getEnvironment().getProperty("server.port", "8020"));
        log.info("Timesheet file: {}{}", store.file(), store.exists() ? "" : " (will be created on first save)");
        log.info("Open {} to track your time", url);
        if (properties.openBrowser()) {
            openBrowser(url, context.getEnvironment());
        }
    }

    private static void openBrowser(String url, Environment env) {
        var os = env.getProperty("os.name", "").toLowerCase();
        String[] command;
        if (os.contains("mac")) {
            command = new String[] {"open", url};
        } else if (os.contains("win")) {
            command = new String[] {"rundll32", "url.dll,FileProtocolHandler", url};
        } else {
            command = new String[] {"xdg-open", url};
        }
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.info("Could not open a browser automatically ({}); open {} yourself", e.getMessage(), url);
        }
    }
}
