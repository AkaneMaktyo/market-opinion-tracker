package com.personal.tracker.service.trading.binance;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal.tracker.config.BinanceSpotProperties;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.OutputEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinanceRequestSignerTest {
  private static final String PASSPHRASE = "temporary-test-passphrase";

  @TempDir
  Path tempDir;

  @Test
  void signsWithEncryptedPkcs8RsaPrivateKey() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    Path pem = writeEncryptedPrivateKey(keyPair);
    BinanceRequestSigner signer = new BinanceRequestSigner(properties(pem, PASSPHRASE));
    String payload = "symbol=BTCUSDT&side=BUY&timestamp=1668481559918";

    String encodedSignature = signer.sign(payload);

    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(verifier.verify(Base64.getDecoder().decode(encodedSignature)));
  }

  @Test
  void signsWithPlainPkcs8RsaPrivateKeyWithoutPassphrase() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    Path pem = writePlainPrivateKey(keyPair);
    BinanceRequestSigner signer = new BinanceRequestSigner(properties(pem, ""));
    String payload = "symbol=ETHUSDT&side=BUY&timestamp=1668481559918";

    String encodedSignature = signer.sign(payload);

    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(verifier.verify(Base64.getDecoder().decode(encodedSignature)));
  }

  @Test
  void rejectsWrongPrivateKeyPassphrase() throws Exception {
    Path pem = writeEncryptedPrivateKey(rsaKeyPair());
    BinanceRequestSigner signer = new BinanceRequestSigner(properties(pem, "wrong-passphrase"));

    assertThrows(IllegalStateException.class, () -> signer.sign("timestamp=1"));
  }

  private Path writeEncryptedPrivateKey(KeyPair keyPair) throws Exception {
    Path pem = tempDir.resolve("rsa-private-key.pem");
    OutputEncryptor encryptor = new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC)
        .setProvider(new BouncyCastleProvider())
        .setRandom(new SecureRandom())
        .setPasssword(PASSPHRASE.toCharArray())
        .build();
    try (JcaPEMWriter writer = new JcaPEMWriter(java.nio.file.Files.newBufferedWriter(pem))) {
      writer.writeObject(new JcaPKCS8Generator(keyPair.getPrivate(), encryptor));
    }
    return pem;
  }

  private Path writePlainPrivateKey(KeyPair keyPair) throws Exception {
    Path pem = tempDir.resolve("plain-rsa-private-key.pem");
    try (JcaPEMWriter writer = new JcaPEMWriter(java.nio.file.Files.newBufferedWriter(pem))) {
      writer.writeObject(new JcaPKCS8Generator(keyPair.getPrivate(), null));
    }
    return pem;
  }

  private static KeyPair rsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static BinanceSpotProperties properties(Path pem, String passphrase) {
    BinanceSpotProperties properties = new BinanceSpotProperties();
    properties.setApiKey("test-api-key");
    properties.setKeyType("RSA");
    properties.setPrivateKeyPath(pem.toString());
    properties.setPrivateKeyPassphrase(passphrase);
    return properties;
  }
}
