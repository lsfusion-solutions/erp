package lsfusion.erp.region.by.finance.evat;

import lsfusion.base.SystemUtils;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class EVATClientAction implements ClientAction {
    public static boolean initialized;

    public Map<String, Map<Long, List<Object>>> files; //signAndSend
    public Map<String, Map<Long, String>> invoices; //getStatus
    public String serviceUrl; //"https://ws.vat.gov.by:443/InvoicesWS/services/InvoicesPort?wsdl"
    public String path; //"c:/Program Files/Avest/AvJCEProv";
    public String exportPath; //"c:/Program Files/Avest/AvJCEProv/archive";
    public String password; //"191217635";
    public String certNumber;
    public int certIndex;
    public boolean useActiveX;
    public int type;

    public EVATClientAction(Map<String, Map<Long, List<Object>>> files, Map<String, Map<Long, String>> invoices, String serviceUrl,
                            String path, String exportPath, String password, String certNumber, int certIndex, boolean useActiveX, int type) {
        this.files = files;
        this.invoices = invoices;
        this.serviceUrl = serviceUrl;
        this.path = path;
        this.exportPath = exportPath;
        this.password = password;
        this.certNumber = certNumber;
        this.certIndex = certIndex;
        this.useActiveX = useActiveX;
        this.type = type;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) {
        if(!initialized) {
            if(path != null) {
                String libraryPath = System.getProperty("java.library.path");
                String libPath = path + (SystemUtils.is64Arch() ? "/win64" : "/win32");
                if (libraryPath != null && !libraryPath.contains(libPath))
                    System.setProperty("java.library.path", libPath + (libraryPath.isEmpty() ? "" : (";" + libraryPath)));
                System.setProperty("by.avest.loader.shared", "true");

                addPath(path + "/avjavasecprov-shared.jar");
                addPath(path + "/avjavasecprovintf.jar");
                addPath(path + "/avoids.jar");
                addPath(path + "/avjceprovlib-avtoken-shared.jar");
                addPath(path + "/avjavaseckit.jar");
                addPath(path + "/avstores.jar");

                addPath(path + "/avedocclient.jar");
                addPath(path + "/avedoctool.jar");
            }
            initialized = true;
        }

        try {
            return new EVATWorker(files, invoices, serviceUrl, path, exportPath, password, certNumber, certIndex, useActiveX, type).execute();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return e.getMessage();
        } catch (ExecutionException e) {
            e.printStackTrace();
            return "Убедитесь, что все сертификаты актуальны. Выполните импорт СОС на портале.\n\n" + e.getMessage();
        }
    }

    public void addPath(String s) {
        try {
            File f = new File(s);
            URL u = f.toURL();
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class urlClass = URLClassLoader.class;
            Method method = urlClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(urlClassLoader, u);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}