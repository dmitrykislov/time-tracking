package com.kislov.timesheets.domain;

/**
 * What kind of day an entry describes.
 *
 * <p>Leave types that {@link #reducesTarget() reduce the target} take a standard day (or the
 * entry's own hours) off that week's target, so a public holiday in a 40h week leaves 32h to work.
 * {@link #TIME_IN_LIEU} does not touch the target: the day is spent from your positive balance.
 */
public enum DayType {
    WORK("Work", false),
    PUBLIC_HOLIDAY("Public holiday", true),
    SICK("Sick leave", true),
    PERSONAL_LEAVE("Personal leave", true),
    ANNUAL_LEAVE("Annual leave", true),
    TIME_IN_LIEU("Time in lieu", false);

    private final String label;
    private final boolean reducesTarget;

    DayType(String label, boolean reducesTarget) {
        this.label = label;
        this.reducesTarget = reducesTarget;
    }

    public String label() {
        return label;
    }

    public boolean isLeave() {
        return this != WORK;
    }

    public boolean reducesTarget() {
        return reducesTarget;
    }
}
