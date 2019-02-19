package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.Pair;
import lsfusion.base.RawFileData;
import lsfusion.erp.integration.DefaultExportXMLActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

abstract class EDIActionProperty extends DefaultExportXMLActionProperty {
    static Namespace soapenvNamespace = Namespace.getNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
    static Namespace topNamespace = Namespace.getNamespace("top", "http://topby.by/");
    static Namespace ns2Namespace = Namespace.getNamespace("ns2", "http://ws.services.eds.topby.by/");
    static Namespace wsNamespace = Namespace.getNamespace("ws", "http://ws.services.eds.topby.by/");

    String charset = "UTF-8";

    EDIActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    EDIActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected String generateXML(String login, String password, String documentNumber, String documentDate, String senderCode, String receiverCode, String deliveryPointCode, String documentXML, String type) {
        Element rootElement = new Element("Envelope", soapenvNamespace);
        rootElement.setNamespace(soapenvNamespace);
        rootElement.addNamespaceDeclaration(soapenvNamespace);
        rootElement.addNamespaceDeclaration(topNamespace);

        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        //parent: rootElement
        Element headerElement = new Element("Header", soapenvNamespace);
        rootElement.addContent(headerElement);

        //parent: rootElement
        Element bodyElement = new Element("Body", soapenvNamespace);
        rootElement.addContent(bodyElement);

        //parent: bodyElement
        Element sendDocumentElement = new Element("SendDocument", topNamespace);
        bodyElement.addContent(sendDocumentElement);

        addStringElement(topNamespace, sendDocumentElement, "username", login);
        addStringElement(topNamespace, sendDocumentElement, "password", password);
        addStringElement(topNamespace, sendDocumentElement, "filename", "invoice" + documentNumber);
        addStringElement(topNamespace, sendDocumentElement, "documentDate", documentDate);
        addStringElement(topNamespace, sendDocumentElement, "documentNumber", documentNumber);
        addStringElement(topNamespace, sendDocumentElement, "senderCode", senderCode);
        addStringElement(topNamespace, sendDocumentElement, "receiverCode", receiverCode);
        addStringElement(topNamespace, sendDocumentElement, "deliveryPointCode", deliveryPointCode);

        addStringElement(topNamespace, sendDocumentElement, "documentType", type);
        addStringElement(topNamespace, sendDocumentElement, "content", documentXML);

        return outputXMLString(doc, charset, null, null, false);
    }


    protected String signDocument(String documentType, String invoiceNumber, String hostEDSService, Integer portEDSService, String xml, String aliasEDSService, String passwordEDSService, String charset) throws IOException, JDOMException {
        if (xml != null) {
            if(aliasEDSService != null || passwordEDSService != null) {
                String urlEDSService = String.format("http://%s:%s/eds/services/EDSService?wsdl", hostEDSService, portEDSService);
                String responseMessage = getResponseMessage(sendRequest(hostEDSService, portEDSService, "eds", "eds", urlEDSService, createSignRequest(xml, aliasEDSService, passwordEDSService, charset), null, true));
                String error = null;
                SAXBuilder builder = new SAXBuilder();
                Document document = builder.build(IOUtils.toInputStream(responseMessage));
                Element rootNode = document.getRootElement();
                Namespace ns = rootNode.getNamespace();
                if (ns != null) {
                    Element body = rootNode.getChild("Body", ns);
                    if (body != null) {
                        Element faultElement = body.getChild("Fault", ns);
                        if (faultElement != null) {
                            error = faultElement.getChildText("faultstring");
                        } else {
                            Element response = body.getChild("GetEDSResponse", ns2Namespace);
                            if (response != null) {
                                String waybill = response.getChildText("waybill");
                                if (waybill != null) {
                                    responseMessage = waybill;
                                }
                            }

                        }
                    }
                }
                if (error != null) throw new RuntimeException(String.format("%s %s не подписан. Ошибка: %s", documentType, invoiceNumber, error));
                return responseMessage;
            } else {
                throw new RuntimeException("Alias or Password for EDSService not found");
            }
        } else
            return null;
    }

