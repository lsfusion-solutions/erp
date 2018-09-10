package lsfusion.erp.region.by.ukm;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

import static org.apache.commons.lang.StringUtils.trimToNull;

public class UploadBrandLoyaActionProperty extends LoyaActionProperty {
    private final ClassPropertyInterface brandInterface;

    public UploadBrandLoyaActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
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
                    String nameBrand = trimToNull((String) findProperty("name[Brand]").read(context, brandObject));

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("id", idBrand);
                    requestBody.put("name", nameBrand);
                    requestBody.put("description", nameBrand);

                    if(existsBrand(url, sessionKey, idBrand)) {
                        modifyBrand(context, url, sessionKey, idBrand, requestBody);
                    } else {
                        createBrand(context, brandObject, url, sessionKey, requestBody);
                    }
                } else {
                    context.delayUserInteraction(new MessageClientAction("Session ID не задан. Необходима авторизация", "Loya: Create Brand Error"));
                }
            } else {
                context.delayUserInteraction(new MessageClientAction("IP не задан", "Loya: Create Brand Error"));
            }
        } catch (Exception e) {
            ServerLoggers.systemLogger.error("Loya: Create Brand Error", e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), "Loya: Create Brand Error"));
        }
    }

    private boolean existsBrand(String url, String sessionKey, Integer idBrand) throws IOException {
        HttpGet getRequest = new HttpGet(url + "brand/" + idBrand);
        return requestSucceeded(executeRequest(getRequest, sessionKey));
    }

    private void modifyBrand(ExecutionContext context, String url, String sessionKey, Integer idBrand, JSONObject requestBody) throws IOException {
        HttpPost postRequest = new HttpPost(url + "brand/" + idBrand);
        HttpResponse response = executeRequest(postRequest, requestBody, sessionKey);
        if (requestSucceeded(response))
            context.delayUserInteraction(new MessageClientAction("Loya: Brand successfully modified", "Loya"));
        else
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Modify Brand Error"));
    }

    private void createBrand(ExecutionContext context, DataObject brandObject, String url, String sessionKey, JSONObject requestBody)
            throws IOException, JSONException, SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        HttpPut putRequest = new HttpPut(url + "brand");
        HttpResponse response = executeRequest(putRequest, requestBody, sessionKey);
        if (requestSucceeded(response)) {
            JSONObject responseObject = new JSONObject(getResponseMessage(response));
            setIdLoyaBrand(context, brandObject, responseObject.getInt("id"));
            context.delayUserInteraction(new MessageClientAction("Loya: Brand successfully created", "Loya"));
        } else {
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Create Brand Error"));
        }
    }

}
