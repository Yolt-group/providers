# Providers

This project is a multi module project, containing a generic part, but mainly provider implementations.
All the AIS and PIS connection to sites (banks and some partners) are managed in this service.

## Providers using bankSpecific fields

**!IMPORTANT!**
Please check this list when **adding any new bank specific field** and **if the name is already on this list for a different provider**, check whether it changes the default behavior of the data that will be sent downstream.
If you want to avoid side-effects in some cases, consider renaming of the field you are about to add.

| Provider   | Field name         | Reason                                                                                                                                                                   | Description                                          |
|------------|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| ING_NL     | transactionType    | Developed under YCO-983 https://git.yolt.io/providers/bespoke-ingnl/-/merge_requests/127                                   | Maps transaction type fields coming from bank model. |
| ING_FR     | transactionType    | Developed under YCO-983 https://git.yolt.io/providers/bespoke-ingnl/-/merge_requests/127                                   | Maps transaction type fields coming from bank model. |
| ING_IT     | transactionType    | Developed under YCO-983 https://git.yolt.io/providers/bespoke-ingnl/-/merge_requests/127                                   | Maps transaction type fields coming from bank model. |
| REVOLUT    | allIbanIdentifiers | Developed under business decision to provide CASY with all it's identifiers. Developed under C4PO-10160 https://git.yolt.io/providers/open-banking/-/merge_requests/2009 | Providing list of possible IBANs for CASY.           |
| REVOLUT_EU | allIbanIdentifiers | Developed under business decision to provide CASY with all it's identifiers. Developed under C4PO-10160 https://git.yolt.io/providers/open-banking/-/merge_requests/2009 | Providing list of possible IBANs for CASY.           |

## Running locally providers - fastest scenario
For setting up Mutual TLS connections to banks, we often need to fetch private (transport) keys from Vault.
For authenticating to Vault, we use a Kubernetes Service Account token. Kubernetes always provides this token on ``/var/run/secrets/kubernetes.io/serviceaccount/token``.
By default providers will try to fetch the token there, except when we run it locally, because in application-local.yaml we have pointed it to a fake token.
When we want to run providers locally against a real Vault, we need to fetch a real token from Kubernetes and save it in a file on filesystem.
Alternatively we can just grab the secrets and run the app.

1. Mind that although for all AWS environments we use the same Vault, the service accounts from different environments have different rights. So, take the secret from the environment you are targeting.
2. Open git bash in providers project directory(Do not run it as ADMIN). Using git bash execute following command to find name of provider pod.
```
kubectl -n ycs get pods | grep providers
```
After you find the name of the pod execute following command to copy secrets from pod to directory team9 inside providers project,
```
kubectl -n ycs cp POD_NAME:/vault/secrets team9 -c providers
# For example:
# kubectl -n ycs cp providers-5fc84696b9-m2rsc:/vault/secrets team9 -c providers
```
This command should create directory ``team9`` inside providers directory with secrets files copied from team9 providers pod.    
After that you should set either set locally the environment variables or bypass the properties, remember to amend values according to real paths on your environment:
```
  yolt.vault.secret.location=file:///Users/MYUSER/vault-helper/team9_providers/
  yolt.vault.secrets.directory=/Users/MYUSER/vault-helper/team9_providers/
  yolt.vault.cassandra.vault-creds-file=file:///Users/MYUSER/vault-helper/team9_providers/cassandra
  management.server.ssl.key-store=/Users/MYUSER/vault-helper/team9_providers/keystore.p12
```
3. After the beforementioned variables are set, set one indicating your environment for example: ``ENVIRONMENT=team9, NAMESPACE=ycs``
4. Run the application with following profiles: ``local,local-team9``

Summarizing after finishing the beforementioned steps, you should have the following env variables set:
* ENVIRONMENT
* NAMESPACE
```
  yolt.vault.secret.location=file:///Users/MYUSER/vault-helper/team9_providers/
  yolt.vault.secrets.directory=/Users/MYUSER/vault-helper/team9_providers/
  yolt.vault.cassandra.vault-creds-file=file:///Users/MYUSER/vault-helper/team9_providers/cassandra
  management.server.ssl.key-store=/Users/MYUSER/vault-helper/team9_providers/keystore.p12
```

#Integration tests
Integration tests make use of Docker Test Containers. To be able to run tests you must have Docker installed on your local machine.

#Business decisions
- After YTS requested to change executionDateTime value in SEPA payment API to date and discussion within IT we decided to change it and fill date to 8:00 am in providers-web so all providers still receive time in app payment apis

## Context diagram
![context diagram](https://git.yolt.io/pages/backend-tools/yolt-architecture-diagram/downloaded-architecture-diagrams/providers.puml.svg?job=build)  
[source](https://git.yolt.io/backend-tools/yolt-architecture-diagram/)

## Alert Manager and PagerDuty: usage for Providers
For the need of migration alerting from Grafana to AlertManager the working document was created
describing how to use new approach and meet imposed the requirements:
https://yolt.atlassian.net/wiki/spaces/LOV/pages/8653998/Draft+PagerDuty+usage+for+providers

# Form Encryption for URL Providers
The clients api are using encryption on their side to encrypt the fields. 
Sample encryptors + Java decrypting code is placed in this repo under [here](codeSamples/asymetricPasswordEncryption).
