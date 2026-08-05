package com.omnihealth.platform.organization.service.impl;

import com.omnihealth.common.exception.ConflictException;
import com.omnihealth.common.exception.DuplicateResourceException;
import com.omnihealth.common.exception.ResourceNotFoundException;
import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.mapper.OrganizationMapper;
import com.omnihealth.platform.organization.repository.OrganizationRepository;
import com.omnihealth.platform.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    @Override
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {

        log.info(
                "Creating organization with organizationCode={}",
                request.organizationCode()
        );

        if (organizationRepository.existsByOrganizationCode(request.organizationCode())) {
            throw new DuplicateResourceException(
                    "Organization",
                    "organizationCode",
                    request.organizationCode()
            );
        }

        if (organizationRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Organization",
                    "email",
                    request.email()
            );
        }

        final Organization organization = organizationMapper.toEntity(request);

        organization.setStatus(OrganizationStatus.PENDING);
        organization.setDemo(Boolean.TRUE.equals(request.demo()));

        final Organization savedOrganization =
                organizationRepository.save(organization);

        log.info(
                "Organization created successfully. organizationId={}",
                savedOrganization.getId()
        );

        return organizationMapper.toResponse(savedOrganization);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> getOrganizations(Pageable pageable) {

        log.info(
                "Fetching organizations. page={}, size={}, sort={}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        Page<Organization> organizations =
                organizationRepository.findAllByDeletedAtIsNull(pageable);

        return organizations.map(organizationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID organizationId) {

        final Organization organization =
                getOrganizationOrThrow(organizationId);

        return organizationMapper.toResponse(organization);
    }

    @Override
    public OrganizationResponse updateOrganization(
            UUID organizationId,
            UpdateOrganizationRequest request
    ) {

        final Organization organization =
                getOrganizationOrThrow(organizationId);

        if (request.email() != null
                && !organization.getEmail().equalsIgnoreCase(request.email())
                && organizationRepository.existsByEmail(request.email())) {

            throw new DuplicateResourceException(
                    "Organization",
                    "email",
                    request.email()
            );
        }

        // Uncomment if organizationCode is editable.
        /*
        if (request.organizationCode() != null
                && !organization.getOrganizationCode().equalsIgnoreCase(request.organizationCode())
                && organizationRepository.existsByOrganizationCode(request.organizationCode())) {

            throw new DuplicateResourceException(
                    "Organization",
                    "organizationCode",
                    request.organizationCode()
            );
        }
        */

        organizationMapper.updateEntity(request, organization);

        final Organization updatedOrganization =
                organizationRepository.save(organization);

        log.info(
                "Organization updated successfully. organizationId={}",
                updatedOrganization.getId()
        );

        return organizationMapper.toResponse(updatedOrganization);
    }

    @Override
    public void archiveOrganization(UUID organizationId) {

        final Organization organization =
                getOrganizationOrThrow(organizationId);

        if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
            throw new ConflictException(
                    "Organization is already archived."
            );
        }

        organization.setStatus(OrganizationStatus.ARCHIVED);
        organization.setDeletedAt(Instant.now());

        organizationRepository.save(organization);

        log.info(
                "Organization archived successfully. organizationId={}",
                organizationId
        );
    }

    private Organization getOrganizationOrThrow(UUID organizationId) {

        return organizationRepository
                .findByIdAndDeletedAtIsNull(organizationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Organization",
                                "id",
                                organizationId.toString()
                        )
                );
    }
}