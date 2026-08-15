package com.personal.tracker.service.trading.binance;

import com.personal.tracker.config.BinanceSpotProperties;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.springframework.stereotype.Component;

@Component
public class BinanceRequestSigner {
  private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
  private final BinanceSpotProperties properties;
  private volatile PrivateKey cachedRsaPrivateKey;

  public BinanceRequestSigner(BinanceSpotProperties properties) {
    this.properties = properties;
  }

  public String sign(String payload) {
    return switch (properties.keyType()) {
      case "HMAC" -> hmac(payload);
      case "RSA" -> rsa(payload);
      default -> throw new IllegalStateException(
          "不支持的币安签名类型：" + properties.keyType());
    };
  }

  private String hmac(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(
          properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("生成币安 HMAC 签名失败", error);
    }
  }

  private String rsa(String payload) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(rsaPrivateKey());
      signature.update(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (IllegalStateException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalStateException("生成币安 RSA 签名失败", error);
    }
  }

  private PrivateKey rsaPrivateKey() {
    PrivateKey current = cachedRsaPrivateKey;
    if (current != null) return current;
    synchronized (this) {
      if (cachedRsaPrivateKey == null) {
        cachedRsaPrivateKey = loadPrivateKey();
      }
      return cachedRsaPrivateKey;
    }
  }

  private PrivateKey loadPrivateKey() {
    char[] passphrase = properties.privateKeyPassphrase().toCharArray();
    try {
      ensureProvider();
      Path path = Path.of(properties.privateKeyPath()).toAbsolutePath().normalize();
      if (!Files.isRegularFile(path)) {
        throw new IllegalStateException("币安 RSA 私钥文件不存在");
      }
      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
           PEMParser parser = new PEMParser(reader)) {
        Object pemObject = parser.readObject();
        PrivateKeyInfo privateKeyInfo;
        if (pemObject instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
          if (passphrase.length == 0) {
            throw new IllegalStateException("币安 RSA 私钥已加密，但私钥密码未配置");
          }
          InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder()
              .setProvider(PROVIDER)
              .build(passphrase);
          privateKeyInfo = encrypted.decryptPrivateKeyInfo(decryptor);
        } else if (pemObject instanceof PrivateKeyInfo plainPrivateKey) {
          privateKeyInfo = plainPrivateKey;
        } else {
          throw new IllegalStateException(
              "币安 RSA 私钥文件不是 PKCS#8 私钥，当前路径可能指向了公钥文件");
        }
        PrivateKey privateKey = new JcaPEMKeyConverter()
            .setProvider(PROVIDER)
            .getPrivateKey(privateKeyInfo);
        if (!"RSA".equalsIgnoreCase(privateKey.getAlgorithm())) {
          throw new IllegalStateException("配置的 PEM 不是 RSA 私钥");
        }
        return privateKey;
      }
    } catch (IllegalStateException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalStateException("无法读取币安 RSA 私钥，请检查文件格式或私钥密码", error);
    } finally {
      Arrays.fill(passphrase, '\0');
    }
  }

  private static synchronized void ensureProvider() {
    if (Security.getProvider(PROVIDER) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }
}
