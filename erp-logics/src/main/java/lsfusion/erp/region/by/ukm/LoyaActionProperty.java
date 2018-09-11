package lsfusion.erp.region.by.ukm;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;

public class LoyaActionProperty extends ScriptingActionProperty {

    public LoyaActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public LoyaActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String getURL(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String ip = (String) findProperty("ipLoya[]").read(context);
        String port = (String) findProperty("portLoya[]").read(context);
        return ip == null ? null : String.format("http://%s:%s/api/1.0/", ip, port == null ? "" : port);
    }

    protected HttpResponse executeRequest(HttpEntityEnclosingRequestBase request, JSONObject requestBody, String sessionKey) throws IOException {
        return executeRequest(request, requestBody.toString(), sessionKey);
    }

    protected HttpResponse executeRequest(HttpEntityEnclosingRequestBase request, String requestBody, String sessionKey) throws IOException {
        request.setEntity(new StringEntity(requestBody, "utf-8"));
        return executeRequest(request, sessionKey);
    }

    protected HttpResponse executeRequest(HttpRequestBase request, String sessionKey) throws IOException {
        request.addHeader("content-type", "application/json");
        request.addHeader("Cookie", "PLAY2AUTH_SESS_ID=" + sessionKey);
        return new DefaultHttpClient().execute(request);
    }

    protected String getCookieResponse(HttpResponse response, int statusCode) throws IOException {
        if (statusCode >= 200 && statusCode < 300) {
            Header[] headers = response.getHeaders("set-cookie");
            if (headers.length > 0) {
                String header = headers[0].getValue();
                if (header != null) {
                    String[] splittedHeader = header.split("=|;");
                    return splittedHeader.length > 1 ? splittedHeader[1] : null;
                }
            }
        }
        return null;
    }

    protected String getResponseMessage(HttpResponse response) throws IOException {
        StringBuilder result = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent()), "utf-8"));
        String output;
        while ((output = br.readLine()) != null) {
            result.append(output);
        }
        return result.toString();
    }

    protected boolean requestSucceeded(HttpResponse response) throws IOException {
        return response.getStatusLine().getStatusCode() == 200;
    }

    protected void setIdLoyaItemGroup(ExecutionContext context, DataObject itemGroupObject, Long id)
            throws IOException, JSONException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("id[LoyaItemGroup]").change(id, newContext, itemGroupObject);
            newContext.apply();
        }
    }

    protected void setIdLoyaBrand(ExecutionContext context, DataObject brandObject, Integer idLoyaBrand) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("idLoya[Brand]").change(idLoyaBrand, newContext, brandObject);
            newContext.apply();
        }
    }

    protected SettingsLoya login(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, JSONException, IOException {
        Integer partnerId = null;
        String sessionKey = null;
        String error = null;

        String url = getURL(context);
        if (url == null)
            error = "IP не задан";
        else {
            partnerId = (Integer) findProperty("idPartnerLoya[]").read(context);
            if (partnerId == null)
                error = "Собственный ID не задан";
            else {
                String email = (String) findProperty("emailLoya[]").read(context);
                String password = (String) findProperty("passwordLoya[]").read(context);
                String apiKey = (String) findProperty("apiKeyLoya[]").read(context);
                if(email != null && password != null && apiKey != null) {
                    String sha1Password = DigestUtils.shaHex(password);

                    HttpPost postRequest = new HttpPost(url + "login");
                    JSONObject keyArg = new JSONObject();
                    keyArg.put("email", email);
                    keyArg.put("password", sha1Password);
                    keyArg.put("apikey", apiKey);
                    StringEntity input = new StringEntity(keyArg.toString(), "utf-8");

                    postRequest.addHeader("content-type", "application/json");
                    postRequest.setEntity(input);

                    HttpResponse response = new DefaultHttpClient().execute(postRequest);

                    int statusCode = response.getStatusLine().getStatusCode();
                    sessionKey = getCookieResponse(response, statusCode);
                    if (sessionKey == null)
                        error = getResponseMessage(response);
                } else {
                    error = "Не задан email / пароль / api key";
                }
            }
        }
        return new SettingsLoya(url, partnerId, sessionKey, error);
    }
}
