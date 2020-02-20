package lsfusion.erp.region.by.integration.edi;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.file.DynamicFormatFileClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Iterator;

public class SendRequestEDIAction extends InternalAction {
    protected final ClassPropertyInterface hostInterface;
    protected final ClassPropertyInterface portInterface;
    protected final ClassPropertyInterface loginInterface;
    protected final ClassPropertyInterface passwordInterface;
    protected final ClassPropertyInterface urlInterface;
    protected final ClassPropertyInterface xmlFileInterface;
    protected final ClassPropertyInterface preemptiveAuthenticationInterface;


    public SendRequestEDIAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        hostInterface = i.next();
        portInterface = i.next();
        loginInterface = i.next();
        passwordInterface = i.next();
        urlInterface = i.next();
        xmlFileInterface = i.next();
        preemptiveAuthenticationInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String host = (String) context.getKeyValue(hostInterface).getValue();
            Integer port = (Integer) context.getKeyValue(portInterface).getValue();
            String login = (String) context.getKeyValue(loginInterface).getValue();
            String password = (String) context.getKeyValue(passwordInterface).getValue();
            String url = (String) context.getKeyValue(urlInterface).getValue();
            String xml = new String(((RawFileData) context.getKeyValue(xmlFileInterface).getValue()).getBytes(), StandardCharsets.UTF_8);
            boolean preemptiveAuthentication = context.getKeyValue(preemptiveAuthenticationInterface).getValue() != null;

            HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, preemptiveAuthentication);

            String responseMessage = getResponseMessage(httpResponse);
            DataObject fileObject = new DataObject(new FileData(new RawFileData(responseMessage.getBytes(StandardCharsets.UTF_8)), "xml"), DynamicFormatFileClass.get());
            findProperty("sendRequestEDIResponse[]").change(fileObject, context);
            findProperty("sendRequestEDIStatus[]").change(httpResponse.getStatusLine().getStatusCode(), context);
        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    protected HttpResponse sendRequest(String host, Integer port, String login, String password, String url, String xml, boolean preemptiveAuthentication) throws IOException {
        // Send post request
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getCredentialsProvider().setCredentials(new AuthScope(host, port),
                new UsernamePasswordCredentials(login, password));
        httpclient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 300000);

        HttpPost httpPost = new HttpPost(url);
        HttpEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        httpPost.addHeader("Content-Type", "text/xml; charset=UTF-8");

        httpPost.setEntity(entity);

        if(preemptiveAuthentication) {
            HttpHost targetHost = new HttpHost(host, port, "http");
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(login, password));

            AuthCache authCache = new BasicAuthCache();
            authCache.put(targetHost, new BasicScheme());

            // Add AuthCache to the execution context
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credsProvider);
            context.setAuthCache(authCache);

            return httpclient.execute(httpPost, context);
        } else
            return httpclient.execute(httpPost);
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

    @Override
    protected boolean allowNulls() {
        return true;
    }
}