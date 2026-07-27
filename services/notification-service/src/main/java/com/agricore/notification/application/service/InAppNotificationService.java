package com.agricore.notification.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.notification.api.response.InAppNotificationResponse;
import com.agricore.notification.infrastructure.persistence.InAppDeliveryJpaRepository;
import com.agricore.notification.infrastructure.persistence.entity.InAppDeliveryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class InAppNotificationService {

    private final InAppDeliveryJpaRepository repository;

    public InAppNotificationService(InAppDeliveryJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InAppNotificationResponse> list(String recipient, Pageable pageable) {
        Page<InAppDeliveryEntity> page = StringUtils.hasText(recipient)
                ? repository.findByRecipient(recipient.trim(), pageable)
                : repository.findAll(pageable);
        return PageResponse.of(
                page.getContent().stream().map(InAppNotificationService::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public InAppNotificationResponse markRead(UUID notificationId) {
        InAppDeliveryEntity delivery = repository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "In-app notification not found"));
        if (delivery.getReadAt() == null) {
            delivery.setReadAt(Instant.now());
            repository.saveAndFlush(delivery);
        }
        return toResponse(delivery);
    }

    private static InAppNotificationResponse toResponse(InAppDeliveryEntity delivery) {
        return new InAppNotificationResponse(
                delivery.getNotificationId(),
                delivery.getRecipient(),
                delivery.getSubject(),
                delivery.getBody(),
                delivery.getDeliveredAt(),
                delivery.getReadAt()
        );
    }
}
