Secrets
=======

This document contains information about the secrets that this service uses.
The shell commands were writting on MacOS so you might need to alter the `base64` calls, the flags are different on Linux.

You may assume that the paragraphs with **How to generate this secret?** always produce base64 encoded output that is ready to be pasted in k8s.


site-management / encryption-key
---

**Type**

32 hex-encoded random bytes.

**Spring property**

`lovebird.encryptionKey`

**Getting the secret from k8s**

`$ kubectl get secret site-management -o json | jq -r '.data["encryption-key"]' | base64 -D`

**How to check if the currently configured secret is correct?**

`$ kubectl get secret site-management -o json | jq -r '.data["encryption-key"]' | base64 -D | wc -c`

Executing the above statement should result in `64`.

**How to generate this secret?**

`$ openssl rand 32 -hex | tr -d '\n' | base64`

Note the subtle removal of the newline.


providers / encryption-key
---

**Type**

32 hex-encoded random bytes.

**Spring property**

`lovebird.authenticationMeans.encryptionKey`

**Getting the secret from k8s**

`$ kubectl get secret providers -o json | jq -r '.data["encryption-key"]' | base64 -D`

**How to check if the currently configured secret is correct?**

`$ kubectl get secret providers -o json | jq -r '.data["encryption-key"]' | base64 -D | wc -c`

Executing the above statement should result in `64`.

**How to generate this secret?**

`$ openssl rand 32 -hex | tr -d '\n' | base64`

Note the subtle removal of the newline.


providers / key-import-encryption-key
---

**Type**

RSA public key in PEM format *without* the `----- BEGIN PUBLIC KEY -----` and corresponding end marker. 

**Spring property**

`yolt.providers.vaultMigrationEncryptionKey`

**Getting the secret from k8s**

```
$ kubectl get secret providers -o json | \
  jq -r '.data["key-import-encryption-key"]' | \
  base64 -D | base64 -D | \
  openssl rsa -pubin -inform der -noout -text
```

**How to check if the currently configured secret is correct?**

Check that it is a 2048 bit key.

**How to generate this secret?**

This secret is part of a keypair, when you change this secret, you *must* also change the corresponding secret that is used by the `backend` / `crypto` project at the same time.

Before you generate this secret, you *must* first generate the corresponding private key.
To do this, follow the instructions in the `SECRETS.md` file in the `backend` / `crypto` project.
Assuming the private key is stored in `${priv}`, run the below command to acquire `${pub}`.

`$ pub=$(echo "${priv}" | base64 -D | base64 -D | 2>/dev/null openssl rsa -pubout -inform der -outform der | base64 | base64)`

