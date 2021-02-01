package lsfusion.erp.region.by.euroopt;

import com.google.common.base.Throwables;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.silvertunnel_ng.netlib.api.NetLayer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

public class GetItemUrlEurooptAction extends EurooptAction {

    private final ClassPropertyInterface barcodeInterface;

    public GetItemUrlEurooptAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        barcodeInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String barcode = (String) context.getDataKeyValue(barcodeInterface).getValue();
            String mainPage = (String) findProperty("captionMainPage[]").read(context);

            if (barcode != null && mainPage != null) {
                boolean useTor = findProperty("ImportEuroopt.useTor[]").read(context) != null;
                NetLayer lowerNetLayer = useTor ? getNetLayer() : null;

                String itemURL = (useTor ? "" : mainPage) + "/search/?searchtext=" + barcode;
                Document doc = getDocument(lowerNetLayer, mainPage, itemURL);
                String href = null;
                if (doc != null) {
                    for(Element productCard : doc.getElementsByClass("products_card")) {
                        for(Element img : productCard.getElementsByClass("img")) {
                            for (Element url : img.getElementsByTag("a")) {
                                href = url.attr("href");
                                break;
                            }
                        }
                    }
                }

                findProperty("itemUrlEuroopt[]").change(href, context);

            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
