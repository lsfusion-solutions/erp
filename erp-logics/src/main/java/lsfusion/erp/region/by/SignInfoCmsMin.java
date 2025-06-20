package lsfusion.erp.region.by;

import by.avest.crypto.pkcs11.provider.ProviderFactory;
import com.google.common.base.Throwables;
import lsfusion.base.SystemUtils;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.*;
import org.bouncycastle.util.encoders.Base64;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.security.*;

public class SignInfoCmsMin extends InternalAction {
    private final ClassPropertyInterface fileInterface;
    private final ClassPropertyInterface aliasInterface;
    private final ClassPropertyInterface passwordInterface;
    private final ClassPropertyInterface pathInterface;


    public SignInfoCmsMin(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        fileInterface = i.next();
        aliasInterface = i.next();
        passwordInterface = i.next();
        pathInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            String dataBase64 = (String) context.getDataKeyValue(fileInterface).getValue();
            byte[] dataBytes = Base64.decode(dataBase64);

            String alias = (String) context.getDataKeyValue(aliasInterface).getValue();
            String password = (String) context.getDataKeyValue(passwordInterface).getValue();
            String path = (String) context.getDataKeyValue(pathInterface).getValue();


            // Подписываем, получаем CMS
            byte[] cmsBytes = signData(dataBytes, alias, password.toCharArray(), path);

            // Возвращаем Base64
            findProperty("rusultSignInfo[]").change(Base64.toBase64String(cmsBytes), context);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }

    public byte[] signData(byte[] dataToSign, String keyAlias, char[] pass, String path) throws Exception {

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

                Signature signature = Signature.getInstance("BELTWITHBIGN", "AvUniversal");
                signature.initSign(key);
                signature.update(dataToSign);
                byte[] signedData = signature.sign();

                if (signedData != null){
                    X509Certificate cert = (X509Certificate) store.getCertificate(alias);
                    byte[] extValue = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
                    if (extValue != null) {
                        byte[] skiBytes = ASN1OctetString.getInstance(ASN1OctetString.getInstance(extValue).getOctets()).getOctets();
                        return buildCustomCMS(signedData, skiBytes);
                    }
                }
            }
        }
        return null;
    }

    public byte[] buildCustomCMS(byte[] signedData, byte[] skiBytes) throws IOException {
        ASN1EncodableVector cmsVector = new ASN1EncodableVector();

        cmsVector.add(new ASN1Integer(3)); // Версия
        cmsVector.add(new DERTaggedObject(false, 0, new DEROctetString(skiBytes))); // SKI

        cmsVector.add(new DERSequence(new ASN1Encodable[] {
                new ASN1ObjectIdentifier("1.2.112.0.2.0.34.101.31.81"),
                DERNull.INSTANCE
        })); // Hash алгоритм

        cmsVector.add(new DERSequence(new ASN1Encodable[] {
                new ASN1ObjectIdentifier("1.2.112.0.2.0.34.101.45.2.1"),
                DERNull.INSTANCE
        })); // Алгоритм подписи

        cmsVector.add(new DEROctetString(signedData)); // Подпись

        return new DERSequence(cmsVector).getEncoded();
    }
}