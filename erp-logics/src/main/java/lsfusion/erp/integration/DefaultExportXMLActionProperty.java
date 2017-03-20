package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jdom.*;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;

public class DefaultExportXMLActionProperty extends DefaultExportActionProperty {

    public DefaultExportXMLActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultExportXMLActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected void outputXml(Document doc, Writer outputStreamWriter, String encoding) throws IOException {
        XMLOutputter xmlOutput = new XMLOutputter();
        xmlOutput.setFormat(Format.getPrettyFormat().setEncoding(encoding));
        PrintWriter fw = new PrintWriter(outputStreamWriter);
        xmlOutput.output(doc, fw);
        fw.close();
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
}