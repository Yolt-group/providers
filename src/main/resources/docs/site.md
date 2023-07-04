# Working with sites

This document contains information on how to work with sites.


## How to: add a site

*Note*: this chapter is about **us (Yolt)** adding a **new** site, it is **not** about a [user adding a site](../functions/add-site.md).

Sometimes we've finished a [provider](../concepts/provider.md) implementation, this document details how to make the site available.

Next to adding a site record by cql, there are other things that needs to be added for a site:
* A consent template : site_consent_template
* In case you want to expose it for Yolt or ING FR (Deprecated) : popular_country_site_ranking

Note that the first attribute is also only used for yolt. 


## Test sites

We can divide test banks in some categories:
1) Sites that serve as all-purpose, reliable test site that can be used by other clients. 
2) Internal only - Sites that actually exist on an external party (scraper). That means that we depend on the availability of this scraper, 
which means we cannot guarantee that adding this bank will work.
3) Internal only - Sites that 'stub' the actual external dependency. Yoltbank contains a best effort similarity of the real external dependency. 
That way we can guarantee that it works. This allows us to test the data-provider that connects to this bank. Primarily useful for scrapers,
because all 3 scrapers are a little bit different. Although it does not leak through our API, we can still have automated E2E tests that 
guarantee that a data-provider for a scraper still works after changes.
4) Internal only - Sites that are merely introduced to test a particular scenario. That means, a bank that belongs in a 'migration group' for example.

#### Reliable all purpose site(s)

| site-id                              | site-name            | description |
| ------------------------------------ |----------------------|-------------|
| 33aca8b9-281a-4259-8492-1b37706af6db | Yolt test bank | This is the general all-purpose test bank that is exposed for all clients. | 

#### Test sites of scrapers
| site-id                              | site-name   | group-name |description |
| ------------------------------------ |-------------|-------------|------------|
| 93bac548-d2de-4546-b106-880a5018460d | Budget Insight Testbank 1 | | Uses the BUDGET_INSIGHT data-provider. Is just internally to test the BI Scraping flow. |
| 6f2c7414-4883-4523-9aaf-4286b667a9af | Budget Insight Testbank 2 | | Same as above. Once added to resolve bugs because then the only client Yolt didn't allow multiple user-sites to 1 site. Therefore a copy was created so you can add this site 2 times. |
| e278a008-bf45-4d19-bb5d-b36ff755be58 | Yodlee test bank | | Test bank internally to mock the 'yodlee scraping flow'. This is a test bank hosted by yodlee. |
| 7842941f-61f0-4607-9ea9-14afd1ec8bcd | Demo Bank MFA | | This is currently the only bank that allows us to test a FORM + FORM flow. It is hosted by yodlee. |                                 
| 33e51254-d12a-4380-aed6-f5f912d8da9f | Saltedge Fake Bank Simple | | Simple test bank of saltedge | 
| ca001c16-02d3-4214-885f-1aad65bb1ad4 | Saltedge Fake Demo Bank | | Simple test bank of saltedge (todo: difference with above?) | 
| 4788b519-cc26-419c-aaae-f8e4e60f8bea | Saltedge Fake Bank API with Fields | | A test bank that allows us to internally test a particular flow. |
| 8ed5d8b8-d06e-46cc-bb14-3a1da967bcf0 | Saltedge Fake Bank Simple with Possible Duplicates | | " | 
| 4c0e5db2-99bf-47a3-b155-29e8e11dd6d4 | Saltedge Fake Bank with 2 step Interactive | | " | 
| 65561dd0-f56d-4eb7-9371-b50b95ef2208 | Saltedge Fake Bank with Error |  | " | 
| b68717db-e74b-4098-b054-6fe7b00ccece | Saltedge Fake Bank with optional Fields |  | " | 
| 26b19b69-fba3-4176-9be7-eaba0c2f7e3a | Saltedge Fake Bank with optional SMS |  | " | 
| 98847a0e-fbe7-4e9d-861f-6b7d4c1e016c | Saltedge Fake Bank with rememberable credentials |  | " | 
| 9f25852f-aa6c-418a-8a2c-ff87648affbb | Saltedge Fake Bank with Select |  | " | 
| 19bf09ca-8104-47a8-8860-451b5d80ab32 | Saltedge Fake Bank with SMS |  | " | 
| 33f6c521-9e4d-49e7-8414-9e87f7b5ada1 | Saltedge Fake Bank with Token |  | " | 
| 5effb41e-911e-434c-b85d-3980e3285e89 | Saltedge Fake Bank with Updates |  | " | 

