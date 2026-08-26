package com.omnihealth.platform.organization.service.impl;

import com.omnihealth.common.exception.DuplicateResourceException;
import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.entity.OrganizationType;
import com.omnihealth.platform.organization.entity.PlatformOrganizationMembership;
import com.omnihealth.platform.organization.mapper.OrganizationMapper;
import com.omnihealth.platform.organization.repository.OrganizationRepository;
import com.omnihealth.platform.organization.repository.PlatformOrganizationMembershipRepository;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PlatformOrganizationMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    private CreateOrganizationRequest createRequest;
    private Organization organizationEntity;
    private User creatorUser;

    @BeforeEach
    void setUp() {
        createRequest = CreateOrganizationRequest.builder()
                .organizationCode("APOLLO_01")
                .legalName("Apollo Health Ltd")
                .displayName("Apollo Clinic")
                .organizationType(OrganizationType.CLINIC)
                .officialEmail("contact@apollo.test")
                .timezone("Asia/Kolkata")
                .currencyCode("INR")
                .locale("en-IN")
                .build();

        // Deliberately leave organizationType unset so the test proves the
        // service sets it from the request before persisting.
        organizationEntity = new Organization();
        organizationEntity.setId(UUID.randomUUID());
        organizationEntity.setOrganizationCode("APOLLO_01");
        organizationEntity.setLegalName("Apollo Health Ltd");
        organizationEntity.setDisplayName("Apollo Clinic");
        organizationEntity.setStatus(OrganizationStatus.DRAFT);
        organizationEntity.setOfficialEmail("contact@apollo.test");

        creatorUser = new User();
        creatorUser.setId(UUID.randomUUID());
        creatorUser.setEmail("admin@apollo.test");
        creatorUser.setEmailVerifiedAt(Instant.now());
    }

    @Test
    void testCreateOrganizationWithCreatorCreatesMembership() {
        when(organizationRepository.existsByOrganizationCode("APOLLO_01")).thenReturn(false);
        when(organizationRepository.existsByOfficialEmail("contact@apollo.test")).thenReturn(false);
        when(organizationMapper.toEntity(createRequest)).thenReturn(organizationEntity);
        when(organizationRepository.save(any(Organization.class))).thenReturn(organizationEntity);
        when(userRepository.findByIdAndDeletedAtIsNull(creatorUser.getId())).thenReturn(Optional.of(creatorUser));
        when(organizationMapper.toResponse(organizationEntity)).thenReturn(
                OrganizationResponse.builder()
                        .id(organizationEntity.getId())
                        .organizationCode(organizationEntity.getOrganizationCode())
                        .legalName(organizationEntity.getLegalName())
                        .displayName(organizationEntity.getDisplayName())
                        .organizationType(OrganizationType.CLINIC)
                        .status(OrganizationStatus.DRAFT)
                        .officialEmail(organizationEntity.getOfficialEmail())
                        .timezone("Asia/Kolkata")
                        .currencyCode("INR")
                        .locale("en-IN")
                        .build()
        );

        OrganizationResponse response = organizationService.createOrganization(createRequest, creatorUser.getId());

        assertNotNull(response);
        assertEquals(OrganizationStatus.DRAFT, response.status());

        // Verify the organization was persisted with its type set from the request.
        ArgumentCaptor<Organization> organizationCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(organizationCaptor.capture());
        Organization savedOrganization = organizationCaptor.getValue();
        assertEquals(OrganizationType.CLINIC, savedOrganization.getOrganizationType());
        assertEquals(OrganizationStatus.DRAFT, savedOrganization.getStatus());

        // Verify primary membership created
        ArgumentCaptor<PlatformOrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(PlatformOrganizationMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        PlatformOrganizationMembership savedMembership = membershipCaptor.getValue();
        assertTrue(savedMembership.isPrimary());
        assertEquals(creatorUser.getId(), savedMembership.getUser().getId());
        assertEquals(organizationEntity.getId(), savedMembership.getOrganization().getId());
    }

    @Test
    void testCreateOrganizationDuplicateCodeThrowsException() {
        when(organizationRepository.existsByOrganizationCode("APOLLO_01")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                organizationService.createOrganization(createRequest, creatorUser.getId())
        );

        verify(organizationRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }
}
