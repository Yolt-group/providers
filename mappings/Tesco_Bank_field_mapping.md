## TESCO BANK

In this particular file one should find a list specifying how fields, that are exposed in Tesco Bank API: https://www.tescobank.com/developer/account-information-service-provider/, are
are mapped on our side.

## Accounts
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**                               /     **BANK MODEL JSON PATH**                                                   |
|    yoltAccountType                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  / Data.Account[].AccountSubType.CURRENT_ACCOUNT/SAVINGS_ACCOUNT/CREDIT_CARD      |
|    lastRefreshed	                           | current time in UTC offset                                                                                                              |
|    availableBalance                          | /ob.api.tescobank.com/open-banking/v3.1/aisp/balances  / Data.Balance[].Type.InterimAvailable                                           |
|    currentBalance                            | /ob.api.tescobank.com/open-banking/v3.1/aisp/balances  / Data.Balance[].Type.InterimBooked ( for Credit Cards - ClosingBooked )         |
|    accountId	                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  / Data.Account[].AccountId                                                       |
|    accountMaskedIdentification               | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  / Data.Account[].Account[].Identification ( for Credit Cards only )              |
|    bic	                                   |                                                                                                                                         |
|    name	                                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  / Data.Account[].nickname / Data.Account[].Account[].name (IBAN/SORTCODE) /Tesco Bank Account (fallback)|
|    currency                                  | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  / Data.Account[].Currency                                                        |
|    closed	                                   |                                                                                                                                         |
|    bankSpecific                              |                                                                                                                                         |
|    linkedAccounts                            |                                                                                                                                         |

## Account Number
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**                               /     **BANK MODEL JSON PATH**                                                   |                                                                                                                                                                  
|    holderName                                | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Account[].name                                                 |
|    scheme	                                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Account[].SchemeName ( exc. UK.OBIE )                          |
|    identification	                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Account[].Identification                                       |
|    secondaryIdentification                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Account[].SecondaryIdentification                              |
|    description	                           |                                                                                                                                         |
|                                              |                                                                                                                                         |

## Credit Card data
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**                               /     **BANK MODEL JSON PATH**                                                   |   
|   apr	                                       |                                                                                                                                         |
|   cashApr                                    |                                                                                                                                         |
|   dueAmount                                  |                                                                                                                                         |
|   dueDate                                    |                                                                                                                                         |
|   availableCreditAmount                      |                                                                                                                                         |
|   runningBalanceAmount	                   |                                                                                                                                         |
|   minPaymentAmount                           |                                                                                                                                         |
|   newChargesAmount	                       |                                                                                                                                         |
|   lastPaymentAmount	                       |                                                                                                                                         |
|   lastPaymentDate	                           |                                                                                                                                         |
|   totalCreditLineAmount                      |                                                                                                                                         |
|   cashLimitAmount	                           |                                                                                                                                         |
|                                              |                                                                                                                                         |   
                                                                            
## Extended Account
|   |   |
|---|---|  
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**                               /     **BANK MODEL JSON PATH**                                                   |                                     
|    resourceId                                | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].AccountId                                                      |
|    currency   	                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Currency                                                       |
|    name                                      |                                                                                                                                         |
|    product	                               |                                                                                                                                         |
|    cashAccountType	                       | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  / Data.Account[].AccountSubType.CURRENT_ACCOUNT/SAVINGS_ACCOUNT/CREDIT_CARD      |
|    status 	                               | ENABLED                                                                                                                                 |
|    bic                                       | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Servicer.Identification                                        |
|    linkedAccounts	                           |                                                                                                                                         |
|    usage                                     | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].AccountType ( mapped to CORPORATE/PRIVATE )                    |
|    details                                   |                                                                                                                                         |
|                                              |                                                                                                                                         |
|   **accountReferences []**                   |                            ---                                                                                                          |
|    type                                      | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].AccountType                                                    |
|    value      	                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Account[].Account.Identification                                         |
|                                              |                                                                                                                                         |
|   **balances []**                            |                            ---                                                                                                         |
|    balanceAmount                             | /ob.api.tescobank.com/open-banking/v3.1/aisp/balances  / Data.Balance[].Amount.Amount                                                  |
|    balanceType	                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Balance[].Type                                                          |
|    lastChangeDateTime                        | /ob.api.tescobank.com/open-banking/v3.1/aisp/accounts  /  Data.Balance[].DateTime                                                      |
|    referenceDate                             |                                                                                                                                        |
|    lastCommittedTransaction	               |                                                                                                                                        |

