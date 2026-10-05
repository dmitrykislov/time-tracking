package ai.totallywild.timesheets.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.totallywild.timesheets.TimesheetsApplication;
import ai.totallywild.timesheets.storage.TimesheetStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the real app against a temp file with the clock pinned to Wednesday 30 Sep 2026, 10:15. */
@SpringBootTest(classes = {TimesheetsApplication.class, TimesheetApiTest.FixedClock.class})
@AutoConfigureMockMvc
class TimesheetApiTest {

    static final ZoneId ZONE = ZoneId.of("Australia/Brisbane");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    static Path dataFile;

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atTime(10, 15).atZone(ZONE).toInstant(), ZONE);
        }
    }

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) throws Exception {
        dataFile = Files.createTempDirectory("timesheets-api").resolve("timesheet.json");
        registry.add("timesheets.data-file", () -> dataFile.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    TimesheetStore store;

    @BeforeEach
    void reset() throws Exception {
        Files.deleteIfExists(dataFile);
    }

    @Test
    void viewOfAnEmptySheet() throws Exception {
        mvc.perform(get("/api/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-09-30"))
                .andExpect(jsonPath("$.now").value("10:15"))
                .andExpect(jsonPath("$.config.weeklyTargetHours").value(40.0))
                .andExpect(jsonPath("$.weeks.length()").value(1))
                .andExpect(jsonPath("$.weeks[0].start").value("2026-09-28"))
                .andExpect(jsonPath("$.weeks[0].current").value(true))
                .andExpect(jsonPath("$.weeks[0].upcoming").value(false))
                .andExpect(jsonPath("$.totals.completedWeeks").value(0))
                .andExpect(jsonPath("$.totals.workedToDateMinutes").value(0))
                .andExpect(jsonPath("$.todayStats.entry").doesNotExist())
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void theFourButtonsAndTheResultingFile() throws Exception {
        mvc.perform(post("/api/today/start-day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayStats.inProgress").value(true))
                .andExpect(jsonPath("$.todayStats.entry.start").value("10:15"));

        mvc.perform(post("/api/today/start-break"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayStats.onBreak").value(true));

        mvc.perform(post("/api/today/start-break"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Already on a break"));

        mvc.perform(post("/api/today/end-break")).andExpect(status().isOk());
        mvc.perform(post("/api/today/end-day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayStats.inProgress").value(false))
                .andExpect(jsonPath("$.todayStats.entry.finish").value("10:15"));
        mvc.perform(post("/api/today/end-day")).andExpect(status().isBadRequest());

        mvc.perform(post("/api/today/nonsense"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown action 'nonsense'"));

        assertThat(Files.readString(dataFile)).contains("\"start\" : \"10:15\"");
    }

    @Test
    void saveEditAndDeleteADay() throws Exception {
        var monday = """
                { "date": "2026-09-28", "type": "WORK", "start": "08:30", "finish": "17:15",
                  "breaks": [ { "start": "12:00", "finish": "12:45", "note": "lunch" } ] }
                """;
        mvc.perform(put("/api/days/2026-09-28").contentType(MediaType.APPLICATION_JSON).content(monday))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[0].workedMinutes").value(480))
                .andExpect(jsonPath("$.weeks[0].days[0].entry.breaks[0].note").value("lunch"))
                .andExpect(jsonPath("$.weeks[0].days[0].note").doesNotExist());

        mvc.perform(put("/api/days/2026-09-29").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"date\": \"2026-09-29\", \"type\": \"SICK\", \"hours\": 4 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[0].leaveMinutes").value(240))
                .andExpect(jsonPath("$.weeks[0].targetMinutes").value(2160))
                .andExpect(jsonPath("$.weeks[0].remainingMinutes").value(2160 - 480));

        mvc.perform(get("/api/timesheet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[1].hours").value(4.0));

        mvc.perform(delete("/api/days/2026-09-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[0].leaveMinutes").value(0));

        mvc.perform(delete("/api/days/2026-09-29"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nothing recorded on 2026-09-29"));
    }

    @Test
    void ruleViolationsComeBackAs400WithTheRule() throws Exception {
        mvc.perform(put("/api/days/2026-09-28").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"date\": \"2026-09-28\", \"type\": \"WORK\", \"start\": \"17:00\", \"finish\": \"09:00\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Finish (09:00) is before start (17:00)"));

        mvc.perform(put("/api/days/2026-09-28").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"date\": \"2026-09-29\", \"type\": \"SICK\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The entry is for 2026-09-29 but was sent to 2026-09-28"));

        mvc.perform(put("/api/days/not-a-date").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/days/2026-09-28").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());

        assertThat(store.load().days()).isEmpty();
    }

    @Test
    void notesAreSavedAndRemovedThroughTheApi() throws Exception {
        mvc.perform(put("/api/notes/2026-09-28").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"text\": \"Deployed v2.\\nPaired with Sam.\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[0].days[0].note").value("Deployed v2.\nPaired with Sam."))
                .andExpect(jsonPath("$.weeks[0].days[0].entry").doesNotExist());

        assertThat(Files.readString(dataFile)).contains("\"notes\"").contains("\"text\" : \"Deployed v2.\\nPaired with Sam.\"");

        mvc.perform(put("/api/notes/2026-09-28").contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[0].days[0].note").doesNotExist());
        assertThat(store.load().notes()).isEmpty();

        mvc.perform(put("/api/notes/nope").contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"x\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configIsSavedAndApplied() throws Exception {
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"weeklyTargetHours\": 38, \"standardDayHours\": 7.6, \"startDate\": \"2026-09-21\", \"openingBalanceHours\": 1.5 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks.length()").value(2))
                .andExpect(jsonPath("$.weeks[1].carryInMinutes").value(90))
                .andExpect(jsonPath("$.weeks[1].targetMinutes").value(2280))
                .andExpect(jsonPath("$.totals.completedWeeks").value(1))
                .andExpect(jsonPath("$.totals.balanceMinutes").value(90 - 2280));

        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"weeklyTargetHours\": 0, \"standardDayHours\": 8 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Weekly target must be greater than zero"));
    }
}
