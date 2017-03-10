package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.Pair;
import lsfusion.erp.integration.DefaultExportXMLActionProperty;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

abstract class EDIActionProperty extends DefaultExportXMLActionProperty {
    static Namespace soapenvNamespace = Namespace.getNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
    static Namespace topNamespace = Namespace.getNamespace("top", "http://topby.by/");

    EDIActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    EDIActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    HttpResponse sendRequest(String host, Integer port, String login, String password, String url, String xml, Pair<String, byte[]> file) throws IOException {
        return sendRequest(host, port, login, password, url, xml, null, file);
    }

    private HttpResponse sendRequest(String host, Integer port, String login, String password, String url, String xml, String soapAction, Pair<String, byte[]> file) throws IOException {
        // Send post request
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getCredentialsProvider().setCredentials(new AuthScope(host, port),
                new UsernamePasswordCredentials(login, password));

        HttpPost httpPost = new HttpPost(url);
        HttpEntity entity;
        if (file == null || file.second == null) {
            entity = new StringEntity(xml, StandardCharsets.UTF_8);
            httpPost.addHeader("Content-type", "text/xml");
            if(soapAction != null)
                httpPost.addHeader("SOAPAction", soapAction);
        } else {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart(FormBodyPartBuilder.create("xml", new StringBody(xml, ContentType.create("application/xml", Charset.forName("UTF-8")))).build());
            FormBodyPart part = FormBodyPartBuilder.create(file.first, new ByteArrayBody(file.second, file.first)).build();
            part.addField("Content-Id", file.first);
            builder.addPart(part);
            entity = builder.build();
        }
        httpPost.setEntity(entity);

        return httpclient.execute(httpPost);
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

    RequestResult getRequestResult(HttpResponse httpResponse, String responseMessage, String prefix) throws IOException, JDOMException {
        RequestResult requestResult = RequestResult.OK;
        int status = httpResponse.getStatusLine().getStatusCode();
        if (status == 200) {
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(new ByteArrayInputStream(responseMessage.getBytes("utf-8")));
            Element rootNode = document.getRootElement();
            Namespace ns = rootNode.getNamespace();
            if (ns != null) {
                Element body = rootNode.getChild("Body", ns);
                if (body != null) {
                    Element response = body.getChild(prefix + "Response", topNamespace);
                    if (response != null) {
                        Element result = response.getChild(prefix + "Result", topNamespace);
                        if (result != null) {
                            String successful = response.getChildText("succesful");
                            if (successful != null && !Boolean.parseBoolean(successful)) {
                                String message = result.getChildText("Message", topNamespace);
                                String errorCode = result.getChildText("ErrorCode", topNamespace);
                                if (errorCode != null) {
                                    switch (errorCode) {
                                        case "1300":
                                            requestResult = RequestResult.AUTHORISATION_ERROR;
                                            break;
                                        default:
                                            requestResult = RequestResult.UNKNOWN_ERROR;
                                    }
                                } else
                                    requestResult = RequestResult.UNKNOWN_ERROR;
                                ServerLoggers.importLogger.error("RequestResult: " + message);
                            }
                        }
                    }
                }
            }
        } else {
            requestResult = RequestResult.UNKNOWN_ERROR;
            ServerLoggers.importLogger.error("RequestResult: " + httpResponse.getStatusLine());
        }
        return requestResult;
    }

    protected String formatDate(Timestamp date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
    }
}