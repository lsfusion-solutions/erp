package lsfusion.erp.region.by.ukm;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

import static org.apache.commons.lang.StringUtils.trimToNull;

public class DeleteBrandLoyaActionProperty extends LoyaActionProperty {
    private final ClassPropertyInterface brandInterface;

    public DeleteBrandLoyaActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        brandInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject brandObject = context.getDataKeyValue(brandInterface);

            String url = getURL(context);
            if(url != null) {
                String sessionKey = trimToNull((String) findProperty("sessionKeyLoya[]").read(context));
                if(sessionKey != null) {
                    Integer idBrand = (Integer) findProperty("idLoya[Brand]").read(context, brandObject);
                    if(idBrand != null) {
                        deleteBrand(context, url, sessionKey, idBrand, brandObject);
                    }
                } else {
                    context.delayUserInteraction(new MessageClientAction("Session ID не задан. Необходима авторизация", "Loya: Create Brand Error"));
                }
            } else {
                context.delayUserInteraction(new MessageClientAction("IP не задан", "Loya: Create Brand Error"));
            }
        } catch (Exception e) {
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), "Loya: Create Brand Error"));
        }
    }

    private void deleteBrand(ExecutionContext context, String url, String sessionKey, Integer idBrand, DataObject brandObject)
            throws IOException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        HttpDeleteWithBody deleteRequest = new HttpDeleteWithBody(url + "brand/" + idBrand);
        deleteRequest.setEntity(new StringEntity("[]"));
        HttpResponse response = executeRequest(deleteRequest, sessionKey);
        if (requestSucceeded(response)) {
            setIdLoyaBrand(context, brandObject, null);
        } else
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Delete Brand Error"));
    }
}
