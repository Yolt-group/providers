package com.yolt.providers.web.service;

import com.yolt.providers.common.exception.*;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatusValue;
import nl.ing.lovebird.providershared.form.ExtendedProviderServiceResponseStatus;

import java.util.HashMap;
import java.util.Map;

public class ExceptionToProviderStatusMapping {
    private static Map<Class<? extends HandledProviderCheckedException>, ProviderServiceResponseStatusValue> mapping = new HashMap<>();
    static {
        mapping.put(ExpiredCredentialsException.class, ExtendedProviderServiceResponseStatus.EXPIRED_CREDENTIALS);
        mapping.put(IncorrectAnswerException.class, ExtendedProviderServiceResponseStatus.INCORRECT_ANSWER);
        mapping.put(IncorrectCredentialsException.class, ExtendedProviderServiceResponseStatus.INCORRECT_CREDENTIALS);
        mapping.put(SiteActionNeededException.class, ExtendedProviderServiceResponseStatus.SITE_ACTION_NEEDED);
        mapping.put(SiteErrorException.class, ExtendedProviderServiceResponseStatus.SITE_ERROR);
        mapping.put(ExternalUserSiteDoesNotExistException.class, ExtendedProviderServiceResponseStatus.USERSITE_DOES_NOT_EXIST_ANYMORE);
        mapping.put(TooManyRefreshesException.class, ExtendedProviderServiceResponseStatus.TOO_MANY_REFRESHES);
        mapping.put(AccessMeansExpiredException.class, ExtendedProviderServiceResponseStatus.ACCESS_MEANS_EXPIRED);
        mapping.put(GenericErrorException.class, ProviderServiceResponseStatus.UNKNOWN_ERROR);
    }

    public static ProviderServiceResponseStatusValue get(Class<? extends HandledProviderCheckedException> clazz) {
        return mapping.getOrDefault(clazz, ProviderServiceResponseStatus.UNKNOWN_ERROR);
    }

    private ExceptionToProviderStatusMapping() {
    }
}
