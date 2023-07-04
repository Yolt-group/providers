package com.yolt.providers.web.service.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CreditCardValidatorTest {

    private static final List<String> validCcNumbers = Arrays.asList(

    );

    private static final List<String> invalidCcNumbers = Arrays.asList(
            "32290430738", "20947389712", "6525878671", "9866981732", "4624994275", "8793510671", "7607783334",
            "6398842489268782", "7265235966470377", "8023155856873756", "5416879988540667", "4443877474765871",
            "2440376000273242", "NotANumber12212312", "72432679283ghj123", "************8781", "XXXXXXXXXXXX8781",
            "null", "", null
    );

    @Test
    public void shouldSuccessfullyValidateAllCorrectNumbers() {
        // when
        List<Boolean> results = validCcNumbers.stream()
                .map(CreditCardValidator::isValidUnmaskedCreditCardNumber)
                .collect(Collectors.toList());

        // then
        Assertions.assertThat(results)
                .allMatch(Boolean.TRUE::equals);
    }

    @Test
    public void shouldFailValidationForIncorrectNumbers() {
        // when
        List<Boolean> results = invalidCcNumbers.stream()
                .map(CreditCardValidator::isValidUnmaskedCreditCardNumber)
                .collect(Collectors.toList());

        // then
        Assertions.assertThat(results)
                .allMatch(Boolean.FALSE::equals);
    }
}
