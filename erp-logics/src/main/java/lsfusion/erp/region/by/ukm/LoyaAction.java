package lsfusion.erp.region.by.ukm;

import lsfusion.erp.ERPLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public class LoyaAction extends InternalAction {

    protected SettingsLoya settings = null;

    public LoyaAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public LoyaAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String getURL(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String ip = (String) findProperty("ipLoya[]").read(context);
        String port = (String) findProperty("portLoya[]").read(context);
        return ip == null ? null : String.format("http://%s:%s/api/1.0/", ip, port == null ? "" : port);
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext context, HttpEntityEnclosingRequestBase request, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        return executeRequestWithRelogin(context, request, requestBody.toString());
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext context, HttpEntityEnclosingRequestBase request, String requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        request.setEntity(new StringEntity(requestBody, "utf-8"));
        return executeRequestWithRelogin(context, request);
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext context, HttpRequestBase request) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        return executeRequestWithRelogin(context, request, 2);
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext context, HttpRequestBase request, int count) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        assert settings != null;
        HttpResponse response = executeRequest(request, settings.sessionKey);
        String responseMessage = getResponseMessage(response);
        if(authenticationFailed(responseMessage)) {
            settings = login(context, true);
            if(count > 0) {
                return executeRequestWithRelogin(context, request, count - 1);
            } else {
                if (settings.error == null) {
                    response = executeRequest(request, settings.sessionKey);
                    responseMessage = getResponseMessage(response);
                }
            }
        }
        return new LoyaResponse(responseMessage, requestSucceeded(response));
    }

    protected HttpResponse executeRequest(HttpRequestBase request, String sessionKey) throws IOException {
        request.setHeader("content-type", "application/json");
        request.setHeader("Cookie", "PLAY2AUTH_SESS_ID=" + sessionKey);
        final HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, 5*60000);
        return new DefaultHttpClient(httpParams).execute(request);
    }

    protected String getCookieResponse(HttpResponse response, int statusCode) {
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
        BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent()), StandardCharsets.UTF_8));
        String output;
        while ((output = br.readLine()) != null) {
            result.append(output);
        }
        return result.toString();
    }

    protected boolean requestSucceeded(HttpResponse response) {
        return response.getStatusLine().getStatusCode() == 200;
    }

    protected SettingsLoya login(ExecutionContext context, boolean relogin) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, JSONException, IOException {
        String error = null;
        String url = getURL(context);
        if (url == null) {
            error = "IP не задан";
        }
        Integer partnerId = (Integer) findProperty("idPartnerLoya[]").read(context);
        if (partnerId == null) {
            error = "Собственный ID не задан";
        }

        boolean logRequests = findProperty("logRequestsLoya[]").read(context) != null;
        String sessionKey = relogin ? null : (String) findProperty("sessionKey[]").read(context);
        if (sessionKey == null) {

            String email = (String) findProperty("emailLoya[]").read(context);
            String password = (String) findProperty("passwordLoya[]").read(context);
            String apiKey = (String) findProperty("apiKeyLoya[]").read(context);
            if (email != null && password != null && apiKey != null) {
                String sha1Password = DigestUtils.shaHex(password);

                HttpPost postRequest = new HttpPost(url + "login");
                JSONObject keyArg = new JSONObject();
                keyArg.put("email", email);
                keyArg.put("password", sha1Password);
                keyArg.put("apikey", apiKey);
                StringEntity input = new StringEntity(keyArg.toString(), "utf-8");

                postRequest.addHeader("content-type", "application/json");
                postRequest.setEntity(input);

                ERPLoggers.importLogger.info("Loya login request: " + IOUtils.toString(postRequest.getEntity().getContent()));

                HttpResponse response = new DefaultHttpClient().execute(postRequest);

                int statusCode = response.getStatusLine().getStatusCode();
                sessionKey = getCookieResponse(response, statusCode);
                if (sessionKey == null) {
                    error = getResponseMessage(response);
                } else {
                    try (ExecutionContext.NewSession newContext = context.newSession()) {
                        findProperty("sessionKey[]").change(sessionKey, newContext);
                        ERPLoggers.importLogger.info("Loya: new SessionKey = " + sessionKey);
                        newContext.apply();
                    }
                }
            } else {
                error = "Не задан email / пароль / api key";
            }
        }
        return new SettingsLoya(url, partnerId, sessionKey, logRequests, error);
    }

    protected boolean authenticationFailed(String response) {
        return response != null && response.startsWith("Authentication failed");
    }

    //as in ukm4mysqlhandler
    protected Long parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null ? 0 : Long.parseLong(idItemGroup.equals("Все") ? "0" : idItemGroup.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return (long) 0;
        }
    }

    protected class LoyaResponse {
        String message;
        boolean succeeded;

        public LoyaResponse(String message, boolean succeeded) {
            this.message = message;
            this.succeeded = succeeded;
        }
    }
}
