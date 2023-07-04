package com.yolt.providers.web.form;

import com.yolt.providers.common.ais.form.EncryptionDetails;
import com.yolt.providers.common.ais.form.FormCreateNewUserResponse;
import com.yolt.providers.common.ais.form.LoginFormResponse;
import com.yolt.providers.common.exception.AccessMeansExpiredException;
import com.yolt.providers.common.exception.ExternalUserSiteDoesNotExistException;
import com.yolt.providers.web.controller.dto.FormCreateNewExternalUserSiteDTO;
import com.yolt.providers.web.controller.dto.FormTriggerRefreshAndFetchDataDTO;
import com.yolt.providers.web.controller.dto.FormUpdateExternalUserSiteDTO;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.form.dto.EncryptionDetailsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import nl.ing.lovebird.clienttokens.verification.ClientIdVerificationService;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.form.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

import static com.yolt.providers.web.service.ServiceConstants.*;

/**
 * End-point for providers service.
 * Take into account that although this controller is dedicated to form providers.
 * As long as this API stays internal, we don't need to check whether received provider is actually form provider, but
 * if there would be any external usage, we should validate it.
 */
@RestController
@RequestMapping(value = "/form", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@RequiredArgsConstructor
@Slf4j
public class FormProviderController {

    private final FormProviderService providerService;
    private final ClientIdVerificationService clientIdVerificationService;

    @PostMapping("/{provider}/access-means/refresh")
    public AccessMeansDTO refreshAccessMeans(
            @PathVariable String provider,
            @RequestBody FormRefreshAccessMeansDTO formRefreshAccessMeansDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_SITE_MANAGEMENT, SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientToken clientToken
    ) throws ExternalUserSiteDoesNotExistException {
        clientIdVerificationService.verify(clientToken, formRefreshAccessMeansDTO.getClientId());
        return providerService.refreshAccessMeansForForm(provider, clientToken, formRefreshAccessMeansDTO);
    }

    @PostMapping("/{provider}/delete-user-site")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deleteUserSite(
            @PathVariable String provider,
            @RequestBody FormDeleteUserSiteDTO formDeleteUserSite,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_SITE_MANAGEMENT
            }) ClientToken clientToken
    ) throws AccessMeansExpiredException {
        clientIdVerificationService.verify(clientToken, formDeleteUserSite.getClientId());
        providerService.deleteUserSite(provider, clientToken, formDeleteUserSite);
    }

    @PostMapping("/{provider}/delete-user")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deleteUser(
            @PathVariable String provider,
            @RequestBody FormDeleteUser formDeleteUser,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_SITE_MANAGEMENT
            }) ClientToken clientToken
    ) throws AccessMeansExpiredException {
        clientIdVerificationService.verify(clientToken, formDeleteUser.getClientId());
        providerService.deleteUser(provider, clientToken, formDeleteUser);
    }

    @PostMapping("/{provider}/fetch-login-form")
    public LoginFormResponseDTO fetchLoginForm(
            @PathVariable String provider,
            @RequestBody FormFetchLoginDTO formFetchLoginDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientToken clientToken
    ) throws IOException {
        clientIdVerificationService.verify(clientToken, formFetchLoginDTO.getClientId());
        LoginFormResponse loginFormResponse = providerService.fetchLoginForm(provider, clientToken, formFetchLoginDTO);
        return new LoginFormResponseDTO(loginFormResponse.getProviderForm(), loginFormResponse.getYoltForm());
    }

    @PostMapping("/{provider}/get-encryption-details")
    public EncryptionDetailsDTO getEncryptionDetails(
            @PathVariable String provider,
            @RequestBody FormGetEncryptionDetailsDTO formGetEncryptionDetailsDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_SITE_MANAGEMENT
            }) ClientToken clientToken
    ) {
        clientIdVerificationService.verify(clientToken, formGetEncryptionDetailsDTO.getClientId());
        EncryptionDetails encryptionDetails = providerService.getEncryptionDetails(provider, clientToken, formGetEncryptionDetailsDTO);
        return toDto(encryptionDetails);
    }

    private EncryptionDetailsDTO toDto(EncryptionDetails encryptionDetails) {
        if (!encryptionDetails.isEncryption()) {
            return new EncryptionDetailsDTO(null);
        }

        if (encryptionDetails.getJweDetails() != null) {
            EncryptionDetails.JWEDetails jweDetails = encryptionDetails.getJweDetails();
            return new EncryptionDetailsDTO(
                    new EncryptionDetailsDTO.JWEDetailsDTO(jweDetails.getAlgorithm(), jweDetails.getEncryptionMethod(), jweDetails.getPublicJSONWebKey())
            );
        }
        throw new IllegalStateException("No encryption method specified, should not happen.");
    }

    @PostMapping("/{provider}/create-new-user")
    public FormCreateNewUserResponseDTO createNewUser(
            @PathVariable String provider,
            @RequestBody FormCreateNewUserRequestDTO formCreateNewUserRequestDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientToken clientToken
    ) {
        clientIdVerificationService.verify(clientToken, formCreateNewUserRequestDTO.getClientId());
        FormCreateNewUserResponse createNewUserResponse = providerService.createNewUser(provider, clientToken, formCreateNewUserRequestDTO);
        return new FormCreateNewUserResponseDTO(createNewUserResponse.getAccessMeans(), createNewUserResponse.getExternalUserId());
    }

    @DeleteMapping("/{provider}/external-user-ids/{externalUserId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deleteExternalUserByIdAtProvider(
            @PathVariable String provider,
            @PathVariable String externalUserId,
            @RequestHeader(name = "client-id", required = false) final UUID clientId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_ASSISTANCE_PORTAL_YTS
            }) ClientToken clientToken
    ) {
        clientIdVerificationService.verify(clientToken, clientId);
        providerService.deleteExternalUserByIdAtProvider(provider, externalUserId, clientId, clientToken);
    }

    // Operations that will return something asynchronously via kafka.

    @GetMapping("/{provider}/external-user-ids")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String fetchExternalUserIdsFromProvider(
            @PathVariable String provider,
            @RequestHeader(name = "client-id") final UUID clientId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_ASSISTANCE_PORTAL_YTS
            }) ClientToken clientToken
    ) {
        clientIdVerificationService.verify(clientToken, clientId);
        UUID batchId = UUID.randomUUID();
        providerService.fetchExternalUserIdsFromProvider(provider, batchId, clientId, clientToken);
        return batchId.toString();
    }

    @PostMapping("/{provider}/update-external-user-site")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void updateExternalUserSite(
            @PathVariable String provider,
            @RequestBody FormUpdateExternalUserSiteDTO updateExternalUserSiteDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientUserToken clientUserToken
    ) {
        clientIdVerificationService.verify(clientUserToken, updateExternalUserSiteDTO.getClientId());
        providerService.updateExternalUserSite(provider, updateExternalUserSiteDTO, clientUserToken);
    }

    @PostMapping("/{provider}/create-new-external-user-site")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void createNewExternalUserSite(
            @PathVariable String provider,
            @RequestBody FormCreateNewExternalUserSiteDTO formCreateNewExternalUserSiteDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientUserToken clientUserToken
    ) {
        clientIdVerificationService.verify(clientUserToken, formCreateNewExternalUserSiteDTO.getClientId());
        providerService.createNewExternalUserSite(provider, formCreateNewExternalUserSiteDTO, clientUserToken);
    }

    @PostMapping("/{provider}/submit-mfa")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submitMfa(
            @PathVariable String provider,
            @RequestBody FormSubmitMfaDTO submitMfaDTO,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientUserToken clientUserToken
    ) {
        clientIdVerificationService.verify(clientUserToken, submitMfaDTO.getClientId());
        providerService.submitMFA(provider, submitMfaDTO, clientUserToken);
    }

    @PostMapping("/{provider}/process-callback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processCallback(
            @PathVariable String provider,
            @RequestBody CallbackRequestDTO callbackRequestDTO,
            @VerifiedClientToken(restrictedTo = SERVICE_SITE_MANAGEMENT) ClientUserToken clientUserToken
    ) {
        clientIdVerificationService.verify(clientUserToken, callbackRequestDTO.getClientId());
        providerService.processCallback(provider, callbackRequestDTO, clientUserToken);
    }

    @PostMapping("/{provider}/trigger-refresh-and-fetch-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerRefreshAndFetchData(
            @PathVariable String provider,
            @RequestBody FormTriggerRefreshAndFetchDataDTO formTriggerRefreshAndFetchData,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_SITE_MANAGEMENT, SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_DEV_PORTAL, SERVICE_YOLT_ASSISTANCE_PORTAL, SERVICE_ASSISTANCE_PORTAL_YTS
            }) ClientUserToken clientUserToken
    ) {
        clientIdVerificationService.verify(clientUserToken, formTriggerRefreshAndFetchData.getClientId());
        providerService.triggerRefreshAndFetchData(provider, formTriggerRefreshAndFetchData, clientUserToken);
    }
}
