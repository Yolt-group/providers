#Business decision records
## Failed accounts
*21.10.2019*<br><br>
There was a discussion whether we should fail all the accounts if at least one of the account fails (any viable reason)
or rather return to user at least some data (for example, 2 successful out of 3 accounts in total).
The decision was made to fail everything, main reasons are:
- if user will see not all the accounts, he/she will complain about that
- some other parties which base their logic on an account data may interpret partial data incorrectly

## OpenBanking - Article 10 API Restrictions
*06.12.2019*<br><br>
There was a discussion what to do in case of  banks implementing Article 10 restrictions. It allowed the TPP to access 
some data (Standing Orders, Direct Debits, Beneficiaries) in a short time window called initial consent window, which 
lasts some minutes after the user consent. After it expires all communications on the restricted endpoints end up with
403 Forbidden or other error.
The conclusion was that it is better to have data a bit deprecated and catch it inside the frame, and rely on it's 
renewal per each user's consent, than have no data at all.

##  DateTime timezones
*09.06.2020*<br><br>
There was a discussion regarding what timezones should we use to present dates returned by the bank.
The conclusion was to use bank headquarters timezone unless date is provided with an offset.