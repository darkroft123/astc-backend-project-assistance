package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.GetAlertDetailPort;
import com.smms.assistance.domain.entity.AlertEntity;
import com.smms.assistance.infrastructure.repository.AlertRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class GetAlertDetailUseCase implements GetAlertDetailPort {

    @Inject
    AlertRepository alertRepository;

    @Override
    public AlertEntity execute(UUID alertId) {
        return alertRepository.findByIdOptional(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
    }
}
