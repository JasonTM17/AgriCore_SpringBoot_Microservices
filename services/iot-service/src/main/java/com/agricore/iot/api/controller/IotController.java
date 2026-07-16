package com.agricore.iot.api.controller;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.api.request.RegisterDeviceRequest;
import com.agricore.iot.api.response.DeviceResponse;
import com.agricore.iot.api.response.IngestResultResponse;
import com.agricore.iot.application.service.IotApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/iot")
public class IotController {

    private final IotApplicationService service;

    public IotController(IotApplicationService service) {
        this.service = service;
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public ResponseEntity<DeviceResponse> register(@Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerDevice(request));
    }

    @PostMapping("/readings")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','FIELD_WORKER')")
    public IngestResultResponse ingest(@Valid @RequestBody IngestReadingRequest request) {
        return service.ingest(request);
    }
}
