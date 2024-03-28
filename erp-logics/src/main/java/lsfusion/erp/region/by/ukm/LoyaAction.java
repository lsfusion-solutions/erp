package lsfusion.erp.region.by.ukm;

import lsfusion.base.file.IOUtils;
import lsfusion.erp.ERPLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

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

    protected String getURL(ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String ip = (String) findProperty("ipLoya[]").read(context);
        String port = (String) findProperty("portLoya[]").read(context);
        return ip == null ? null : String.format("http://%s:%s/api/1.0/", ip, port == null ? "" : port);
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext<ClassPropertyInterface> context, HttpUriRequestBase request, JSONObject requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        return executeRequestWithRelogin(context, request, requestBody.toString());
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext<ClassPropertyInterface> context, HttpUriRequestBase request, String requestBody) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        request.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
        return executeRequestWithRelogin(context, request);
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext<ClassPropertyInterface> context, HttpUriRequestBase request) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        return executeRequestWithRelogin(context, request, 2);
    }

    protected LoyaResponse executeRequestWithRelogin(ExecutionContext<ClassPropertyInterface> context, HttpUriRequestBase request, int count) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, JSONException {
        assert settings != null;
        CloseableHttpResponse response = executeRequest(request, settings.sessionKey);
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

    protected CloseableHttpResponse executeRequest(HttpUriRequestBase request, String sessionKey) throws IOException {
        request.setHeader("content-type", "application/json");
        request.setHeader("Cookie", "PLAY2AUTH_SESS_ID=" + sessionKey);
        try(PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager()) {
            connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(5, TimeUnit.MINUTES).build());
            RequestConfig.Builder configBuilder = RequestConfig.custom().setConnectionRequestTimeout(5, TimeUnit.MINUTES).setResponseTimeout(5, TimeUnit.MINUTES);
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().setConnectionManager(connectionManager).setDefaultRequestConfig(configBuilder.build()).build()) {
                return httpClient.execute(request, classicHttpResponse -> null);
            }
        }
    }

    protected String getCookieResponse(CloseableHttpResponse response, int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            Header[] headers = response.getHeaders("set-cookie");
            if (headers.length > 0) {
                String header = headers[0].getValue();
                if (header != null) {
                    String[] splittedHeader = header.split("[=;]");
                    return splittedHeader.length > 1 ? splittedHeader[1] : null;
                }
            }
        }
        return null;
    }

    protected String getResponseMessage(CloseableHttpResponse response) throws IOException {
        return IOUtils.readStreamToString(response.getEntity().getContent(), "UTF-8");
    }

    protected boolean requestSucceeded(CloseableHttpResponse response) {
        return response.getCode() == 200;
    }

    protected SettingsLoya login(ExecutionContext<ClassPropertyInterface> context, boolean relogin) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, JSONException, IOException {
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
                StringEntity input = new StringEntity(keyArg.toString(), StandardCharsets.UTF_8);

                postRequest.addHeader("content-type", "application/json");
                postRequest.setEntity(input);

                ERPLoggers.importLogger.info("Loya login request: " + IOUtils.readStreamToString(postRequest.getEntity().getContent(), "UTF-8"));

                try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    CloseableHttpResponse response = httpClient.execute(postRequest, classicHttpResponse -> null);
                    int statusCode = response.getCode();
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
                }
            } else {
                error = "Не задан email / пароль / api key";
            }
        }
        return new SettingsLoya(url, partnerId, sessionKey, logRequests, error);
    }

    protected boolean authenticationFailed(String response) {
        return response != null && (response.startsWith("Authentication failed") || response.contains("Authentication required"));
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
