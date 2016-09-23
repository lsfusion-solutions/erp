package lsfusion.erp.region.by.finance.evat;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

public class EVATClientAction implements ClientAction {
    public static boolean initialized;

    public Map<String, Map<Integer, byte[]>> files;
    public String serviceUrl; //"https://ws.vat.gov.by:443/InvoicesWS/services/InvoicesPort?wsdl"
    public String path; //"c:/Program Files/Avest/AvJCEProv";
    public String exportPath; //"c:/Program Files/Avest/AvJCEProv/archive";
    public String password; //"191217635";
    public int type;

    public EVATClientAction(String serviceUrl, String path, String exportPath, String password, int type) {
        this(new HashMap<String, Map<Integer, byte[]>>(), serviceUrl, path, exportPath, password, type);
    }

    public EVATClientAction(Map<String, Map<Integer, byte[]>> files, String serviceUrl, String path, String exportPath, String password, int type) {
        this.files = files;
        this.serviceUrl = serviceUrl;
        this.path = path;
        this.exportPath = exportPath;
        this.password = password;
        this.type = type;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        if(!initialized) {
            String libraryPath = System.getProperty("java.library.path");
            String path32 = path + "/win32";
            if(libraryPath != null && !libraryPath.contains(path32))
                System.setProperty("java.library.path", path32 + (libraryPath.isEmpty() ? "" : (";" + libraryPath)));
            System.setProperty("by.avest.loader.shared", "true");

            addPath(path + "/avjavasecprov-shared.jar");
            addPath(path + "/avjavasecprovintf.jar");
            addPath(path + "/avoids.jar");
            addPath(path + "/avjceprovlib-avtoken-shared.jar");
            addPath(path + "/avjavaseckit.jar");
            addPath(path + "/avstores.jar");

            addPath(path + "/avedocclient.jar");
            addPath(path + "/avedoctool.jar");
            initialized = true;
        }

        switch (type) {
            case 0:
                return new EVATHandler().signAndSend(files, serviceUrl, path, exportPath, password);
            case 1:
                return new EVATHandler().listAndGet(path, serviceUrl, null, password);
            default:
                return null;
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