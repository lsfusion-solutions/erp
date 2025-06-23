package lsfusion.erp.region.by;

import by.avest.crypto.pkcs11.provider.ProviderFactory;
import lsfusion.base.Pair;
import lsfusion.base.SystemUtils;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.*;

public class SignAvestClientAction implements ClientAction {
    byte[] dataToSign;
    String keyAlias;
    char[] pass;
    String path;

    public SignAvestClientAction(byte[] dataToSign, String keyAlias, char[] pass, String path) {
        this.dataToSign = dataToSign;
        this.keyAlias = keyAlias;
        this.pass = pass;
        this.path = path;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        try {
            return signData(dataToSign, keyAlias, pass, path);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public Object signData(byte[] dataToSign, String keyAlias, char[] pass, String path) throws Exception {
        String libraryPath = System.getProperty("java.library.path");
        String libPath = path + (SystemUtils.is64Arch() ? "/win64" : "/win32");
        if (libraryPath != null && !libraryPath.contains(libPath))
            System.setProperty("java.library.path", libPath + (libraryPath.isEmpty() ? "" : (";" + libraryPath)));
        System.setProperty("by.avest.loader.shared", "true");

        ProviderFactory.addAvUniversalProvider();

        KeyStore store = KeyStore.getInstance("AvPersonal");
        store.load(null, null);

        Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (alias.equals(keyAlias)) {
                PrivateKey key = (PrivateKey) store.getKey(alias, pass);
                X509Certificate cert = (X509Certificate) store.getCertificate(alias);
                if (cert != null) {
                    Signature signature = Signature.getInstance("BELTWITHBIGN", "AvUniversal");
                    signature.initSign(key);
                    signature.update(dataToSign);
                    return new Pair<>(signature.sign(), cert);
                }
            }
        }
        return "Alias not found";
    }
}
