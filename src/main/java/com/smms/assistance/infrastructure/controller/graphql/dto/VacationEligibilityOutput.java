package com.smms.assistance.infrastructure.controller.graphql.dto;

import java.time.LocalDateTime;

public class VacationEligibilityOutput {
    public int requiredDays;
    public long daysElapsed;
    @org.eclipse.microprofile.graphql.Name("isEligible")
    public boolean isEligible;
    public String joinDate;
    public Long daysRemaining;

    public VacationEligibilityOutput() {}

    public VacationEligibilityOutput(int requiredDays, long daysElapsed, boolean isEligible, String joinDate) {
        this.requiredDays = requiredDays;
        this.daysElapsed = daysElapsed;
        this.isEligible = isEligible;
        this.joinDate = joinDate;
        this.daysRemaining = Math.max(0, requiredDays - daysElapsed);
    }
}