## Transactions
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**                                   /     **BANK MODEL JSON PATH**                                  |   
|    externalId                                | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].TransactionId                             |
|    dateTime	                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].BookingDateTime                           |
|    amount                                    | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].Amount.Amount                             |
|    status                                    | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].Status (BOOKED/PENDING/null)              |
|    type	                                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].CreditDebitIndicator (CREDIT/DEBIT/null)  |
|    description                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].TransactionInformation / " "              |                                                                                 
|    category                                  | GENERAL                                                                                                                    |
|    merchant                                  | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].MerchantDetails.MerchantName              |
|    bankSpecific                              |                                                                                                                            |
|                                              |                                                                                                                            |
|   **extendedTransaction**                    |                            ---                                                                                             |
|    status                                    | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].Status                                    |
|    transactionId	                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].TransactionId                             |
|    entryReference	                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].TransactionReference                      |
|    creditorId	                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].CreditorAccount.Identification            |
|    proprietaryBankTransactionCode	           | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].ProprietaryBankTransactionCode.Code       |
|    remittanceInformationUnstructured         | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].TransactionInformation                    |
|    remittanceInformationStructured 	       |                                                                                                                            |
|    purposeCode	                           |                                                                                                                            |
|    bankTransactionCode	                   |                                                                                                                            |
|    transactionIdGenerated	                   | true                                                                                                                       |                                                                                                      |
|    endToEndIn                                |                                                                                                                            |
|    mandateId	                               |                                                                                                                            |
|    checkId	                               |                                                                                                                            |
|    bookingDate                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].BookingDateTime                           |
|    valueDate	                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].ValueDateTime                             |
|                                              |                                                                                                                            |
|   TRANSACTION AMOUNT                         |                            ---                                                                                             |
|    currency                                  | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].Amount.Currency                           |
|    amount	                                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].Amount.Amount                             |
|                                              |                                                                                                                            |
|   ORIGINAL AMOUNT                            |                            ---                                                                                             |
|    currency                                  |                                                                                                                            |
|    amount	                                   |                                                                                                                            |
|                                              |                                                                                                                            |
|   CREDITOR ACCOUNT                           |                            ---                                                                                             |
|    type                                      | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].CreditorAccount.SchemeName (IBAN /.Identification)  |
|    value                                     | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[].CreditorAccount.Identification            | 
|    ultimateCreditor                          |                                                                                                                            |
|    debtorName                                |                                                                                                                            |
|                                              |                                                                                                                            |
|   DEBTOR ACCOUNT                             |                            ---                                                                                             |
|    type                                      |                                                                                                                            |
|    value  	                               |                                                                                                                            |
|    ultimateDebtor                            |                                                                                                                            |
|                                              |                                                                                                                            |
|   EXCHANGE RATE                              | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[]CurrencyExchange.ExchangeRate              |
|    currencyFrom                              | /ob.api.tescobank.com/open-banking/v3.1/aisp/transactions  /  Data.Transaction[]CurrencyExchange.SourceCurrency            |
|    rateFrom	                               |                                                                                                                            |
|    currencyTo                                |                                                                                                                            |
|    rateTo                                    |                                                                                                                            |
|    rateDate	                               |                                                                                                                            |
|    rateContract	                           |                                                                                                                            |
|    creditorName	                           |                                                                                                                            |
|                                              |                                                                                                                            |

## Direct Debits ( for Current, Savings only )
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                                                                        |
|    directDebitId                             | /ob.api.tescobank.com/open-banking/v3.1/aisp/direct-debits  /  Data.DirectDebit[].DirectDebitId                                    |
|    description                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/direct-debits  /  Data.DirectDebit[].Name                                             |
|    directDebitStatus 	                       | /ob.api.tescobank.com/open-banking/v3.1/aisp/direct-debits  /  Data.DirectDebit[].DirectDebitStatusCode = ACTIVE? (T/F)            |
|    previousPaymentDateTime	               | /ob.api.tescobank.com/open-banking/v3.1/aisp/direct-debits  /  Data.DirectDebit[].PreviousPaymentDateTime                          |
|    previousPaymentAmount	                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/direct-debits  /  Data.DirectDebit[].Data.DirectDebit[].PreviousPaymentAmount.Amount  |

## Standing Orders ( for Current, Savings only )
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                                                                        |
|    standingOrderId                           | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].StandingOrderId                              |
|    frequency                                 | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].Frequency ( to be described )                |
|    description                               | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].Reference                                    |
|    nextPaymentDateTime 	                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].NextPaymentDateTime                          |
|    nextPaymentAmount	                       | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].NextPaymentAmount.Amount                     |
|    finalPaymentDateTime	                   | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].FinalPaymentDateTime                         |
|                                              |                                                                                                                                    |
|   COUNTER PARTY                              |                            ---                                                                                                     |
|    holderName                                | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].CreditorAccount.Name                         |
|    scheme                                    | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].CreditorAccount.SchemeName ( exc. UK.OBIE )  |
|    identification                            | /ob.api.tescobank.com/open-banking/v3.1/aisp/standing-orders  /  Data.StandingOrder[].CreditorAccount.Identification               |
|    secondaryIdentfication                    |                                                                                                                                    |
|    description                               |                                                                                                                                    |