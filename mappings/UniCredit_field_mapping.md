## UNICREDIT

In this particular file one should find a list specifying how fields, that are exposed via UniCredit API: https://developer.unicredit.eu/, are
mapped in Accounts & Transactions microservice.


|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**    |
|    yoltAccountType                           | /hydrogen/v1/accounts        / $.accounts[].cashAccountType [supported: CURRENT, CREDIT_CARD]  |
|    lastRefreshed	                           | current time in UTC offset                                                                     |
|    availableBalance                          | /hydrogen/v1/balances        / $.balances[?(@.balanceType == 'interimAvailable')].amount       |
|    currentBalance                            | /hydrogen/v1/balances        / $.balances[?(@.balanceType == 'expected')].amount               |
|    accountId	                               | /hydrogen/v1/accounts        / $.accounts[].resourceId                                     |
|    accountMaskedIdentification               |                                                                                            |
|    bic	                                   | /hydrogen/v1/accounts        / $.accounts[].bic                                            |
|    name	                                   | /hydrogen/v1/accounts        / $.accounts[].name                                           |
|    currency                                  | /hydrogen/v1/accounts        / $.accounts[].currency                                       |
|    closed	                                   |                                                                                            |
|                                              |                                                                                            |
|   **accountNumber**                          |                            ---                                                             |
|    holderName                                |                                                                                            |
|    scheme	                                   | /hydrogen/v1/accounts        / IBAN                                                        |
|    identification	                           | /hydrogen/v1/accounts        / $.accounts[].iban                                           |
|    secondaryIdentification                   |                                                                                            |
|    description	                           |                                                                                            |
|                                              |                                                                                            |
|   **creditCardData**                         |                            ---                                                             |
|   apr	                                       |                                                                                            |
|   cashApr                                    |                                                                                            |
|   dueAmount                                  |                                                                                            |
|   dueDate                                    |                                                                                            |
|   availableCreditAmount                      | /hydrogen/v1/accounts          / $.balances[?(@.balanceType == 'interimAvailable')].amount |
|   runningBalanceAmount	                   |                                                                                            |
|   minPaymentAmount                           |                                                                                            |
|   newChargesAmount	                       |                                                                                            |
|   lastPaymentAmount	                       |                                                                                            |
|   lastPaymentDate	                           |                                                                                            |
|   totalCreditLineAmount                      |                                                                                            |
|   cashLimitAmount	                           |                                                                                            |
|                                              |                                                                                            |
|   **extendedAccount**                        |                            ---                                                             |
|    resourceId                                | /hydrogen/v1/accounts        / $.accounts[].resourceId                                     |
|    currency   	                           | /hydrogen/v1/accounts        / $.accounts[].currency                                       |
|    name                                      |                                                                                            |
|    product	                               |                                                                                            |
|    cashAccountType	                       | /hydrogen/v1/accounts        / $.accounts[].cashAccountType                                |           
|    status 	                               |                                                                                            |
|    bic                                       | /hydrogen/v1/accounts        / $.accounts[].bic                                            |
|    linkedAccounts	                           |                                                                                            |
|    usage                                     |                                                                                            |
|    details                                   |                                                                                            |
|                                              |                                                                                            |
|   **accountReferences []**                   |                            ---                                                             |
|    type                                      | IBAN                                                                                       |
|    value      	                           | /hydrogen/v1/accounts        / $.accounts[].iban                                           |                                                                                        |


## Balances


|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                                |
|    balanceAmount                             |                                                                                            |
|    currency                                  | /hydrogen/v1/balances         / balances[].balanceAmount.currency                          |
|    amount                                    | /hydrogen/v1/balances         / balances[].balanceAmount.amount                            |          
|    balanceType	                           | /hydrogen/v1/balances        / $.balances[].balanceType                                    |
|    lastChangeDateTime                        | /hydrogen/v1/balances        / $.balances[].lastChangeDateTime                             |
|    referenceDate                             | /hydrogen/v1/balances        / $.balances[].referenceDate                                  |
|    lastCommittedTransaction	               | /hydrogen/v1/balances        / $.balances[].lastCommittedTransaction                       |
|    bankSpecific                              |                                                                                            |
|    linkedAccount                             |                                                                                            |

