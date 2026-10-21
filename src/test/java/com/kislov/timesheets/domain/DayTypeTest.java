package com.kislov.timesheets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DayTypeTest {

    /** The rule table behind leave handling: which kinds are leave, and which take time off the target. */
    @Test
    void leaveKindsAndTargetReduction() {
        assertThat(DayType.WORK.isLeave()).isFalse();
        assertThat(DayType.WORK.reducesTarget()).isFalse();
        assertThat(DayType.WORK.label()).isEqualTo("Work");

        for (var type : new DayType[] {DayType.PUBLIC_HOLIDAY, DayType.SICK, DayType.PERSONAL_LEAVE, DayType.ANNUAL_LEAVE}) {
            assertThat(type.isLeave()).as(type.name()).isTrue();
            assertThat(type.reducesTarget()).as(type.name()).isTrue();
        }
        assertThat(DayType.TIME_IN_LIEU.isLeave()).isTrue();
        assertThat(DayType.TIME_IN_LIEU.reducesTarget()).isFalse();
        assertThat(DayType.TIME_IN_LIEU.label()).isEqualTo("Time in lieu");
        assertThat(DayType.PUBLIC_HOLIDAY.label()).isEqualTo("Public holiday");
    }
}
