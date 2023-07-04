# Banque Internationale a Luxembourg (BIL)
[Current open problems on our end][1] 

## BIP overview 

|                                       |                                      |
|---------------------------------------|--------------------------------------|
| **Country of origin**                 | Luxembourg                           | 
| **Site Id**                           | 28319529-bd51-4032-b509-84bec8b40284 |
| **Standard**                          | STET                                 |
| **Contact**                           | E-mail: support_openbanking@bil.com  |
| **Developer Portal**                  | https://developer.bil.com/           | 
| **Account SubTypes**                  | Current Accounts                     |
| **IP Whitelisting**                   | No                                   |
| **AIS Standard version**              | Version 1.4                          |
| **PISP Standard version**             | Version 1.4                          |
| **Auto-onboarding**                   |                                      |
| **Requires PSU IP address**           |                                      |
| **Type of certificate**               | eIDAS certificate required: QSEAL    |
| **Signing algorithms used**           |                                      |
| **Mutual TLS Authentication Support** |                                      |

## Links - sandbox

|                            |                                       |
|----------------------------|---------------------------------------|
| **Base URL**               | https://api.bil.com/stet/ais/1.4      | 
| **Authorization Endpoint** | https://api.bil.com/stet/auth-sbx/1.4 |

## Links - production 

|                            |                                   |
|----------------------------|-----------------------------------|
| **Base URL**               | https://api.bil.com/stet/ais/1.4  | 
| **Authorization Endpoint** | https://api.bil.com/stet/auth/1.4 |

## Client configuration overview
Example fields (for each bank fields might be different)

|                                  |     |
|----------------------------------|-----|
| **Signing key id**               |     | 
| **Signing certificate**          |     | 
| **Transport key id**             |     |
| **Transport certificate**        |     |
| **Certificate Agreement Number** |     |
| **Client id**                    |     | 

## Registration details

During registration process (to get an acccess to production environment) the Bank requires us to sign the code using our private key. Without completing that activity, we are not able to access documentation and bank’s API. We have 3 tries to sign the code, in case of failure our account will be blocked.

## Connection Overview

## Sandbox overview
  
## External links
* [Current open problems on our end][1]

[1]: <https://yolt.atlassian.net/issues/?jql=project%20%3D%20%22C4PO%22%20AND%20component%20%3D%20%22BIL%22>