## CREDIT MUTUEL

In this particular file one should find a list specifying how fields, that are exposed in
Credit Mutuel/CIC API: https://www.creditmutuel.fr/oauth2/en/devportal/stetpsd2-spec-v1.1.html#/, are mapped on our side.
## Accounts


|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**    |
|    yoltAccountType                           | /stet-psd2-api/v1.1/accounts  / $.accounts[].cashAccountType.CACC /ExternalCashAccountType.CURRENT |
|    lastRefreshed	                           | current time in UTC offset                                                                         |
|    availableBalance                          | /stet-psd2-api/v1.1/accounts / $.accounts[].balances[].balanceAmount.amount.XPCD                   |
|    currentBalance                            | /stet-psd2-api/v1.1/accounts / $.accounts[].balances[].balanceAmount.amount.CLBD                   |
|    accountId	                               | /stet-psd2-api/v1.1/accounts / $.accounts[].resourceId                                             |
|    accountMaskedIdentification               |                                                                                                    |
|                                              |                                                                                                    |
|   **accountNumber**                          |                            ---                                                                     |
|    holderName                                |                                                                                                    |
|    scheme	                                   |                                                                                                    |
|    identification	                           | /stet-psd2-api/v1.1/accounts  / $.accounts[].accountId.iban                                        |
|    secondaryIdentification                   |                                                                                                    |
|    description	                           |                                                                                                    |
|    bic	                                   |                                                                                                    |
|    name	                                   | /stet-psd2-api/v1.1/accounts  / $.accounts[].name                                                  |
|    currency                                  | /stet-psd2-api/v1.1/accounts  / $.accounts[].accountId.currency                                    |
|    closed	                                   |                                                                                                    |
|                                              |                                                                                                    |
|   **creditCardData**                         |                            ---                                                                     |
|   apr	                                       |                                                                                                    |
|   cashApr                                    |                                                                                                    |
|   dueAmount                                  |                                                                                                    |
|   dueDate                                    |                                                                                                    |
|   availableCreditAmount                      | /stet-psd2-api/v1.1/accounts   / $.accounts[].balances[@Type='OTHR'].amount                        |
|   runningBalanceAmount	                   |                                                                                                    |
|   minPaymentAmount                           |                                                                                                    |
|   newChargesAmount	                       |                                                                                                    |
|   lastPaymentAmount	                       |                                                                                                    |
|   lastPaymentDate	                           |                                                                                                    |
|   totalCreditLineAmount                      |                                                                                                    |
|   cashLimitAmount	                           |                                                                                                    |
|                                              |                                                                                                    |
|   **extendedAccount**                        |                            ---                                                                     |
|    resourceId                                | /stet-psd2-api/v1.1/accounts / $.accounts[].resourceId                                             |
|                                              |                                                                                                    |
|   **accountReferences []**                   |                            ---                                                                     |
|    type                                      | /stet-psd2-api/v1.1/accounts / $.accounts[].accountId.iban                                         |
|    value      	                           | /stet-psd2-api/v1.1/accounts / $.accounts[].accountId.iban                                         |
|    currency   	                           | /stet-psd2-api/v1.1/accounts / $.accounts[].accountId.currency                                     |
|    name                                      | /stet-psd2-api/v1.1/accounts / $.accounts[].name                                                   |
|    product	                               |                                                                                                    |
|    cashAccountType	                       | /stet-psd2-api/v1.1/accounts  / $.accounts[].cashAccountType.CACC /ExternalCashAccountType.CURRENT |
|    status 	                               | ENABLED                                                                                            |
|    bic                                       |                                                                                                    |
|    linkedAccounts	                           | /stet-psd2-api/v1.1/accounts  / $.accounts[].accountId.linkedAccounts                              |
|    usage                                     | PRIVATE                                                                                            |
|    details                                   |                                                                                                    |

## Balances


|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                      |
|    balanceAmount                             | /stet-psd2-api/v1.1/balances / $.balances[].balanceAmount.amount                 |
|    balanceType	                           | /stet-psd2-api/v1.1/balances / $.balances[].balanceType                          |
|    lastChangeDateTime                        |                                                                                  |
|    referenceDate                             | /stet-psd2-api/v1.1/balances / $.balances[].referenceDate                        |
|    lastCommittedTransaction	               |                                                                                  |
|    bankSpecific                              |                                                                                  |
|    linkedAccount                             |                                                                                  |

