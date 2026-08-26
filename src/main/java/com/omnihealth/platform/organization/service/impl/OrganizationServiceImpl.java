package com.omnihealth.platform.organization.service.impl;

import com.omnihealth.common.exception.ConflictException;
import com.omnihealth.common.exception.DuplicateResourceException;
import com.omnihealth.common.exception.ResourceNotFoundException;
import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.entity.PlatformOrganizationMembership;
import com.omnihealth.platform.organization.mapper.OrganizationMapper;
import com.omnihealth.platform.organization.repository.OrganizationRepository;
import com.omnihealth.platform.organization.repository.PlatformOrganizationMembershipRepository;
import com.omnihealth.platform.organization.service.OrganizationService;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.repository.UserRepository;
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
    private final PlatformOrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationMapper organizationMapper;

    @Override
    public OrganizationResponse createOrganization(
            CreateOrganizationRequest request,
            UUID creatorUserId
    ) {
        log.info(
                "Creating organization with organizationCode={}, creatorUserId={}",
                request.organizationCode(),
                creatorUserId
        );

        if (organizationRepository.existsByOrganizationCode(request.organizationCode())) {
            throw new DuplicateResourceException(
                    "Organization",
                    "organizationCode",
                    request.organizationCode()
            );
        }

        if (organizationRepository.existsByOfficialEmail(request.officialEmail())) {
            throw new DuplicateResourceException(
                    "Organization",
                    "officialEmail",
                    request.officialEmail()
            );
        }

        Organization organization = organizationMapper.toEntity(request);
        organization.setStatus(OrganizationStatus.DRAFT);
        organization.setOrganizationType(request.organizationType());
        // organizationCode is a @Mapping(ignore = true) target in OrganizationMapper
        // (same as organizationType), so toEntity never copies it from the request.
        // It must be set explicitly here, otherwise the NOT NULL organization_code
        // column is inserted as null and the org insert fails at flush.
        organization.setOrganizationCode(request.organizationCode());

        Organization savedOrganization = organizationRepository.save(organization);

        // Associate the creator as the primary member (owner) when provided.
        // The onboarding session lifecycle is owned exclusively by the
        // onboarding module and is intentionally NOT created here, to keep a
        // single source of truth for the signup state machine.
        if (creatorUserId != null) {
            User creator = userRepository.findByIdAndDeletedAtIsNull(creatorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", creatorUserId.toString()));

            PlatformOrganizationMembership membership = PlatformOrganizationMembership.builder()
                    .organization(savedOrganization)
                    .user(creator)
                    .isPrimary(true)
                    .joinedAt(Instant.now())
                    .build();
            membershipRepository.save(membership);
            log.info("Created primary organization membership for userId={} in organizationId={}", creator.getId(), savedOrganization.getId());
        }

        log.info(
                "Organization created successfully. organizationId={}",
                savedOrganization.getId()
        );

        return organizationMapper.toResponse(savedOrganization);
    }

    @Override
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        return createOrganization(request, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> getOrganizations(
            Pageable pageable
    ) {
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
    public OrganizationResponse getOrganization(
            UUID organizationId
    ) {
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

        /*
         * Official email is unique.
         * Only perform the duplicate check when the client actually supplied a new email.
         */
        if (request.officialEmail() != null
                && !organization.getOfficialEmail().equalsIgnoreCase(request.officialEmail())
                && organizationRepository.existsByOfficialEmail(request.officialEmail())) {

            throw new DuplicateResourceException(
                    "Organization",
                    "officialEmail",
                    request.officialEmail()
            );
        }

        organizationMapper.updateEntity(
                request,
                organization
        );

        final Organization updatedOrganization =
                organizationRepository.save(organization);

        log.info(
                "Organization updated successfully. organizationId={}",
                updatedOrganization.getId()
        );

        return organizationMapper.toResponse(updatedOrganization);
    }

    @Override
    public void archiveOrganization(
            UUID organizationId
    ) {
        final Organization organization =
                getOrganizationOrThrow(organizationId);

        if (organization.getStatus() == OrganizationStatus.TERMINATED) {
            throw new ConflictException(
                    "Organization is already terminated."
            );
        }

        organization.setStatus(OrganizationStatus.TERMINATED);
        organization.setTerminatedAt(Instant.now());
        organization.setDeletedAt(Instant.now());

        organizationRepository.save(organization);

        log.info(
                "Organization archived successfully. organizationId={}",
                organizationId
        );
    }

    private Organization getOrganizationOrThrow(
            UUID organizationId
    ) {
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