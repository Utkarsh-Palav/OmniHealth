package com.omnihealth.platform.organization.mapper;

import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.entity.Organization;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface OrganizationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizationCode", ignore = true)
    @Mapping(target = "organizationType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "activatedAt", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "terminatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Organization toEntity(CreateOrganizationRequest request);

    OrganizationResponse toResponse(Organization organization);

    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizationCode", ignore = true)
    @Mapping(target = "organizationType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "activatedAt", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "terminatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(
            UpdateOrganizationRequest request,
            @MappingTarget Organization organization
    );
}