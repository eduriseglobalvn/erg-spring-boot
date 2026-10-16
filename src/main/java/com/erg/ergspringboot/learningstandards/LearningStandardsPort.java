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

/** Use-case port shared by REST/GraphQL adapters. */
public interface LearningStandardsPort {
    PackageResponse createPackage(UserAccessContext access, CreatePackageRequest request,
                                  String idempotencyKey, String correlationId);

    List<PackageResponse> packages(UserAccessContext access, String status);

    PackageResponse validatePackage(UserAccessContext access, String packageId,
                                    ValidatePackageRequest request, String idempotencyKey,
                                    String correlationId);

    PackageResponse packageById(UserAccessContext access, String packageId);

    PackageResponse launchPackage(UserAccessContext access, String packageId);

    LtiRegistrationResponse createLtiRegistration(UserAccessContext access,
                                                   CreateLtiRegistrationRequest request,
                                                   String idempotencyKey, String correlationId);

    LtiLaunchResponse launchLti(UserAccessContext access, String registrationId,
                                LtiLaunchRequest request, String idempotencyKey,
                                String correlationId);

    XapiStatementResponse ingestXapi(UserAccessContext access, XapiStatementRequest request,
                                     String idempotencyKey, String correlationId);
}
