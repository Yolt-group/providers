# General rules
1) "**Faster release procedure**" can be applied only when releasing just a single service, _providers_. If there are more services to release, proceed with "**Extended release procedure**".
2) Person able to do a release must be mentioned in allowed CKs in _gitlab-ci.yml_.
3) In case of any issues occurring during a release or after a release, the person who is doing / done a release is responsible for fix it.
4) If there is such case, the person who will be doing a release can ask and assign someone else who will do a verification after _providers_ service release. The person has to be asked before the release!

# Terms
* Release owner = Person responsible for a release
* Release reviewer = Person responsible for a review of release

# Faster release procedure
1) Release owner verifies if entry about previous release in [changelog][1] contains pipeline number (important in case of revert!).
2) Release owner verifies if changes requires performance tests (is there at least one change that has mark *requires
   performance tests* in [changelog][1]):
    * If there is such change, then the "extended" release, that involves ACC, has to be performed.
3) Release owner claims release penguin and posts in _#c4po_be_penguin_ channel the release candidate that will be releases (e.g. "
   RC: https://git.yolt.io/providers/providers/pipelines/315556")
4) Release owner resets _team9_ environment to existing production version (the latest pipeline from [here][3]).
5) Release owner deploys master branch to _team9_ environment:
    * If _team9_ environment doesn't work for some reason (e.g. one of services is always "not getting up"), contact with _**C4PO Ops of the Day**_, _**@c4po-backenders**_ (in that order).
6) Release owner runs [cucumber tests][2] in team9 (_team9 team-c4po suite_):
    * If tests failed, first run it again and then in the meantime analyze the source of failure:
        * If the same tests still fail and analysis shows the same source of failure, which is not know , then the "extended" release, that involves ACC environments, has to be performed.
    * If tests passed, you have to wait for verification of the process to be made by release reviewer (4 eye principle)
7) Release reviewer performs following actions:
   * verifies that previous release in [changelog][1] contains pipeline number
   * verifies that performance tests weren't needed
   * verifies that environment has been reset and version of _providers_ service is the same as that one mentioned as release candidate on _#c4po_be_penguin_ channel
   * verifies that cucumber tests passed
   * If everything was checked and looks good, release reviewer runs step _good to go to prod_ for related _providers_ service pipeline (the one that was deployed and tested in team9).
8) Release owner runs step _Deploy to prd_ for ALL production environments (YFB PRD, YFB EXT PRD and YFB Sandbox) and after that verifies deployment status in production environments with release reviewer
    * In case of "pod not starting up", release owner brings back previous version (by running _Deploy to prd_ job of
      previous released master) and analyzes why deployment to PRD failed.
        * To find previous released version, go to [changelog][1] and in last release there is information about release
          candidate.
        * Check in previous version pipeline if there was a repin of _config-server_ made. In such case you have to repin it again after deployment of this version to production. It can be done by pressing _Repin config &amp; deploy_ buttons.
    * In case of successful deployment to all production environments, release owner informs about this C4PO on _#c4po_be_penguin_ channel
9) Release owner and reviewer (or person assigned as part of general rule #4) verify Grafana and Kibana metrics, looking for any traces of regression:
    * [YFB Kibana errors and warns][4]
    * [YFB EXT Kibana errors and warns][5]
    * [YFB Kibana Sandbox errors and warns][6]
    * [Grafana Monitoring Panel - All env][7]
    * [Grafana OpenBanking - General][8]
    * [Grafana OpenBanking - Issues][9]
    * [Grafana OpenBanking - Requests and responses per second][10]
        * In case of anything suspicious, any traces of regression, release owner bring back previous version (by running _Deploy to prd_ job of previous released master) and analyzes why deployment to PRD failed.
        * Check in previous version pipeline if there was a repin of _config-server_ made. In such case you have to repin it again after deployment of this version to production. It can be done by pressing _Repin config &amp; deploy_ buttons.
10) Release owner creates "new release" in [changelog][1], that must contain:
     * what was released (table content),
     * who did a release,
     * who did a review,
     * who verified Grafana and Kibana,
     * which _providers_ master pipeline was released.
11) Release owner sets the release penguin free in #c4po_be_penguin channel.

# Extended release procedure
1) Release owner verifies if entry about previous release in changelog contains pipeline number (important in case of revert!).
2) Release owner, deploys master branch to _team9_ environment. If this was already done as part of "Faster" release
   procedure and team9 was unstable, then skip this step:
    * If _team9_ environment doesn't work for some reason (e.g. one of services is always "not getting up"), contact
      with _**C4PO Ops of the Day**_, _**@c4po-backenders**_ (in that order) and proceed to the next step
3) Release owner posts in  _#c4po_be_penguin_ channel the release candidate that will be releases (e.g. "
   RC: https://git.yolt.io/providers/providers/pipelines/315556")
4) Release owner claims (or queues and waits) the release bird in #be_release channel.
5) Release owner resets acceptance to current version in production (from last production pipeline in _environment-config_ run _Copy environment to _ -> _yfb-acc (from production)_ - it deploys versions of pods from production, so wait until process is finished.
6) Release owner, deploys _providers_ master branch to acceptance environment (YFB ACC) via _Deploy to dta_ -> _yfb-prd_ step from relate _providers_ pipeline.
    * If other services have to be released as well, run the same job for them using their pipelines.
7) Release owner runs [cucumber tests][2] in yfb-acc (_yfb-acc acceptance suite_):
    * If other services are released you can also run tests using their pipelines. There are dedicated _yfb-acc cucumber_ buttons
    * If performance tests are necessary you can run them using _Downstream_ -> _performance_tests_ job
