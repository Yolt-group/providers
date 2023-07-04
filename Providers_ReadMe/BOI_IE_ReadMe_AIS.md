## Bank of Ireland
[Current open problems on our end][1] 

## BIP overview 

|                                       |                                                                                         |
|---------------------------------------|-----------------------------------------------------------------------------------------|
| **Country of origin**                 | Ireland                                                                                 | 
| **Site Id**                           | 640e085a-7d4e-48f2-9844-2fea712834ee                                                    |
| **Standard**                          | Open Banking                                                                            |
| **Contact**                           | Contact Form : https://www.bankofireland.com/api/developer/contact/?developer=developer |
| **Developer Portal**                  | https://eu1.anypoint.mulesoft.com/exchange/portals/bankofireland/                       | 
| **Account SubTypes**                  | Current, Credits, Savings                                                               |
| **IP Whitelisting**                   | No                                                                                      |
| **AIS Standard version**              |                                                                                         |
| **PISP Standard version**             |                                                                                         |
| **Auto-onboarding**                   |                                                                                         |
| **Requires PSU IP address**           |                                                                                         |
| **Type of certificate**               | eIDAS QWAC and QSEAL                                                                    |
| **Signing algorithms used**           |                                                                                         |
| **Mutual TLS Authentication Support** |                                                                                         |

## Links - sandbox
 Bank of Ireland allows customers registered on the following bank channels to provide access to Third Parties.
 <br> 365 Online </br>
 <br> Business On Line </br>

|                                       |                                                                                       |
|---------------------------------------|---------------------------------------------------------------------------------------|
| **Well-known endpoint**               | https://auth-sandbox.bankofireland.com/oauth/as/b365/.well-known/openid-configuration | 
| **Redirect URL (Authorization Flow)** | https://auth-sandbox.bankofireland.com/oauth/as/b365/authorization.oauth2             |

## Links - production 

|                                       |                                                                                                                                                                                                           |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Well-known endpoint**               | 365 Online : https://auth.ob.bankofireland.com/oauth/as/b365/.well-known/openid-configuration <br>Business On Line: https://auth.ob.bankofireland.com/oauth/as/bol/.well-known/openid-configuration </br> |
| **Redirect URL (Authorization Flow)** | 365 Online:   https://auth.ob.bankofireland.com/oauth/as/b365/authorization.oauth2 <br>Business On Line: https://auth.ob.bankofireland.com/oauth/as/bol/authorization.oauth2 </br>                        |

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
* [Registration type is dynamic][2] <br> </br>
It is described in "Access our live APIs" point 
<br> You need to acquire eIDAS certs from a valid QTSP, upload in OB directory, get OB SSA and register with us via DCR process </br>

## Connection Overview

## Business and technical decisions

## Sandbox overview
  
## External links
* [Current open problems on our end][1]
* [Registration type][2]

[1]: <https://yolt.atlassian.net/issues/?jql=project%20%3D%20%22C4PO%22%20AND%20component%20%3D%20%22Bank%20of%20Ireland%22%20%20AND%20status%20!%3D%20Done%20AND%20Resolution%20%3D%20Unresolved%20ORDER%20BY%20status>
[2]: <https://eu1.anypoint.mulesoft.com/exchange/portals/bankofireland/pages/Getting%20Started/>