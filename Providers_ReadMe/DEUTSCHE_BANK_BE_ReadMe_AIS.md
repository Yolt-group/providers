## Deutsche Bank Belgium
[Current open problems on our end][1] 

## BIP overview 

|                                       |                                                                                                                            |
|---------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| **Country of origin**                 | Belgium                                                                                                                    | 
| **Site Id**                           | 8aa1a301-144b-4715-8d48-442c5020b595                                                                                       |
| **Standard**                          | Berlin Group                                                                                                               |
| **Contact**                           | E-mail: xs2a.api@db.com                                                                                                    |
| **Developer Portal**                  | https://developer.db.com/                                                                                                  | 
| **Account SubTypes**                  | As bank stated: All types of accounts are supported, they should be online banking enabled. Mostly it is current accounts. |
| **IP Whitelisting**                   | No                                                                                                                         |
| **AIS Standard version**              | 1.5.7                                                                                                                      |
| **PISP Standard version**             | 1.5.7                                                                                                                      |
| **Auto-onboarding**                   |                                                                                                                            |
| **Requires PSU IP address**           |                                                                                                                            |
| **Type of certificate**               | eIDAS QWAC                                                                                                                 |
| **Signing algorithms used**           |                                                                                                                            |
| **Mutual TLS Authentication Support** |                                                                                                                            |

## Links - sandbox
Refer to developer portal / sandbox section, self onboard yourself, post which you can get Postman collection with all use cases/endpoints/ Test Data spreadsheet/ Testing Guideline. 

## Links - production 

|              |                      |
|--------------|----------------------|
| **Base URL** | https://xs2a.db.com/ | 

The XS2A Interface is resource oriented. Resources can be addressed under the API endpoints https://xs2a.db.com/{service-group}/{country-code}/{business-entity}/{version}/{service}{?query-parameters}

Example : https://xs2a.db.com/piis/DE/DB/v1/funds-confirmations

Details can be found in the ticket C4PO-8194
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

## Connection Overview
Be aware to take a look at documentation 8.2.2.10. as those might be type of API specific for BE

## Sandbox overview
  
## Business and technical decisions
  
## External links
* [Current open problems on our end][1]

[1]: <https://yolt.atlassian.net/issues/?jql=project%20%3D%20%22C4PO%22%20AND%20component%20%3D%20%22Deutsche%20Bank%20Belgium%22%20%20AND%20status%20!%3D%20Done%20AND%20Resolution%20%3D%20Unresolved%20ORDER%20BY%20status>
