package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.portal.dto.PortalMeResponse;
import cn.tolim.pudans.iam.portal.dto.PortalUserView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class PortalMeService {

    private final PortalSessionService portalSessionService;

    public PortalMeService(PortalSessionService portalSessionService) {
        this.portalSessionService = portalSessionService;
    }

    // Builds the current user and session information of the caller
    // Parameter request: HttpServletRequest current request
    // Returns: PortalMeResponse
    public PortalMeResponse getCurrentUser(HttpServletRequest request) {
        PortalMeResponse response = new PortalMeResponse();
        Optional<PortalIdentity> optional = portalSessionService.resolve(request);
        response.setAuthenticated(optional.isPresent());
        if (optional.isEmpty()) {
            return response;
        }
        PortalIdentity identity = optional.get();
        PortalUserView user = new PortalUserView();
        user.setUserId(identity.userId());
        user.setUsername(identity.username());
        user.setDisplayName(identity.displayName());
        user.setUserType(identity.userType());
        user.setOrganizationId(identity.organizationId());
        user.setOrganizationName(identity.organizationName());
        user.setEmail(identity.email());
        user.setPhone(identity.phone());
        user.setAvatar(identity.avatar());
        user.setEmailVerified(identity.emailVerified());
        user.setPhoneVerified(identity.phoneVerified());
        user.setGroups(identity.groups() == null ? new ArrayList<>() : new ArrayList<>(identity.groups()));
        response.setUser(user);
        response.setSession(identity.session());
        return response;
    }
}