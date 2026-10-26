package com.smms.assistance.application.in;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;

public interface HasApprovedVacationPort {
    Optional<Object[]> execute(UUID userId, LocalDate date);
}
