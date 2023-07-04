package com.yolt.providers.web.configuration;

import nl.ing.lovebird.errorhandling.ErrorInfo;

public enum ErrorConstants implements ErrorInfo {

    CLIENT_CONFIGURATION_VALIDATION_ERROR("001", "The client configuration is invalid."),
    MISSING_AUTHENTICATION_MEANS("031", "The authentication means for given provider are missing."),
    ACCESS_MEANS_INVALID("034", "The provided access means are not valid."),
    PAYMENT_CREATION_FAILED("035", "Something went wrong while sending payment creation to bank."),
    PAYMENT_CONFIRMATION_BAD_REQUEST("036", "Incorrect values passed to confirm the payment."),
    PAYMENT_CONFIRMATION_FAILED("037", "Something went wrong while sending payment confirmation to bank."),
    UNSUPPORTED_PROVIDER("038", "Given provider is not recognized"),
    AUTHENTICATION_MEANS_VALIDATION_ERROR("039", "Wrong format of provided authentication means."),
    AUTHENTICATION_MEANS_UNRECOGNIZED_KEY("040", "Authentication mean key not recognized."),
    USER_CANCELLED_PAYMENT("041", "User cancelled payment."),
    UNKNOWN_PROVIDER_VERSION("042", "Given provider version is not recognized"),
    USER_DOES_NOT_EXIST("050", "User does not exist."),
    AUTO_ONBOARDING_ERROR("060", "The auto-onboading failed."),
    BACK_PRESSURE_REQUEST("061", "Too many requests from providers to ASPSP, no data returns from API calls"),
    PAYMENT_EXECUTION_TECHNICAL_ERROR("062", "Technical error during payment execution"),
    FORM_COULD_NOT_BE_DECRYPTED("063", "Submitted form could not be decrypted");

    private final String code;
    private final String message;

    ErrorConstants(final String code, final String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
