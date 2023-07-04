from jwcrypto import jwk, jwe

public_key = jwk.JWK.from_json("""
	{"alg":"RSA-OAEP-256","kty":"RSA","n":"tixrPVyrHFwff8Tv6dppJ6Pw1CJdMhWzMr7QqJg76_adNUQMl5f6C7b6_9l9nOeqdBqxyOycf3mianK0gHrVN3rwyPmh98yJiA_BeMzunMOe115vZew2d8XpEDXx8mbLSMnm7bKprKgvRKYf6B93ZfPTLosP6kabv12Pz9LR5NCx6joT55ii6uSFrvCilsvZRoeNxxOPPBCvAgd9sOxwROBUsXrgsgmpUzSuLxtdoRfNX7bPBi-znofAxChenDOcSGCv5lZ-0TKVhNzg0nsijjqOe5skwhRFBNGHj6Dg70tS5IcsQtz0hNRQCxBzDRrbn5TTBNo0So5G22g4l6cYJw","e":"AQAB"}
	""")
payload = "TOP_SECRET_UNBREAKABLE_PASSWORD"
protected_header = {
    "alg": "RSA-OAEP-256",
    "enc": "A256GCM"
}
jwetoken = jwe.JWE(payload.encode('utf-8'),
                   recipient=public_key,
                   protected=protected_header)
encrypted_password = jwetoken.serialize(True)
print(encrypted_password)
