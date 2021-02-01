package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.DefaultImportAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.silvertunnel_ng.netlib.adapter.url.NetlibURLStreamHandlerFactory;
import org.silvertunnel_ng.netlib.api.NetFactory;
import org.silvertunnel_ng.netlib.api.NetLayer;
import org.silvertunnel_ng.netlib.api.NetLayerIDs;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;

public class EurooptAction extends DefaultImportAction {

    String userAgent = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36";

    String logPrefix = "Import Euroopt: ";

    public EurooptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected NetLayer getNetLayer() {
        NetLayer lowerNetLayer = NetFactory.getInstance().getNetLayerById(NetLayerIDs.TOR);
        // wait until TOR is ready (optional):
        lowerNetLayer.waitUntilReady();
        return lowerNetLayer;
    }

    protected Document getDocument(NetLayer lowerNetLayer, String mainPage, String url) throws IOException {
        int count = 3;
        while (count > 0) {
            try {
                Thread.sleep(50);
                if (lowerNetLayer == null) {
                    Connection connection = Jsoup.connect(url);
                    connection.timeout(10000);
                    connection.userAgent(userAgent);
                    return connection.get();
                } else {
                    URLConnection urlConnection = getTorConnection(lowerNetLayer, mainPage, url);
                    try (InputStream responseBodyIS = urlConnection.getInputStream()) {
                        return Jsoup.parse(responseBodyIS, "utf-8", "");
                    }
                }
            } catch (HttpStatusException | SocketTimeoutException | ConnectException e) {
                count--;
                if (count <= 0) {
                    ERPLoggers.importLogger.error(logPrefix + "error for url " + url + ": ", e);
                } else {
                    try {
                        ERPLoggers.importLogger.error(logPrefix + "error for url " + url + ", will retry");
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        return null;
    }

    protected URLConnection getTorConnection(NetLayer lowerNetLayer, String mainPage, String url) throws IOException {
        // prepare URL handling on top of the lowerNetLayer
        NetlibURLStreamHandlerFactory factory = new NetlibURLStreamHandlerFactory(false);
        // the following method could be called multiple times
        // to change layer used by the factory over the time:
        factory.setNetLayerForHttpHttpsFtp(lowerNetLayer);

        // send request with POST data
        URLConnection urlConnection = new URL(null, mainPage + url, factory.createURLStreamHandler("https")).openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.connect();
        return urlConnection;
    }
}