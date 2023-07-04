package com.yolt.providers.web.circuitbreaker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;

import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;

@Getter
@RequiredArgsConstructor
public enum ProvidersCircuitBreakerCommand {

    PIS_CREATE_PAYMENT(PIS, "createPayment"),
    PIS_CONFIRM_PAYMENT(PIS, "confirmPayment"),
    PIS_GENERATE_AUTH_URL(PIS, "generateAuthorizeUrl"),

    PIS_SEPA_INITIATE_PAYMENT(PIS, "initiateSepaPayment"),
    PIS_SEPA_SUBMIT_PAYMENT(PIS, "submitSepaPayment"),
    PIS_SEPA_SUBMIT_PERIODIC_PAYMENT(PIS, "submitSepaPeriodicPayment"),
    PIS_SEPA_GET_PAYMENT_STATUS(PIS, "getSepaPaymentStatus"),
    PIS_SEPA_INITIATE_SCHEDULED_PAYMENT(PIS, "initiateSepaScheduledPayment"),
    PIS_SEPA_INITIATE_PERIODIC_PAYMENT(PIS, "initiateSepaPeriodicPayment"),

    PIS_UK_DOMESTIC_INITIATE_SINGLE_PAYMENT(PIS, "initiateUkDomesticSinglePayment"),
    PIS_UK_DOMESTIC_SUBMIT_PAYMENT(PIS, "submitUkDomesticPayment"),
    PIS_UK_DOMESTIC_SUBMIT_PERIODIC_PAYMENT(PIS, "submitUkDomesticPeriodicPayment"),
    PIS_UK_DOMESTIC_GET_PAYMENT_STATUS(PIS, "getUkDomesticPaymentStatus"),

    PIS_UK_DOMESTIC_INITIATE_SCHEDULED_PAYMENT(PIS, "initiateUkDomesticScheduledPayment"),
    PIS_UK_DOMESTIC_INITIATE_PERIODIC_PAYMENT(PIS, "initiateUkDomesticPeriodicPayment"),

    AIS_GET_LOGIN_INFO(AIS, "getLoginInfo"),
    AIS_CREATE_ACCESS_MEANS(AIS, "createNewAccessMeans"),
    AIS_REFRESH_ACCESS_MEANS(AIS, "refreshAccessMeans"),
    AIS_FETCH_DATA(AIS, "fetchData"),
    AIS_GET_ACCOUNTS(AIS, "getAccounts"),
    AIS_NOTIFY_USER_SITE_DELETE(AIS, "notifyUserSiteDelete");

    private String serviceType;
    private String command;

    ProvidersCircuitBreakerCommand(ServiceType type, String command) {
        this.serviceType = type.name();
        this.command = command;
    }

    public String getServiceType() {
        return this.serviceType;
    }

    public String getCommand() {
        return command;
    }
}
