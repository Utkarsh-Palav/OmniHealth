package com.omnihealth.tenant.metadata.controller;

import com.omnihealth.common.builder.ApiResponseBuilder;
import com.omnihealth.common.constants.ApiRoutes;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.tenant.metadata.dto.TenantPingResponse;
import com.omnihealth.tenant.metadata.service.TenantPingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated probe endpoint that proves database-per-tenant runtime routing:
 * it writes and reads a row in the caller's tenant database and returns the rows
 * plus the resolved tenant id.
 */
@RestController
@RequestMapping(ApiRoutes.TENANT)
@RequiredArgsConstructor
@Tag(name = "Tenant", description = "Tenant runtime-routing probe endpoints")
public class TenantPingController {

    private final TenantPingService tenantPingService;
    private final ApiResponseBuilder apiResponseBuilder;

    @PostMapping("/ping")
    @Operation(
            summary = "Tenant ping",
            description = "Writes and reads a row in the caller's tenant database to prove per-request routing."
    )
    public ResponseEntity<ApiResponse<TenantPingResponse>> ping(HttpServletRequest httpRequest) {
        TenantPingResponse response = tenantPingService.ping();
        return ResponseEntity.ok(apiResponseBuilder.success(response, "Tenant ping succeeded.", httpRequest));
    }
}
