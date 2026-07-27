package com.agricore.notification.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.InAppNotificationResponse;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.application.service.InAppNotificationService;
import com.agricore.notification.application.service.NotificationApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private final NotificationApplicationService service;
    private final InAppNotificationService inAppService;

    public NotificationController(
            NotificationApplicationService service,
            InAppNotificationService inAppService
    ) {
        this.service = service;
        this.inAppService = inAppService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_NOTIFICATION_ADMIN')")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(request));
    }

    @GetMapping("/in-app")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_NOTIFICATION_ADMIN')")
    public PageResponse<InAppNotificationResponse> listInApp(
            @RequestParam(required = false) @Size(min = 1, max = 320) String recipient,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(Sort.Order.desc("deliveredAt"), Sort.Order.desc("notificationId"));
        return inAppService.list(recipient, PageRequest.of(page, size, sort));
    }

    @PatchMapping("/in-app/{notificationId}/read")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_NOTIFICATION_ADMIN')")
    public InAppNotificationResponse markInAppRead(@PathVariable UUID notificationId) {
        return inAppService.markRead(notificationId);
    }
}
