package com.omnihealth.platform.onboarding.mapper;

import com.omnihealth.platform.onboarding.dto.response.OnboardingSessionResponse;
import com.omnihealth.platform.onboarding.entity.OnboardingSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OnboardingMapper {
    @Mapping(
            target = "organizationId",
            source = "organization.id"
    )
    @Mapping(
            target = "initiatedByUserId",
            source = "initiatedBy.id"
    )
    OnboardingSessionResponse toResponse(OnboardingSession session);
}
