package lsfusion.erp.integration;

import lsfusion.erp.ERPLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jdom2.*;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DefaultExportXMLAction extends DefaultExportAction {

    public DefaultExportXMLAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultExportXMLAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected void setAttribute(Element element, String id, Object value) {
        if (value != null)
            element.setAttribute(new Attribute(id, String.valueOf(value)));
    }

    protected void addBigDecimalElement(Namespace namespace, Element parent, String id, BigDecimal value) {
        if (value != null)
            parent.addContent(new Element(id, namespace).setText(String.valueOf(value)));
    }

    protected void addBooleanElement(Namespace namespace, Element parent, String id, boolean value) {
        parent.addContent(new Element(id, namespace).setText(String.valueOf(value)));
    }

    protected void addStringElement(Element parent, String id, String value) {
        if (value != null)
            parent.addContent(new Element(id).setText(value));
    }

    protected void addCDataElement(Element parent, String id, String value) {
        if (value != null)
            parent.addContent(new Element(id).addContent(new CDATA(value)));
    }

    protected void addIntegerElement(Element parent, String id, Integer value) {
        if (value != null)
            parent.addContent(new Element(id).setText(String.valueOf(value)));
    }

    protected void addIntegerElement(Namespace namespace, Element parent, String id, Integer value) {
        if (value != null)
            parent.addContent(new Element(id, namespace).setText(String.valueOf(value)));
    }

    protected void addStringElement(Namespace namespace, Element parent, String id, String value) {
        if (value != null)
            parent.addContent(new Element(id, namespace).setText(value));
    }

    protected void outputXml(Document doc, Writer outputStreamWriter, String encoding) throws IOException {
        XMLOutputter xmlOutput = new XMLOutputter();
        xmlOutput.setFormat(Format.getPrettyFormat().setEncoding(encoding));
        PrintWriter fw = new PrintWriter(outputStreamWriter);
        xmlOutput.output(doc, fw);
        fw.close();
    }

    protected String outputXML(Document doc, String encoding, String outputDir) {
        String xml = new XMLOutputter(Format.getPrettyFormat().setEncoding(encoding)).outputString(doc);
        if (outputDir != null) {
            try {
                FileUtils.writeStringToFile(new File(outputDir + "/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")) + ".xml"), xml);
            } catch (Exception e) {
                ERPLoggers.importLogger.error("Export Error: ", e);
            }
        }
        return xml;
    }

    protected String sendRequest(String url, String xml) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(new StringEntity(xml, ContentType.create("application/xml")));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }
}