    private String createSignRequest(String xml, String alias, String password, String charset) {

        Element rootElement = new Element("Envelope", soapenvNamespace);
        rootElement.setNamespace(soapenvNamespace);
        rootElement.addNamespaceDeclaration(soapenvNamespace);
        rootElement.addNamespaceDeclaration(wsNamespace);
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        Element headerElement = new Element("Header", soapenvNamespace);
        rootElement.addContent(headerElement);

        Element bodyElement = new Element("Body", soapenvNamespace);

        Element getEDSElement = new Element("GetEDS", wsNamespace);
        addStringElement(getEDSElement, "waybill", String.format("<![CDATA[%s]]>", xml));

        Element keyInfoElement = new Element("keyInfo");
        addStringElement(keyInfoElement, "alias", alias);
        addStringElement(keyInfoElement, "password", password);
        getEDSElement.addContent(keyInfoElement);

        bodyElement.addContent(getEDSElement);

        rootElement.addContent(bodyElement);

        return outputXMLString(doc, charset, null, null, true);
    }

    protected boolean sendDocument(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider, String invoiceNumber, String documentXML,
                              DataObject eInvoiceObject, boolean showMessages, boolean isCancel, int step) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean result = false;
        HttpResponse httpResponse = sendRequest(host, port, login, password, url, documentXML, null);
        RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "SendDocument");
        switch (requestResult) {
            case OK:
                if (showMessages) {
                    ServerLoggers.importLogger.info(String.format("%s SendEInvoice %s request succeeded", provider, invoiceNumber));
                    switch (step) {
                        case 1:
                            findProperty("exportedSupplier[EInvoice]").change(true, context, eInvoiceObject);
                            break;
                        case 3:
                            findProperty("exportedCustomer[EInvoice]").change(true, context, eInvoiceObject);
                            break;
                        case 4:
                            findProperty("importedSupplier[EInvoice]").change(true, context, eInvoiceObject);
                            break;
                    }
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Накладная %s%s выгружена", provider, invoiceNumber, isCancel ? " (отмена)" : ""), "Экспорт"));
                    context.apply();
                }
                result = true;
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error(String.format("%s SendEInvoice %s: invalid login-password", provider, invoiceNumber));
                if (showMessages) {
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Накладная %s не выгружена: ошибка авторизации", provider, invoiceNumber), "Экспорт"));
                }
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error(String.format("%s SendEInvoice %s: unknown error", provider, invoiceNumber));
                if (showMessages) {
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Накладная %s не выгружена: неизвестная ошибка", provider, invoiceNumber), "Экспорт"));
                }
        }
        return result;
    }

    HttpResponse sendRequest(String host, Integer port, String login, String password, String url, String xml, Pair<String, RawFileData> file) throws IOException {
        return sendRequest(host, port, login, password, url, xml, file, false);
    }

    private HttpResponse sendRequest(String host, Integer port, String login, String password, String url, String xml, Pair<String, RawFileData> file, boolean preemptiveAuthentication) throws IOException {
        // Send post request
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getCredentialsProvider().setCredentials(new AuthScope(host, port),
                new UsernamePasswordCredentials(login, password));
        httpclient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 300000);

        HttpPost httpPost = new HttpPost(url);
        HttpEntity entity;
        if (file == null || file.second == null) {
            entity = new StringEntity(xml, StandardCharsets.UTF_8);
            httpPost.addHeader("Content-Type", "text/xml; charset=UTF-8");
        } else {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart(FormBodyPartBuilder.create("xml", new StringBody(xml, ContentType.create("application/xml", Charset.forName("UTF-8")))).build());
            FormBodyPart part = FormBodyPartBuilder.create(file.first, new ByteArrayBody(file.second.getBytes(), file.first)).build();
            part.addField("Content-Id", file.first);
            builder.addPart(part);
            entity = builder.build();
        }
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
        BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent()), "utf-8"));
        String output;
        while ((output = br.readLine()) != null) {
            result.append(output);
        }
        return result.toString();
    }

    RequestResult getRequestResult(HttpResponse httpResponse, String responseMessage, String prefix) throws IOException, JDOMException {
        return getRequestResult(httpResponse, responseMessage, null, prefix);
    }

    RequestResult getRequestResult(HttpResponse httpResponse, String responseMessage, String archiveDir, String prefix) throws IOException, JDOMException {
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
                            String successful = result.getChildText("Succesful", topNamespace);
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
        if(requestResult != RequestResult.OK && archiveDir != null) {
            try {
                FileUtils.writeStringToFile(new File(archiveDir + "/response" + System.currentTimeMillis() + ".xml"), responseMessage);
            } catch (Exception e) {
                ServerLoggers.importLogger.error("Archive file error: ", e);
            }
        }
        return requestResult;
    }

    protected String formatTimestamp(Timestamp date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
    }
}