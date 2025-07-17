package lsfusion.erp.region.ru.utils.cryptopro;

import com.google.common.base.Throwables;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.util.CollectionStore;
import ru.CryptoPro.CAdES.CAdESSignature;
import ru.CryptoPro.CAdES.CAdESType;
import ru.CryptoPro.CAdES.exception.CAdESException;
import ru.CryptoPro.Crypto.CryptoProvider;
import ru.CryptoPro.JCP.JCP;
import ru.CryptoPro.reprov.RevCheck;
//import sun.security.jca.Providers;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.*;

public class CryptoPro {

    static {
        int bcindex = 1;//Providers.getFullProviderList().getIndex("BC");
        if (bcindex == -1) {
            Security.addProvider(new JCP()); // провайдер JCP
            Security.addProvider(new RevCheck());
            Security.addProvider(new CryptoProvider());// провайдер шифрования JCryptoP
        } else {
            Security.insertProviderAt(new JCP(), bcindex + 1); // провайдер JCP
            Security.insertProviderAt(new RevCheck(), bcindex + 2);
            Security.insertProviderAt(new CryptoProvider(), bcindex + 3);// провайдер шифрования JCryptoP
        }

        System.setProperty("com.sun.security.enableCRLDP", "true");
        System.setProperty("ocsp.enable", "true");
    }

    public static PrivateKey loadConfiguration(String storeType, String storeFile,
                                               char[] storePassword, String alias, char[] password,
                                               List<Certificate> certs,
                                               List<X509CertificateHolder> chain) throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            IOException, UnrecoverableKeyException {

        KeyStore keyStore = KeyStore.getInstance(storeType);
        keyStore.load(storeFile == null || storeFile.isEmpty() ? null : new FileInputStream(storeFile),
                storePassword);

        PrivateKey privateKey =
                (PrivateKey) keyStore.getKey(alias, password);

        // Получаем цепочку сертификатов.
        certs.addAll(Arrays.asList(keyStore.getCertificateChain(alias)));

        certs.forEach(cert -> {
            try {
                chain.add(new X509CertificateHolder(cert.getEncoded()));
            } catch (IOException | CertificateEncodingException e) {
                throw Throwables.propagate(e);
            }
        });

        return privateKey;
    }

    public static byte[] sign(byte[] data, boolean detached, String storeFile, char[] storePassword, String alias, char[] password, Integer algorithm) {
        try {
            List<Certificate> certs = new ArrayList<Certificate>();
            List<X509CertificateHolder> chain = new ArrayList<X509CertificateHolder>();
            PrivateKey privateKey = loadConfiguration(JCP.HD_STORE_NAME, storeFile, storePassword, alias, password, certs, chain);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            CAdESSignature signature = new CAdESSignature(detached);
            signature.setCertificateStore(new CollectionStore(chain));

            signature.addSigner(JCP.PROVIDER_NAME,
                    null,
                    null,
                    privateKey,
                    certs,
                    (algorithm == null ? CAdESType.CAdES_BES : algorithm),
                    null,
                    false,
                    null,
                    null);
            signature.open(out);
            signature.update(data);
            signature.close();
            return out.toByteArray();

        } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | CAdESException | KeyStoreException e) {
            throw Throwables.propagate(e);
        }
    }
}