## Transactions
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**    |
|    externalId                                | /stet-psd2-api/v1.1/transactions / $.transactions[].resourceId                                  |
|    dateTime	                               | /stet-psd2-api/v1.1/transactions / $.transactions[].bookingDate                                 |
|    amount                                    | /stet-psd2-api/v1.1/transactions / $.transactions.booked[].transactionAmount.amount             |
|    status                                    | /stet-psd2-api/v1.1/transactions / $.transactions[].status                                      |
|    type	                                   | /stet-psd2-api/v1.1/transactions / $.transactions[].creditDebitIndicator                        |
|    description                               | /stet-psd2-api/v1.1/transactions / $.transactions[].remittanceInformation.unstructured OR "N/A" |
|    category                                  | GENERAL                                                                                         |
|    merchant                                  |                                                                                                 |
|    bankSpecific                              |                                                                                                 |
|                                              |                                                                                                 |
|   **extendedTransaction**                    |                            ---                                                                  |
|    status                                    | /stet-psd2-api/v1.1/transactions / $.transactions[].status                                      |
|    transactionId	                           |                                                                                                 |
|    entryReference	                           | /stet-psd2-api/v1.1/transactions / $.transactions[].resourceId                                  |
|    endToEndIn                                |                                                                                                 |
|    mandateId	                               |                                                                                                 |
|    checkId	                               |                                                                                                 |
|    creditorId	                               |                                                                                                 |
|    bookingDate                               | /stet-psd2-api/v1.1/transactions / $.transactions.booked[].bookingDate                          |
|    valueDate	                               | /stet-psd2-api/v1.1/transactions / $.transactions[].valueDate                                   |
|                                              |                                                                                                 |
|   **transactionAmount**                      |                            ---                                                                  |
|    currency                                  | /stet-psd2-api/v1.1/transactions / $.transactions[].transactionAmount.currency                  |
|    amount	                                   | /stet-psd2-api/v1.1/transactions / $.transactions[].transactionAmount.amount(ABS)               |
|                                              |                                                                                                 |
|   **originalAmount**                         |                            ---                                                                  |
|    currency                                  |                                                                                                 |
|    amount	                                   |                                                                                                 |
|                                              |                                                                                                 |
|   **exchangeRate []**                        |                            ---                                                                  |
|                                              |                                                                                                 |
|    currencyFrom                              |                                                                                                 |
|    rateFrom	                               |                                                                                                 |
|    currencyTo                                |                                                                                                 |
|    rateTo                                    |                                                                                                 |
|    rateDate	                               |                                                                                                 |
|    rateContract	                           |                                                                                                 |
|    creditorName	                           |                                                                                                 |
|                                              |                                                                                                 |
| **creditorAccount**                          |                            ---                                                                  |
|    type                                      |                                                                                                 |
|    value                                     |                                                                                                 |
|    ultimateCreditor                          |                                                                                                 |
|    debtorName                                |                                                                                                 |
|                                              |                                                                                                 |
|   **debtorAccount**                          |                            ---                                                                  |
|                                              |                                                                                                 |
|    type                                      |                                                                                                 |
|    value  	                               |                                                                                                 |
|    ultimateDebtor                            |                                                                                                 |
|    remittanceInformationUnstructured         | /stet-psd2-api/v1.1/transactions / $.transactions[].remittanceInformation.unstructured[]        |
|    remittanceInformationStructured 	       |                                                                                                 |
|    purposeCode	                           |                                                                                                 |
|    bankTransactionCode	                   |                                                                                                 |
|    proprietaryBankTransactionCode	           |                                                                                                 |
|    transactionIdGenerated	                   |                                                                                                 |

## Direct Debits
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                      |
|    directDebitId                             |                                                                                  |
|    description                               |                                                                                  |
|    directDebitStatus 	                       |                                                                                  |
|    previousPaymentDateTime	               |                                                                                  |
|    previousPaymentAmount	                   |                                                                                  |

## Standing Orders
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                      |
|    standingOrderId                           |                                                                                  |
|    frequency                                 |                                                                                  |
|    description                               |                                                                                  |
|    nextPaymentDateTime 	                   |                                                                                  |
|    nextPaymentAmount	                       |                                                                                  |
|    finalPaymentDateTime	                   |                                                                                  |
|                                              |                                                                                  |
| **counterParty**                             |                            ---                                                   |
|    holderName                                |                                                                                  |
|    scheme                                    |                                                                                  |
|    identification                            |                                                                                  |
|    secondaryIdentfication                    |                                                                                  |
|    description                               |                                                                                  |
