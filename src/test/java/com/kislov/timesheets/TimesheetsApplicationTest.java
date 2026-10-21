package com.kislov.timesheets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimesheetsApplicationTest {

    @Test
    void picksTheBrowserOpenerForEachOperatingSystem() {
        assertThat(TimesheetsApplication.browserCommand("Mac OS X", "http://localhost:8020")).containsExactly("open", "http://localhost:8020");
        assertThat(TimesheetsApplication.browserCommand("Windows 11", "http://localhost:8020"))
                .containsExactly("rundll32", "url.dll,FileProtocolHandler", "http://localhost:8020");
        assertThat(TimesheetsApplication.browserCommand("Linux", "http://localhost:8020")).containsExactly("xdg-open", "http://localhost:8020");
        assertThat(TimesheetsApplication.browserCommand("", "u")).containsExactly("xdg-open", "u");
    }
}
