package lsfusion.erp.region.by;

import com.google.common.base.Throwables;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.util.encoders.Base64;

import lsfusion.server.logics.UtilsLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

public class SignInfoCmsMin extends InternalAction {
    private final ClassPropertyInterface fileInterface;

    public SignInfoCmsMin(UtilsLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        fileInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            String cmsBase64 = (String) context.getDataKeyValue(fileInterface).getValue();
            // 1. Декодируем CMS
            byte[] cmsBytes = Base64.decode(cmsBase64);
            CMSSignedData signedData = null;

            signedData = new CMSSignedData(cmsBytes);

            // 2. Берём первого подписанта
            SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
            // 3. Извлекаем messageDigest из SignedAttributes
            AttributeTable attrs = signer.getSignedAttributes();
            Attribute digestAttr = attrs.get(PKCSObjectIdentifiers.pkcs_9_at_messageDigest);
            ASN1OctetString digest = (ASN1OctetString) digestAttr.getAttrValues().getObjectAt(0);
            byte[] hashBytes = digest.getOctets();
            // 4. Подпись
            byte[] signatureBytes = signer.getSignature();
            // 5. Собираем signInfo вручную
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(new ASN1Integer(3));
            v.add(new DERTaggedObject(false, 0, new DEROctetString(hashBytes)));
            v.add(new DERSequence(new ASN1Encodable[] {
                    new ASN1ObjectIdentifier("1.2.112.0.2.0.34.101.31.81"), DERNull.INSTANCE }));
            v.add(new DERSequence(new ASN1Encodable[] {
                    new ASN1ObjectIdentifier("1.2.112.0.2.0.34.101.45.2.1"), DERNull.INSTANCE }));
            v.add(new DEROctetString(signatureBytes));
            DERSequence signInfo = new DERSequence(v);
            byte[] signInfoBytes = signInfo.getEncoded("DER");
            // 6. Возвращаем Base64
            findProperty("rusultSignInfo[]").change(Base64.toBase64String(signInfoBytes), context);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }
}