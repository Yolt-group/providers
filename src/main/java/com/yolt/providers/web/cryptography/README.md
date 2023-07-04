# Security providers and a bug we encountered
We spent a ridiculous amount chasing a weird bug over the past two days.
Because we learned some useful things we thought it would be nice to share a little about what we learned for posterity.

The bug
=======
We added a feature and an accompanying test to the providers service.
Most details aren't important except one: adding `@AutoConfigureWireMock(port = 0)` to the `@IntegrationTestContext` (see https://git.yolt.io/providers/providers/-/commit/d2d5a8c0542c47396fd806861766e3c4694585dc#204848aa735ffc36f5d9d39626cb7c56e4f7cf1d_29_30).
The above change caused seemingly unrelated tests to fail with a TLS handshake error, the tests in question were in the file `MutualTLSRestTemplateManagerTest`.

The failure
===========
After some debugging we figured out that the TLS handshake could not complete because server (wiremock) and client (the object under test supplied by `MutualTLSRestTemplateManager`) could not agree on a SignatureScheme during the TLS handshake.
For some reason, adding the `@AutoConfigureWireMock(port = 0)` annotation caused several SignatureSchemes to disappear from the TLS clients Hello message (the RSASSA-PSS signature schemes).

A hint
======
A hint to where we should look to remedy this problem could be found in the `@BeforeAll` and `@AfterAll` blocks of `MutualTLSRestTemplateManagerTest` where SecurityProviders were being reordered (see https://git.yolt.io/providers/providers/-/blob/d2d5a8c0542c47396fd806861766e3c4694585dc/src/test/java/com/yolt/providers/web/cryptography/transport/MutualTLSRestTemplateManagerTest.java#L119).
This seemed a little strange but it's a good hint of where to look: the SecurityProviders.

A discovery
===========
We discovered that, *if* the YoltSecurityProvider is installed as the 1st provider to supply RSASSA-PSS *and after that* Javas SSL functionality is invoked, the SignatureSchemes associated with RSASSA-PSS are NOT available during a TLS handshake.
This is weird because the YoltSecurityProvider does supply the RSASSA-PSS functionality.
After some debugging we determined that during initialization of the SignatureSchemes (code inside javas SSL implementation), the method `SignatureSpi#engineGetParameter` is called.
YoltSecurityProvider provides a delegating SignatureSpi that can only know what the delegate must be when a key is available (a HSM key or a 'real' key), during initialization there is no key, and hence `SignatureSpi#engineGetParameter` throws an exception, making SignatureScheme think that RSASSA-PSS is not supported.
This causes the rsa_pss_rsae_sha256 and associated signature algorithms to not be present in the Client Hello TLS messages which made the `MutualTLSRestTemplateManager` test fail because client and server could not agree on a scheme.
Conversely, *if* Javas SSl functionality is invoked *and after that* the YoltSecurityProvider is installed, the associated suite *is* marked as available)
This, the unavailability of rsa_pss_rsae_sha256 because YoltSecurityProvider was installed first, is what happened when we tried to connect with the 'autoconfigured' wiremock. Or this is what happens when you try to start providers locally, or execute any https request in the integration tests for that matter.
The question now is, why *is* rsa_pss_rsae_sha256 available on our team environments and on production (we checked this) while we load the YoltSecurityProvider very early during the application lifecycle?
In our application (providers) we add the SecurityProviders to the list as part of the class ApplicationConfiguration.
ApplicationConfiguration is clearly loaded very early by the classloader, and (one would think) is loaded prior to the application using any TLS/SSL functionality, and therefore the rsa_pss_rsae_sha256 scheme should not be available.

Enter: config-server
====================
Prior to our app doing anything useful when it is not ran locally, it first retrieves additional configuration from the config-server.
This retrieval is done prior to the ApplicationContext being loaded and it is also done over TLS (https://config-server/...).
This took us a good chunk of time to figure out, it was only after enabling ssl debugging and deploying the application that everything clicked.

What now?
=========
Clearly it is desirable for all SecurityProviders to be installed prior to any SSL functionality to be used.
If we configure our app in this way (by loading the providers using system properties for instance) the rsa_pss_rsae_sha256 scheme will not be available unless we make a change to YoltSecurityProvider.