package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providershared.callback.UserDataCallbackResponse;
import nl.ing.lovebird.providershared.callback.UserSiteData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse.Status.FINISHED;
import static com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse.Status.REFRESH_IN_PROGRESS_WAITING_CALLBACK;

@Service
@RequiredArgsConstructor
public class AccountsPostProcessingService {

    private final CreditCardAccountPostProcessor creditCardAccountPostProcessor;

    public DataProviderResponse postProcessDataProviderResponse(final DataProviderResponse providerResponse) {
        List<ProviderAccountDTO> postProcessedAccounts = doPostProcessing(providerResponse.getAccounts());

        if (providerResponse instanceof FormUserSiteDataProviderResponse) {
            FormUserSiteDataProviderResponse.Status status = providerResponse.isDataExpectedAsynchronousViaCallback() ? REFRESH_IN_PROGRESS_WAITING_CALLBACK : FINISHED;
            return new FormUserSiteDataProviderResponse(
                    postProcessedAccounts,
                    status);
        } else {
            return new DataProviderResponse(
                    postProcessedAccounts
            );
        }
    }

    public UserDataCallbackResponse postProcessUserDataCallbackResponse(final UserDataCallbackResponse callbackResponse) {
        UserSiteData userSiteData = callbackResponse.getUserSiteData();
        List<ProviderAccountDTO> postProcessedAccounts = doPostProcessing(callbackResponse.getUserSiteData().getAccounts());

        return new UserDataCallbackResponse(
                callbackResponse.getProvider(),
                callbackResponse.getExternalUserId(),
                new UserSiteData(
                        userSiteData.getExternalUserSiteId(),
                        postProcessedAccounts,
                        userSiteData.getFailedAccounts(),
                        userSiteData.getProviderServiceResponseStatusValue())
        );
    }

    private List<ProviderAccountDTO> doPostProcessing(final List<ProviderAccountDTO> accounts) {
        var postProcessedAccounts = new ArrayList<ProviderAccountDTO>();

        for (var account : accounts) {
            var postProcessedAccount = creditCardAccountPostProcessor.postProcessAccount(account);
            postProcessedAccounts.add(postProcessedAccount);
        }

        return postProcessedAccounts;
    }
}
