package lsfusion.erp.region.by;

import com.google.common.base.Throwables;
import lsfusion.base.Pair;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.util.encoders.Base64;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.*;

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
            Object result = context.requestUserInteraction(new SignAvestClientAction(dataBytes, alias, password.toCharArray(), path));
            if (result instanceof Pair) {
                byte[] sign = ((Pair<byte[], X509Certificate>) result).first;
                X509Certificate cert = ((Pair<byte[], X509Certificate>) result).second;

                // Возвращаем Base64
                findProperty("rusultSignInfo[]").change(Base64.toBase64String(buildCustomCMS(sign, cert)), context);
                findProperty("rusultCert[]").change(Base64.toBase64String(cert.getEncoded()), context);
            } else {
                context.message((String) result, "Error");
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public byte[] buildCustomCMS(byte[] signedData, X509Certificate cert) throws IOException {

        byte[] extValue = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
        byte[] skiBytes;
        if (extValue != null) {
            skiBytes = ASN1OctetString.getInstance(ASN1OctetString.getInstance(extValue).getOctets()).getOctets();

            ASN1EncodableVector cmsVector = new ASN1EncodableVector();

            cmsVector.add(new ASN1Integer(3)); // Версия
            cmsVector.add(new DERTaggedObject(false, 0, new DEROctetString(skiBytes))); // SKI

            cmsVector.add(new DERSequence(new ASN1Encodable[]{
                    new ASN1ObjectIdentifier("1.2.112.0.2.0.34.101.31.81"),
                    DERNull.INSTANCE
            })); // Hash алгоритм

            cmsVector.add(new DERSequence(new ASN1Encodable[]{
                    new ASN1ObjectIdentifier("1.2.112.0.2.0.34.101.45.2.1"),
                    DERNull.INSTANCE
            })); // Алгоритм подписи

            cmsVector.add(new DEROctetString(signedData)); // Подпись

            return new DERSequence(cmsVector).getEncoded();
        }
        return null;
    }
}