#### Test sites stubbing out scraper
| site-id                              | site-name   | group-name |description |
| -------------------------------------|-------------| ------------|------------|
| 8d25dc30-7dc9-11e8-adc0-fa7ae01bbebc | Yoltbank-Yodlee flow  | | Uses YODLEE dataprovider, but yodlee is stubbed out by yoltbank. |
| 0285a320-7dca-11e8-adc0-fa7ae01bbebc | Yoltbank-Budget insight flow  | | Uses BUDGET_INSIGHT dataprovider, but BI is stubbed out by yoltbank. |
| 082af309-8f16-452c-a48e-0a8830b603b7 | Yoltbank-Saltedge flow  | | Uses SALTEDGE dataprovider, but saltedge is stubbed out by yoltbank. |

#### Scenarios 
| site-id                              | site-name    | group-name |description |
| ------------------------------------ | -------------| ------------|------------|
| 4f958fc6-220e-11e8-b467-0ed5f89f718b | Migration group yodlee test bank (migration) | Yolt test Open banking | Same as 'Yodlee test bank'. However, this bank is grouped in a 'migration group'. This only exists so Yolt can test migration that relies on this group. This is the bank to START migration. |
| 6dbddacf-8195-46c1-b57e-c0beeea49990 | Migration group yodlee test bank (migrated) | Yolt test Open banking | Same as 'Migration group yodlee test bank (migration)', except that this is the target bank with fewer (remainder) of supported accounts. |
| 21170e28-fe88-465c-8bff-9f6288416b76 | Scen. 1: FR Bank Migration Success  | Scen. 1: FR Bank Migration Success | ING FR only.  | 
| 333e1b97-1055-4b86-a112-bc1db801145f | Scen. 1: FR Bank Migration Success direct connection  | Scen. 1: FR Bank Migration Success | ING FR only.  | 
| de337ce7-dc43-4971-b0c1-b3ca00b2118c | Scen. 2: FR Bank Migration Success Remainder still on scraping - all account types  | Scen. 2: FR Bank Migration Success Remainder still on scraping | ING FR only. | 
| 840d4df3-07d2-4d2e-b177-6db8f4cea479 | Scen. 2: FR Bank Migration Success Remainder still on scraping - direct connection  | Scen. 2: FR Bank Migration Success Remainder still on scraping | ING FR only.  | 
| acb1c151-5a58-4fa5-bb8b-7830c519678a | Scen. 2: FR Bank Migration Success Remainder still on scraping - remainder (savings/credit) only  | Scen. 2: FR Bank Migration Success Remainder still on scraping | ING FR only.  | 
| a10de3d4-93f4-4346-8391-42a8319852b2 | Scen. 3: FR Bank Migration Partial migration (noLongerSupported)  | Scen. 3: FR Bank Migration Partial migration (noLongerSupported) | ING FR only.  | 
| 035ba2f1-f751-4d71-be88-3e6649ad1051 | Scen. 3: FR Bank Migration Partial migration (noLongerSupported) direct connection  | Scen. 3: FR Bank Migration Partial migration (noLongerSupported) | ING FR only.  | 
| 7bdbe19f-c564-4dd9-bb89-9873a3fb2037 | Scen. 4: FR Bank Migration No migration (noLongerSupported)  | Scen. 4: FR Bank Migration No migration (noLongerSupported) | ING FR only.  | 
| fedcc4d1-3e22-4d79-8947-d8e363622533 | Scen. 5: FR Bank Migration Partial migration (noLongerSupported)  | Scen. 5: FR Bank Migration Partial migration (noLongerSupported) | ING FR only.  | 
| 106a46ec-05b1-4a32-a9fb-ce728c47ce1f | Scen. 5: FR Bank Migration Partial migration (noLongerSupported)  | Scen. 5: FR Bank Migration Partial migration (noLongerSupported) | ING FR only. See  | 

#### Others (to be removed)
| site-id                              | site-name     | group-name |description |
| ------------------------------------ |---------------| ------------|------------|
| 45ea79d9-e1fc-4a61-812d-5672e970606f | Ceska Test bank  | | can be removed? (c4po)| 
| 828e4f90-2773-45c2-9199-cbf9264ef1cc | ING NL Test bank  | | can be removed? (c4po) |  
| ed1fd770-db06-4e93-9199-abccceae3820 | Polish Test bank  | | Used to test a special 'polish api flow'. Nobody uses this anymore, but we're not really allowed to throw it away? | 
| ca8a362a-a351-4358-9f1c-a8b4b91ed65b | Yolt test Open banking  | Yolt test Open banking | currently the only way to test the whole flow including M-TLS + signing. This is to be introduced in yolt provider, so after that it is redundant. | 
