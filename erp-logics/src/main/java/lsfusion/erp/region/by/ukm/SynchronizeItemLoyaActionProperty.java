package lsfusion.erp.region.by.ukm;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.StringUtils;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.trim;

public class SynchronizeItemLoyaActionProperty extends SynchronizeLoyaActionProperty {
    private final ClassPropertyInterface itemInterface;

    public SynchronizeItemLoyaActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        itemInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            boolean logRequests = findProperty("logRequestsLoya[]").read(context) != null;
            boolean useBarcodeAsId = findProperty("useBarcodeAsIdLoya[]").read(context) != null;

            DataObject itemObject = context.getDataKeyValue(itemInterface);

            SettingsLoya settings = login(context);
            if (settings.error == null) {

                Map<String, Integer> discountLimits = getDiscountLimits(context);

                String idSku = (String) findProperty("id[Item]").read(context, itemObject);
                String barcode = (String) findProperty("idBarcode[Item]").read(context, itemObject);
                String id = useBarcodeAsId ? barcode : idSku;
                String caption = StringUtils.trimToEmpty((String) findProperty("caption[Item]").read(context, itemObject));
                String idUOM = (String) findProperty("idUOM[Item]").read(context, itemObject);
                boolean split = findProperty("split[Item]").read(context, itemObject) != null;
                String idSkuGroup = trim((String) findProperty("idSkuGroup[Item]").read(context, itemObject));
                Integer idLoyaBrand = (Integer) findProperty("idLoyaBrand[Item]").read(context, itemObject);
                Item item = new Item(id, caption, idUOM, split, idSkuGroup, idLoyaBrand);
                uploadItem(context, settings, item, discountLimits, logRequests);

            } else context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ServerLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }
}