package com.yolt.providers.web.service.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class CreditCardValidator {

    public static boolean isValidUnmaskedCreditCardNumber(String ccNumber) {
        try {
            Long.parseLong(ccNumber);
            return isLongEnough(ccNumber) && isLuhnCheckValid(ccNumber);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLongEnough(String ccNumber) {
        return ccNumber.length() > 11;
    }

    private static boolean isLuhnCheckValid(String ccNumber) {
            int nSum = 0;
            boolean alternate = false;
            boolean result = false;
            for (int i = ccNumber.length() - 1; i >= 0; i--) {
                int d = ccNumber.charAt(i) - '0';

                if (alternate) {
                    d = d * 2;
                }

                nSum += d / 10;
                nSum += d % 10;

                alternate = !alternate;
            }
            result = (nSum % 10 == 0);
            if (result) {
                // this case should not happen, that's why log as warn to not being forgotten in info level
                log.warn("Found unmasked credit card number eligible for masking post processing");
            }
            return result;
    }
}