8) Release owner observes the outcome of cucumber and performance tests
    * If tests failed, run them again and in the meantime analyze the source of failure:
        * If the same tests still fail and brief analysis doesn't show the source of issue, ask for help the owners of failing tests.
9) Release reviewer performs following actions:
    * verifies that previous release in [changelog][1] contains pipeline number
    * verifies that environment has been reset and version of _providers_ (the same as that one mentioned as release candidate on _#c4po_be_penguin_ channel) and other released services are correct
    * verifies that cucumber tests passed
    * verifies that performance tests were run if needed and passed
    * If everything was checked and looks good, release reviewer runs step _good to go to prod_ for related _providers_ service pipeline (the one that was deployed and tested in team9) and for other services if release requires such action.
10) Release owner runs step _Deploy to prd_ for ALL production environments (YFB PRD, YFB EXT PRD and YFB Sandbox) for all released services and after that verifies deployment status in production environments with release reviewer
    * In case of "pod not starting up", release owner brings back previous version (by running _Deploy to prd_ job of
      previous released master) and analyzes why deployment to PRD failed.
        * To find previous released version, go to [changelog][1] and in last release there is information about release
          candidate.
        * Check in previous version pipeline if there was a repin of _config-server_ made. In such case you have to repin it again after deployment of this version to production. It can be done by pressing _Repin config &amp; deploy_ buttons.
    * In case of successful deployment to all production environments, release owner informs about this C4PO on _#c4po_be_penguin_ channel
11) Release owner and reviewer (or person assigned as part of general rule #4) verify Grafana and Kibana metrics, looking for any traces of regression:
    * [YFB Kibana errors and warns][4]
    * [YFB EXT Kibana errors and warns][5]
    * [YFB Kibana Sandbox errors and warns][6]
    * [Grafana Monitoring Panel - All env][7]
    * [Grafana OpenBanking - General][8]
    * [Grafana OpenBanking - Issues][9]
    * [Grafana OpenBanking - Requests and responses per second][10]
        * In case of anything suspicious, any traces of regression, release owner bring back previous version (by running _Deploy to prd_ job of previous released master) and analyzes why deployment to PRD failed.
        * Check in previous version pipeline if there was a repin of _config-server_ made. In such case you have to repin it again after deployment of this version to production. It can be done by pressing _Repin config &amp; deploy_ buttons.
13) Release owner creates "new release" in [changelog][1], that must contain:
    * what was released (table content),
    * who did a release,
    * who did a review,
    * who verified Grafana and Kibana,
    * which _providers_ master pipeline was released.
14) Release owner sets the release bird free in #be_release channel.

# Providers repin procedure
1) Release owner claims (or queues and waits) the release bird in #be_release channel.
2) Release owner runs step _Repin config & deploy_ for ALL production environments (YFB PRD, YFB EXT PRD and YFB Sandbox) and after that verifies deployment status in production environments with release reviewer
    * In case of "pod not starting up", release owner revert changes from _config-server_, which are the root cause of the problem and perform another repin action.
3) Release owner and reviewer (or person assigned as part of general rule #4) verify Grafana and Kibana metrics, looking for any traces of regression:
    * [YFB Kibana errors and warns][4]
    * [YFB EXT Kibana errors and warns][5]
    * [YFB Kibana Sandbox errors and warns][6]
    * [Grafana Monitoring Panel - All env][7]
    * [Grafana OpenBanking - General][8]
    * [Grafana OpenBanking - Issues][9]
    * [Grafana OpenBanking - Requests and responses per second][10]
        * In case of anything suspicious, any traces of regression, release owner revert changes from _config-server_, which are the root cause of the problem and perform another repin action.
4) Release owner sets the release bird free in #be_release channel.
5) Release owner click _Run pipeline_ button on [providers pipelines][11] form to pin newest _config-server_ version also to the newest master

[1]: https://yolt.atlassian.net/wiki/spaces/LOV/pages/3903043/C4PO+owned+services+changelog "Providers Changelog"
[2]: https://git.yolt.io/backend/cucumber-tests/-/pipelines "Cucumber tests"
[3]: https://git.yolt.io/deployment/environment-config/-/pipelines?page=1&scope=all&ref=production "Production pipelines"
[4]: https://kibana.yfb-prd.yolt.io/goto/601f9ea496ea7bf66de747771da69cf0 "Kibana YFB PRD"
[5]: https://kibana.yfb-ext-prd.yolt.io/goto/ef15b45560756118a9208eeefb4a5b52 "Kibana YFB EXT PRD"
[6]: https://kibana.yfb-sandbox.yolt.io/goto/581ad9ee24393ea933291b0a5b5f6f59 "Kibana YFB Sandbox"
[7]: https://grafana.yolt.io/d/LU5G3rEMk/monitoring-panel-all-env?orgId=1 "Grafana Monitoring Panel - All env"
[8]: https://grafana.yolt.io/d/0x0BJt4Gk/open-banking-management-information-general?orgId=1 "Grafana OpenBanking - General"
[9]: https://grafana.yolt.io/d/FFBL1t4Mz/open-banking-management-information-issues?orgId=1 "Grafana OpenBanking - Issues"
[10]: https://grafana.yolt.io/d/YF-8JpVMz/open-banking-management-information-requests-and-responses-per-second?orgId=1 "Grafana OpenBanking - Requests and responses per second"
[11]: https://git.yolt.io/providers/providers/-/pipelines "Providers pipelines"
