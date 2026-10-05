package com.kislov.timesheets;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param dataFile    the JSON file that holds everything
 * @param openBrowser open the default browser at startup
 */
@ConfigurationProperties(prefix = "timesheets")
public record TimesheetsProperties(Path dataFile, boolean openBrowser) {
}
