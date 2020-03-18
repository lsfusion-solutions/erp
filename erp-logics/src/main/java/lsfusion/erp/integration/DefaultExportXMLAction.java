package lsfusion.erp.integration;

import lsfusion.erp.ERPLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.language.ScriptingLogicsModule;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.FileUtils;
import org.jdom.*;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
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

    protected void setAttribute(Element element, String id, Object value, Namespace namespace) {
        if (value != null)
            element.setAttribute(id, String.valueOf(value), namespace);
    }

    protected void addBigDecimalElement(Element parent, String id, BigDecimal value) {
        if (value != null)
            parent.addContent(new Element(id).setText(String.valueOf(value)));
    }

    protected void addBigDecimalElement(Namespace namespace, Element parent, String id, BigDecimal value) {
        if (value != null)
            parent.addContent(new Element(id, namespace).setText(String.valueOf(value)));
    }

    protected void addBooleanElement(Element parent, String id, boolean value) {
        parent.addContent(new Element(id).setText(String.valueOf(value)));
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

    //новый метод, который заменит все остальные
    protected String outputXMLString(Document doc, String encoding, String outputDir, String prefix, boolean escapeEntities) {
        XMLOutputter xmlOutputter = escapeEntities ? new XMLOutputter(Format.getPrettyFormat().setEncoding(encoding)) {
            @Override
            public String escapeElementEntities(String str) {
                return str;
            }
        } : new XMLOutputter(Format.getPrettyFormat().setEncoding(encoding));
        String xml = xmlOutputter.outputString(doc);
        if (outputDir != null) {
            try {
                FileUtils.writeStringToFile(new File(outputDir + "/" + (prefix != null ? prefix : "") +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")) + ".xml"), xml);
            } catch (Exception e) {
                ERPLoggers.importLogger.error("Export Error: ", e);
            }
        }
        return xml;
    }

    protected String sendRequest(String url, String xml) throws IOException {
        Request request = new Request.Builder().url(url).post(FormBody.create(MediaType.parse("application/xml"), xml)).build();
        return new OkHttpClient().newCall(request).execute().body().string();
    }
}