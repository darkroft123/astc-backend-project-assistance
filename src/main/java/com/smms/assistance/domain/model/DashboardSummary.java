package com.smms.assistance.domain.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummary {
    private Integer totalAttendances;
    private Integer totalAbsences;
    private Integer pendingJustifications;
}