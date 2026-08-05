package com.omnihealth.platform.organization.controller;

import com.omnihealth.common.builder.ApiResponseBuilder;
import com.omnihealth.common.constants.ApiRoutes;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiRoutes.ORGANIZATIONS)
@RequiredArgsConstructor
@Validated
@Tag(
        name = "Organizations",
        description = "Organization management APIs"
)
public class OrganizationController {

    private final OrganizationService organizationService;
    private final ApiResponseBuilder apiResponseBuilder;

    @Operation(
            summary = "Create Organization",
            description = "Creates a new organization in the platform."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            HttpServletRequest httpRequest
    ) {
        OrganizationResponse response = organizationService.createOrganization(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        apiResponseBuilder.created(
                                response,
                                "Organization created successfully.",
                                httpRequest)
                );
    }

    @Operation(
            summary = "Get Organizations",
            description = "Retrieves a paginated list of organizations."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationResponse>>> getOrganizations(
            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable,
            HttpServletRequest httpRequest
    ) {

        Page<OrganizationResponse> page =
                organizationService.getOrganizations(pageable);

        return ResponseEntity.ok(
                apiResponseBuilder.paginated(
                        page,
                        "Organizations retrieved successfully.",
                        httpRequest
                )
        );
    }

    @Operation(
            summary = "Get Organization",
            description = "Retrieves an organization by its unique identifier."
    )
    @GetMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganizationByID(
            @PathVariable UUID organizationId,
            HttpServletRequest httpRequest
    ) {
        OrganizationResponse response = organizationService.getOrganization(organizationId);

        return ResponseEntity.ok(
                apiResponseBuilder.success(response, "Organization retrieved successfully", httpRequest)
        );
    }

    @Operation(
            summary = "Update Organization",
            description = "Updates an existing organization's information."
    )
    @PutMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationRequest request,
            HttpServletRequest httpRequest
    ) {

        OrganizationResponse response =
                organizationService.updateOrganization(
                        organizationId,
                        request
                );

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Organization updated successfully.",
                        httpRequest
                )
        );
    }

    @Operation(
            summary = "Archive Organization",
            description = "Archives an organization using a soft delete."
    )
    @DeleteMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<Void>> archiveOrganization(
            @PathVariable UUID organizationId,
            HttpServletRequest httpRequest
    ) {

        organizationService.archiveOrganization(organizationId);

        return ResponseEntity.ok(
                apiResponseBuilder.noContent(
                        "Organization archived successfully.",
                        httpRequest
                )
        );
    }

}