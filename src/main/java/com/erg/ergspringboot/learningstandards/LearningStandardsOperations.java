package com.erg.ergspringboot.learningstandards;

import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreateLtiRegistrationRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.LtiLaunchRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.ValidatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.XapiStatementRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiLaunchResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiRegistrationResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.PackageResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.XapiStatementResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public application facade; adapters do not depend on internal services. */
@Service
public class LearningStandardsOperations {
    private final LearningStandardsPort port;

    public LearningStandardsOperations(LearningStandardsPort port) {
        this.port = port;
    }

    public PackageResponse createPackage(UserAccessContext access, CreatePackageRequest request,
                                         String idempotencyKey, String correlationId) {
        return port.createPackage(access, request, idempotencyKey, correlationId);
    }

    public List<PackageResponse> packages(UserAccessContext access, String status) {
        return port.packages(access, status);
    }

    public PackageResponse validatePackage(UserAccessContext access, String packageId,
                                           ValidatePackageRequest request, String idempotencyKey,
                                           String correlationId) {
        return port.validatePackage(access, packageId, request, idempotencyKey, correlationId);
    }

    public PackageResponse packageById(UserAccessContext access, String packageId) {
        return port.packageById(access, packageId);
    }

    public PackageResponse launchPackage(UserAccessContext access, String packageId) {
        return port.launchPackage(access, packageId);
    }

    public LtiRegistrationResponse createLtiRegistration(UserAccessContext access,
                                                          CreateLtiRegistrationRequest request,
                                                          String idempotencyKey, String correlationId) {
        return port.createLtiRegistration(access, request, idempotencyKey, correlationId);
    }

    public LtiLaunchResponse launchLti(UserAccessContext access, String registrationId,
                                       LtiLaunchRequest request, String idempotencyKey,
                                       String correlationId) {
        return port.launchLti(access, registrationId, request, idempotencyKey, correlationId);
    }

    public XapiStatementResponse ingestXapi(UserAccessContext access, XapiStatementRequest request,
                                             String idempotencyKey, String correlationId) {
        return port.ingestXapi(access, request, idempotencyKey, correlationId);
    }
}
