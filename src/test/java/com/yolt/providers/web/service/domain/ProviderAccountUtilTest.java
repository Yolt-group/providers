package com.yolt.providers.web.service.domain;

import nl.ing.lovebird.extendeddata.account.ExtendedAccountDTO;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.providerdomain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class ProviderAccountUtilTest {
    private final ProviderAccountUtil providerAccountUtil = new ProviderAccountUtil();
    private DirectDebitDTO directDebitMock;
    private StandingOrderDTO standingOrderMock;

    @BeforeEach
    public void setup() {
        directDebitMock = mock(DirectDebitDTO.class);
        standingOrderMock = mock(StandingOrderDTO.class);
    }

    @Test
    public void shouldReturnWholeDto() {
        //given
        ProviderAccountDTO providerAccount = getProviderAccount(directDebitMock, standingOrderMock);
        //when
        ProviderAccountDTO filteredAccount = providerAccountUtil.validateAndFilterOptionalDetails(providerAccount);
        //then
        assertThat(filteredAccount.getDirectDebits()).hasSize(1);
        assertThat(filteredAccount.getStandingOrders()).hasSize(1);
    }

    private ProviderAccountDTO getProviderAccount(DirectDebitDTO directDebit, StandingOrderDTO standingOrder) {
        List<DirectDebitDTO> directDebits = new ArrayList<DirectDebitDTO>() {{
            add(directDebit);
        }};
        List<StandingOrderDTO> standingOrders = new ArrayList<StandingOrderDTO>() {{
            add(standingOrder);
        }};
        return getProviderAccount(directDebits, standingOrders);
    }

    private ProviderAccountDTO getProviderAccount(List<DirectDebitDTO> directDebits, List<StandingOrderDTO> standingOrders) {
        return new ProviderAccountDTO(AccountType.CREDIT_CARD, ZonedDateTime.now(), new BigDecimal(10),
                new BigDecimal(10), "accountId", "accountMask",
                mock(ProviderAccountNumberDTO.class), "bic", "name", CurrencyCode.PLN,
                false, mock(ProviderCreditCardDTO.class), new ArrayList<>(), directDebits, standingOrders, mock(ExtendedAccountDTO.class), null, null);
    }

    @Test
    public void shouldFilterOutDirectDebit() {
        //given
        doThrow(new RuntimeException("Some exception")).when(directDebitMock).validate();
        ProviderAccountDTO providerAccount = getProviderAccount(directDebitMock, standingOrderMock);
        //when
        ProviderAccountDTO filteredAccount = providerAccountUtil.validateAndFilterOptionalDetails(providerAccount);
        //then
        assertThat(filteredAccount.getDirectDebits()).hasSize(0);
        assertThat(filteredAccount.getStandingOrders()).hasSize(1);

    }

    @Test
    public void shouldFilterOutStandingOrder() {
        //given
        doThrow(new RuntimeException("Some exception")).when(standingOrderMock).validate();
        ProviderAccountDTO providerAccount = getProviderAccount(directDebitMock, standingOrderMock);
        //when
        ProviderAccountDTO filteredAccount = providerAccountUtil.validateAndFilterOptionalDetails(providerAccount);
        //then
        assertThat(filteredAccount.getDirectDebits()).hasSize(1);
        assertThat(filteredAccount.getStandingOrders()).hasSize(0);

    }

    @Test
    public void shouldFilterOutAllValuesThatDoesntPassValidation() {
        doThrow(new RuntimeException("Some exception")).when(standingOrderMock).validate();
        doThrow(new RuntimeException("Some exception")).when(directDebitMock).validate();
        List<DirectDebitDTO> directDebits = new ArrayList<DirectDebitDTO>() {{
            add(directDebitMock);
            add(mock(DirectDebitDTO.class));
        }};
        List<StandingOrderDTO> standingOrders = new ArrayList<StandingOrderDTO>() {{
            add(standingOrderMock);
            add(mock(StandingOrderDTO.class));
            add(mock(StandingOrderDTO.class));
        }};
        ProviderAccountDTO providerAccount = getProviderAccount(directDebits, standingOrders);
        //when
        ProviderAccountDTO filteredAccount = providerAccountUtil.validateAndFilterOptionalDetails(providerAccount);
        //then
        assertThat(filteredAccount.getDirectDebits()).hasSize(1);
        assertThat(filteredAccount.getStandingOrders()).hasSize(2);

    }
}
