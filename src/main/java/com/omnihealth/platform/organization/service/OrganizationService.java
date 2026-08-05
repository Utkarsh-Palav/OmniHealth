package com.omnihealth.platform.organization.service;

import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrganizationService {

    OrganizationResponse createOrganization(
            CreateOrganizationRequest request
    );

    Page<OrganizationResponse> getOrganizations(Pageable pageable);

    OrganizationResponse getOrganization(
            UUID organizationId
    );

    OrganizationResponse updateOrganization(
            UUID organizationId,
            UpdateOrganizationRequest request
    );

    void archiveOrganization(
            UUID organizationId
    );
}
