package com.yolt.providers.web.cryptography;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.*;

/**
 * A singleton keystore implementation to use in an SSLContext.
 * @see  com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManager#setupExternalRestTemplateBuilderFactory
 */
public class YoltKeyStoreSpi extends KeyStoreSpi {

    private String alias;
    private Key key;
    private Certificate[] chain;
    private Date creationDate;
    private Map<String, Certificate> certificates;

    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        key = null;
        chain = null;
        creationDate = null;
        certificates = new HashMap<>();
    }

    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        return key;
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return chain;
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        return certificates.get(alias);
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return creationDate;
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        this.creationDate = new Date();
        this.alias = alias;
        this.key = key;
        this.chain = chain;
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        this.certificates.put(alias, cert);
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> engineAliases() {
        Vector<String> aliases = new Vector<>(certificates.keySet());
        aliases.add(alias);
        return aliases.elements();
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return alias != null && (alias.equals(this.alias) || this.certificates.containsKey(alias));
    }

    @Override
    public int engineSize() {
        return this.key == null ? 0 : 1;
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return alias != null && alias.equals(this.alias);
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return certificates.containsKey(alias);
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        return certificates.entrySet().stream()
                .filter(entry -> entry.getValue().equals(cert))
                .findAny()
                .map(Map.Entry::getKey).orElse(null);
    }


}
