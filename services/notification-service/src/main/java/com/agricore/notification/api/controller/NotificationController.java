package com.agricore.notification.api.controller;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.application.service.NotificationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationApplicationService service;

    public NotificationController(NotificationApplicationService service) {
        this.service = service;
    }

    /**
     * Sends an ad-hoc notification. Restricted to administrators: the caller chooses the recipient
     * and the entire message body, so leaving it open to any authenticated token would make the
     * platform an authenticated relay the moment the log sink is swapped for a real email adapter.
     *
     * <p>Event-driven notifications do not come through here — the Kafka listener calls the
     * application service directly.
     */
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(request));
    }
}
