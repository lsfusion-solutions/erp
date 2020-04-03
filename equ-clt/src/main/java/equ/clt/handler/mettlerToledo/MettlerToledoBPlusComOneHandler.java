package equ.clt.handler.mettlerToledo;

import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MettlerToledoBPlusComOneHandler extends MultithreadScalesHandler {

    protected String getLogPrefix() {
        return "Mettler Toledo BPlus ComOne: ";
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new BPlusSendTransactionTask(transaction, scales);
    }

    protected String clearPLU(TCPSocket socket) throws IOException, JDOMException {
        String messageId = Instant.now().toString();

        Element rootElement = new Element("Message");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        Element artsCommonHeaderElement = new Element("ARTSCommonHeader");
        artsCommonHeaderElement.setAttribute("MessageType", "Request");
        addStringElement(artsCommonHeaderElement, "MessageID", messageId);
        rootElement.addContent(artsCommonHeaderElement);

        Element itemTransactionElement = new Element("ItemTransaction");
        itemTransactionElement.setAttribute("ActionCode", "DeleteAll");
        rootElement.addContent(itemTransactionElement);

        Element itemElement = new Element("Item");
        itemTransactionElement.addContent(itemElement);

        return sendRequest(socket, new XMLOutputter(Format.getPrettyFormat().setEncoding("UTF-8")).outputString(doc), messageId);
    }

    protected String sendPLU(TCPSocket socket, TransactionScalesInfo transaction) throws IOException, JDOMException {
        String messageId = Instant.now().toString();

        Element rootElement = new Element("Message");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        Element artsCommonHeaderElement = new Element("ARTSCommonHeader");
        artsCommonHeaderElement.setAttribute("MessageType", "Request");
        addStringElement(artsCommonHeaderElement, "MessageID", messageId);
        rootElement.addContent(artsCommonHeaderElement);

        Element itemTransactionElement = new Element("ItemTransaction");
        itemTransactionElement.setAttribute("ActionCode", "Write");
        rootElement.addContent(itemTransactionElement);

        for (ScalesItemInfo item : transaction.itemsList) {

            Element itemElement = new Element("Item");
            addStringElement(itemElement, "PLU", item.pluNumber != null ? String.valueOf(item.pluNumber) : item.idBarcode);
            itemTransactionElement.addContent(itemElement);

            Element descriptionsElement = new Element("Descriptions");
            descriptionsElement.setAttribute("Action", "Create");
            itemElement.addContent(descriptionsElement);

            Element itemNameElement = new Element("Description");
            itemNameElement.setAttribute("Type", "ItemName");
            itemNameElement.setText(item.name);
            descriptionsElement.addContent(itemNameElement);

            Element itemShortNameElement = new Element("Description");
            itemShortNameElement.setAttribute("Type", "ItemShortName");
            itemShortNameElement.setText(item.name);
            descriptionsElement.addContent(itemShortNameElement);

            Element extraTextElement = new Element("Description");
            extraTextElement.setAttribute("Type", "ExtraText");
            extraTextElement.setText(item.description);
            descriptionsElement.addContent(extraTextElement);

            Element alternativeItemIDsElement = new Element("AlternativeItemIDs");
            addStringElement(alternativeItemIDsElement, "AlternativeItemID", item.idBarcode);
            alternativeItemIDsElement.setAttribute("Action", "Create");
            itemElement.addContent(alternativeItemIDsElement);

            Element itemPricesElement = new Element("ItemPrices");
            itemPricesElement.setAttribute("Action", "Create");
            itemElement.addContent(itemPricesElement);

            Element itemPriceElement = new Element("ItemPrice");
            itemPriceElement.setAttribute("UnitOfMeasureCode", isWeight(item, 0) ? "KGM" : "PCS");
            itemPriceElement.setText(String.valueOf(item.price));
            itemPricesElement.addContent(itemPriceElement);

            Element datesElement = new Element("Dates");
            datesElement.setAttribute("Action", "Create");
            itemElement.addContent(datesElement);

            if(item.daysExpiry != null) {
                Element dateOffsetBestBeforeElement = new Element("DateOffset");
                dateOffsetBestBeforeElement.setAttribute("Type", "BestBefore");
                dateOffsetBestBeforeElement.setAttribute("UnitOfOffset", "day");
                dateOffsetBestBeforeElement.setAttribute("IsPrintEnabled", "true");
                dateOffsetBestBeforeElement.setText(String.valueOf(item.daysExpiry));
                datesElement.addContent(dateOffsetBestBeforeElement);
            }

            Element labelFormatsElement = new Element("LabelFormats");
            itemElement.addContent(labelFormatsElement);

            Element labelFormatElement = new Element("LabelFormatID");
            labelFormatElement.setAttribute("Index", "0");
            labelFormatElement.setText("1");
            labelFormatsElement.addContent(labelFormatElement);

            Element barcodesElement = new Element("Barcodes");
            addStringElement(barcodesElement, "BarcodeID", item.idBarcode);
            itemElement.addContent(barcodesElement);
        }

        String xml = new XMLOutputter(Format.getPrettyFormat().setEncoding("UTF-8")).outputString(doc);

        return sendRequest(socket, xml, messageId);
    }

    private void addStringElement(Element parent, String id, String value) {
        if (value != null) parent.addContent(new Element(id).setText(value));
    }

    protected String sendRequest(TCPSocket socket, String request, String messageId) throws IOException, JDOMException {
        try {
            socket.open();
            socket.write(request);
            return receiveResponse(socket, messageId);
        } finally {
            socket.close();
        }
    }

    private String receiveResponse(TCPSocket socket, String messageId) throws IOException, JDOMException {
        String error = "Unknown Error";
        String response = socket.read(60);

        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new StringReader(response));
        Element rootNode = document.getRootElement();

        Element artsCommonHeaderElement = rootNode.getChild("ARTSCommonHeader");
        if (artsCommonHeaderElement != null) {

            Element responseElement = artsCommonHeaderElement.getChild("Response");
            if (responseElement != null) {
                Element requestIdElement = responseElement.getChild("RequestID");
                if (requestIdElement != null) {
                    if (requestIdElement.getText().equals(messageId)) {
                        String responseCode = responseElement.getAttributeValue("ResponseCode");
                        if (responseCode != null) {
                            if (responseCode.equals("OK")) {
                                error = null;
                            } else {
                                Element businessErrorElement = responseElement.getChild("BusinessError");
                                if (businessErrorElement != null) {
                                    Element codeElement = businessErrorElement.getChild("Code");
                                    if (codeElement != null) {
                                        error = getErrorDescription(codeElement.getText());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return error;
    }

    private String getErrorDescription(String code) {
        switch (code) {
            case "1001":
                return "Error parsing Message Header";
            case "1002":
                return "Error parsing  ARTSCommon Header";
            case "1003":
                return "Error parsing Message Type value";
            case "1004":
                return "Invalid Message type value";
            case "1005":
                return "Service for the transaction not available";
            case "1006":
                return "Error parsing Action code attribute";
            case "1007":
                return "Error parsing Action code value";
            case "1008":
                return "Invalid Action code value";
            case "1009":
                return "Error parsing Type(Object) in Transaction";
            case "1010":
                return "Invalid Type(Object) in Transaction";
            case "1011":
                return "Error parsing DeviceTime tag(CPlatformServer)";
            case "1012":
                return "Error parsing Firmware file(CPlatformServer";
            case "1013":
                return "Error parsing file(CPlatformServer)";
            case "1014":
                return "Error reading Config value(CConfigurationServer)";
            case "1015":
                return "Error writing Config value(CConfigurationServer)";
            case "1016":
                return "Error de-serializing object";
            case "1017":
                return "Invalid Access code value";
            case "1018":
                return "Invalid object type or range in readRange deleteRange";
            case "1019":
                return "No Ticket type in TransactionLog Transaction";
            case "1020":
                return "No  DeviceMap type in Network Exploration";
            case "1021":
                return "No  VendorContext type in FloatingVendor Transaction";
            case "1022":
                return "Network Timeout";
            case "1023":
                return " Error in accessing XML file";
            default:
                return "Error" + code;
        }
    }

    class BPlusSendTransactionTask extends SendTransactionTask {

        public BPlusSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
        }

        @Override
        protected Pair<List<String>, Boolean> run() {
            String error = null;

            TCPSocket socket = new TCPSocket(scales.port, 3001);

            try {

                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear) {
                    error = clearPLU(socket);
                }
                if (error != null) {
                    processTransactionLogger.error(getLogPrefix() + "Failed to clear PLU, " + error);
                } else {
                    processTransactionLogger.info(getLogPrefix() + "Sending " + transaction.itemsList.size() + " items..." + scales.port);
                    error = sendPLU(socket, transaction);
                    if (error != null) {
                        processTransactionLogger.error(getLogPrefix() + "Failed to load PLU, " + error);
                    }
                }
            } catch (Throwable t) {
                error = String.format(getLogPrefix() + "IP %s error, transaction %s error: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                processTransactionLogger.error(error, t);
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return Pair.create(error != null ? Collections.singletonList(error) : new ArrayList<>(), transaction.snapshot && error == null);
        }
    }
}