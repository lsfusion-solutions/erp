//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package lsfusion.erp.region.by.finance.evat;

import by.avest.certstore.Util;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;

public class CustomAvCertStoreProvider extends Provider {
    private static final long serialVersionUID = -1705206564205077288L;
    private static final String name = "AvCertStoreProvider";
    private static final double version = 1.0D;
    private static final String info = "The Avest certificate store provider.";

    public CustomAvCertStoreProvider() {
        super("AvCertStoreProvider", 1.0D, "The Avest certificate store provider.");
        if(Util.isDebug()) {
            Util.log("avstores loading, provider name: " + this.getName());
        }

        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                if(Util.isDebug()) {
                    Util.log("installing factories");
                }

                CustomAvCertStoreProvider.this.put("CertStore.AvDir", "by.avest.certstore.dir.DirectoryCertStore");
                CustomAvCertStoreProvider.this.put("CertStore.AvDirSingle", "by.avest.certstore.dir.SingleDirectoryCertStore");
                CustomAvCertStoreProvider.this.put("CertStore.AvDB", "by.avest.certstore.db.DatabaseCertStore");
                CustomAvCertStoreProvider.this.put("AttrCertStore.Collection", "by.avest.certstore.CollectionAttrCertStore");
                CustomAvCertStoreProvider.this.put("AttrCertStore.AvDirSingle", "by.avest.certstore.dir.SingleDirectoryAttrCertStore");
                CustomAvCertStoreProvider.this.put("CertPathBuilder.PKIXAttr", "by.avest.crypto.certpath.PKIXAttrCertPathBuilder");
                CustomAvCertStoreProvider.this.put("CertPathValidator.PKIXAttr", "by.avest.crypto.certpath.PKIXAttrCertPathValidator");
                CustomAvCertStoreProvider.this.put("AttrCertStore.AvStoreXml", "by.avest.certstore.avstore.AvCertAttrCertStore");
                return null;
            }
        });
    }
}