## Transactions
|   |   |
|---|---|
|     **YOLT DATA MODEL**                      |    **BANK API ENDPOINT**     /     **BANK MODEL JSON PATH**                                        |
|    externalId                                | /hydrogen/v1/transactions        / $.transactions.booked/pending[].transactionId                   |
|    dateTime	                               | /hydrogen/v1/transactions        / $.transactions.booked/pending[].bookingDate                     |
|    amount                                    | /hydrogen/v1/transactions        / $.transactions.booked/pending[].transactionAmount.amount        |
|    status                                    | based on transaction origin (booked or pending)                                                    |
|    type	                                   | calculated based on $.transactions.booked/pending[].transactionAmount.amount sign                  |
|    description                               | /hydrogen/v1/transactions      / $.transactions.booked/pending[].remittanceInformationUnstructured |
|    category                                  | GENERAL                                                                                            |
|    merchant                                  | /hydrogen/v1/transactions        / $.transactions.booked/pending[].creditorName                    |
|    bankSpecific                              |                                                                                                    |
|                                              |                                                                                                    |
|   **extendedTransaction**                    |                            ---                                                                     |
|    status                                    |  based on transaction origin (booked or pending)                                                   |
|    transactionId	                           | /hydrogen/v1/transactions       / $.transactions.booked/pending[].transactionId                    |
|    entryReference	                           | /hydrogen/v1/transactions       / $.transactions.booked/pending[].entryReference                   |
|    endToEndIn                                | /hydrogen/v1/transactions       / $.transactions.booked/pending[].endToEndId                       |
|    mandateId	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].mandateId                        |
|    checkId	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].checkId                          |
|    creditorId	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].creditorId                       |
|    bookingDate                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].bookingDate                      |
|    valueDate	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].valueDate                        |
|    ultimateCreditor                          | /hydrogen/v1/transactions       / $.transactions.booked/pending[].ultimateCreditor                 |                                                                
|    debtorName                                | /hydrogen/v1/transactions       / $.transactions.booked/pending[].debtorName                       |                                                        
|    ultimateDebtor                            | /hydrogen/v1/transactions       / $.transactions.booked/pending[].ultimateDebtor                   |
|    remittanceInformationUnstructured         | /hydrogen/v1/transactions       / $.transactions.booked/pending[].remittanceInformationUnstructured|
|    remittanceInformationStructured 	       | /hydrogen/v1/transactions       / $.transactions.booked/pending[].remittanceInformationStructured  |
|    purposeCode	                           | /hydrogen/v1/transactions       / $.transactions.booked/pending[].purposeCode                      |
|    bankTransactionCode	                   | /hydrogen/v1/transactions       / $.transactions.booked/pending[].bankTransactionCode              |
|    proprietaryBankTransactionCode	           | /hydrogen/v1/transactions       / $.transactions.booked/pending[].proprietaryBankTransactionCode   |
|    transactionIdGenerated	                   | false                                                                                              |
|                                              |                                                                                                    |
|   **transactionAmount**                      |                            ---                                                                     |
|    currency                                  | /hydrogen/v1/transactions       / $.transactions.booked[].transactionAmount.currency               |
|    amount	                                   | /hydrogen/v1/transactions       / $.transactions.booked[].transactionAmount.amount                 |
|                                              |                                                                                                    |
|   **originalAmount**                         |                            ---                                                                     |
|    currency                                  |                                                                                                    |
|    amount	                                   |                                                                                                    |
|                                              |                                                                                                    |
|   **exchangeRate []**                        |                            ---                                                                     |
|                                              |                                                                                                    |
|    currencyFrom                              | /hydrogen/v1/transactions       / $.transactions.booked/pending[].exchangeRate[].currencyFrom      |                                                                           
|    rateFrom	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].exchangeRate[].rateFrom          |                                                                        
|    currencyTo                                | /hydrogen/v1/transactions       / $.transactions.booked/pending[].exchangeRate[].currencyTo        |                                                                         
|    rateTo                                    | /hydrogen/v1/transactions       / $.transactions.booked/pending[].exchangeRate[].rateTo            |                                                                     
|    rateDate	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].exchangeRate[].rateDate          |                                                                       
|    rateContract	                           | /hydrogen/v1/transactions       / $.transactions.booked/pending[].exchangeRate[].rateContract      |                                                                           
|                                              |                                                                                                    |
| **creditorAccount**                          |                            ---                                                                     |
|    type                                      | IBAN                                                                                               |
|    value                                     | /hydrogen/v1/transactions       / $.transactions.booked/pending[].creditorAccount.iban             |
|                                              |                                                                                                    |
|   **debtorAccount**                          |                            ---                                                                     |
|                                              |                                                                                                    |
|    type                                      | IBAN                                                                                               |
|    value  	                               | /hydrogen/v1/transactions       / $.transactions.booked/pending[].debtorAccount.iban               |                                                                  

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
