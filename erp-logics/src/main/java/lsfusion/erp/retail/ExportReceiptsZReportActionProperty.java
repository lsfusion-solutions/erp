package lsfusion.erp.retail;

import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExportReceiptsZReportActionProperty extends ScriptingActionProperty {

    public ExportReceiptsZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM, new ValueClass[]{});
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {
    }

    public void export(ExecutionContext<ClassPropertyInterface> context, DataObject zReportObject, String filePath, boolean customPath) {

        if (filePath == null && !customPath)
            return;

        try {

            List<DataObject> receiptObjectsList = new ArrayList<DataObject>();

            DataSession session = context.getSession();
            Map<String, byte[]> files = new HashMap<String, byte[]>();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("zReport");
            doc.appendChild(rootElement);

            String numberZReport = (String) LM.findLCPByCompoundName("numberZReport").read(session, zReportObject);

            KeyExpr receiptExpr = new KeyExpr("receipt");
            ImRevMap<Object, KeyExpr> receiptKeys = MapFact.singletonRev((Object) "receipt", receiptExpr);

            String[] receiptProperties = new String[]{"dateTimeReceipt", "discountSumReceipt",
                    "numberDiscountCardReceipt"};
            QueryBuilder<Object, Object> receiptQuery = new QueryBuilder<Object, Object>(receiptKeys);
            for (String rProperty : receiptProperties) {
                receiptQuery.addProperty(rProperty, getLCP(rProperty).getExpr(session.getModifier(), receiptExpr));
            }
            receiptQuery.and(getLCP("zReportReceipt").getExpr(session.getModifier(), receiptQuery.getMapExprs().get("receipt")).compare(zReportObject.getExpr(), Compare.EQUALS));
            receiptQuery.and(getLCP("exportReceipt").getExpr(session.getModifier(), receiptQuery.getMapExprs().get("receipt")).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptResult = receiptQuery.execute(session.sql);

            if (receiptResult.size() == 0)
                return;
            for (int i = 0, size = receiptResult.size(); i < size; i++) {
                DataObject receiptObject = new DataObject(receiptResult.getKey(i).get("receipt"), (ConcreteClass) LM.findClassByCompoundName("Receipt"));
                LM.findLCPByCompoundName("exportReceipt").change((Object) null, session.getSession(), receiptObject);

                Element receipt = doc.createElement("receipt");
                rootElement.appendChild(receipt);

                Map<Integer, Object[]> paymentConditionMap = new HashMap<Integer, Object[]>();

                KeyExpr preReceiptDetailExpr = new KeyExpr("receiptDetail");
                KeyExpr prePromotionConditionExpr = new KeyExpr("promotionCondition");
                ImRevMap<Object, KeyExpr> preReceiptDetailKeys = MapFact.toRevMap((Object) "receiptDetail", preReceiptDetailExpr, "promotionCondition", prePromotionConditionExpr);

                String[] receiptDetailPromotionConditionProperties = new String[]{"quantityReceiptSaleDetailPromotionCondition", "promotionSumReceiptSaleDetailPromotionCondition", "setUserPromotionReceiptSaleDetailPromotionCondition"};
                QueryBuilder<Object, Object> preReceiptDetailQuery = new QueryBuilder<Object, Object>(preReceiptDetailKeys);
                for (String pcProperty : receiptDetailPromotionConditionProperties) {
                    preReceiptDetailQuery.addProperty(pcProperty, getLCP(pcProperty).getExpr(session.getModifier(), preReceiptDetailExpr, prePromotionConditionExpr));
                }
                preReceiptDetailQuery.addProperty("idPromotionCondition", getLCP("idPromotionCondition").getExpr(session.getModifier(), prePromotionConditionExpr));
                preReceiptDetailQuery.and(getLCP("receiptReceiptDetail").getExpr(session.getModifier(), preReceiptDetailExpr).compare(receiptObject.getExpr(), Compare.EQUALS));
                preReceiptDetailQuery.and(getLCP("quantityReceiptSaleDetailPromotionCondition").getExpr(preReceiptDetailExpr, prePromotionConditionExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> preReceiptDetailResult = preReceiptDetailQuery.execute(session.getSession().sql);

                for (int j = 0, sizeResult = preReceiptDetailResult.size(); j < sizeResult; j++) {
                    Integer idReceiptDetail = (Integer) preReceiptDetailResult.getKey(j).valueIt().iterator().next();
                    String idPromotionCondition = (String) preReceiptDetailResult.getValue(j).get("idPromotionCondition");
                    BigDecimal quantityReceiptSaleDetailPromotionCondition = (BigDecimal) preReceiptDetailResult.getValue(j).get("quantityReceiptSaleDetailPromotionCondition");
                    BigDecimal promotionSumReceiptSaleDetailPromotionCondition = (BigDecimal) preReceiptDetailResult.getValue(j).get("promotionSumReceiptSaleDetailPromotionCondition");
                    paymentConditionMap.put(idReceiptDetail, new Object[]{idPromotionCondition, quantityReceiptSaleDetailPromotionCondition, promotionSumReceiptSaleDetailPromotionCondition});
                }


                KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
                ImRevMap<Object, KeyExpr> receiptDetailKeys = MapFact.singletonRev((Object) "receiptDetail", receiptDetailExpr);

                String[] receiptDetailProperties = new String[]{"typeReceiptDetail", "quantityReceiptSaleDetail",
                        "quantityReceiptReturnDetail", "priceReceiptDetail", "idBarcodeReceiptDetail", "sumReceiptDetail",
                        "discountSumReceiptDetail", "discountPercentReceiptSaleDetail"};
                QueryBuilder<Object, Object> receiptDetailQuery = new QueryBuilder<Object, Object>(receiptDetailKeys);
                for (String rdProperty : receiptDetailProperties) {
                    receiptDetailQuery.addProperty(rdProperty, getLCP(rdProperty).getExpr(session.getModifier(), receiptDetailExpr));
                }
                receiptDetailQuery.and(getLCP("receiptReceiptDetail").getExpr(session.getModifier(), receiptDetailExpr).compare(receiptObject.getExpr(), Compare.EQUALS));
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptDetailResult = receiptDetailQuery.execute(session.getSession().sql);

                int numberReceiptDetail = 1;
                for (int j = 0, sizeResult = receiptDetailResult.size(); j < sizeResult; j++) {
                    Integer idReceiptDetail = (Integer) receiptDetailResult.getKey(j).valueIt().iterator().next();
                    Element receiptDetail = doc.createElement("receiptDetail");
                    rootElement.appendChild(receiptDetail);

                    String[] fields = new String[]{"typeReceiptDetail", "priceReceiptDetail", "quantityReceiptSaleDetail",
                            "quantityReceiptReturnDetail", "idBarcodeReceiptDetail", "sumReceiptDetail",
                            "discountSumReceiptDetail", "discountPercentReceiptSaleDetail", "numberReceiptDetail",
                            "idPromotionCondition", "quantityReceiptSaleDetailPromotionCondition",
                            "promotionSumReceiptSaleDetailPromotionCondition"};

                    for (String field : fields) {
                        Object value = receiptDetailResult.getValue(j).get(field);
                        if (field.equals("numberReceiptDetail")) {
                            value = numberReceiptDetail;
                            numberReceiptDetail++;
                        } else if (field.equals("idPromotionCondition")) {
                            value = paymentConditionMap.containsKey(idReceiptDetail) ? paymentConditionMap.get(idReceiptDetail)[0] : null;
                        } else if (field.equals("quantityReceiptSaleDetailPromotionCondition")) {
                            value = paymentConditionMap.containsKey(idReceiptDetail) ? paymentConditionMap.get(idReceiptDetail)[1] : null;
                        } else if (field.equals("promotionSumReceiptSaleDetailPromotionCondition")) {
                            value = paymentConditionMap.containsKey(idReceiptDetail) ? paymentConditionMap.get(idReceiptDetail)[2] : null;
                        }
                        if (value != null) {
                            Element element = doc.createElement(field.replace("Sale", "").replace("Return", ""));
                            element.appendChild(doc.createTextNode(String.valueOf(value)));
                            receiptDetail.appendChild(element);
                        }
                    }
                    receipt.appendChild(receiptDetail);
                }

                KeyExpr paymentExpr = new KeyExpr("payment");
                ImRevMap<Object, KeyExpr> paymentKeys = MapFact.singletonRev((Object) "payment", paymentExpr);

                QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<Object, Object>(paymentKeys);
                paymentQuery.addProperty("sumPayment", getLCP("sumPayment").getExpr(session.getModifier(), paymentExpr));
                paymentQuery.addProperty("paymentMeansPayment", getLCP("paymentMeansPayment").getExpr(session.getModifier(), paymentExpr));
                paymentQuery.addProperty("sidPaymentTypePayment", getLCP("sidPaymentTypePayment").getExpr(session.getModifier(), paymentExpr));

                paymentQuery.and(getLCP("receiptPayment").getExpr(session.getModifier(), paymentQuery.getMapExprs().get("payment")).compare(receiptObject.getExpr(), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> paymentResult = paymentQuery.execute(session.sql);
                for (ImMap<Object, Object> paymentValues : paymentResult.valueIt()) {
                    Element payment = doc.createElement("payment");
                    rootElement.appendChild(payment);

                    String[] fields = new String[]{"sumPayment", "paymentMeansPayment", "sidPaymentTypePayment"};
                    for (String field : fields) {
                        Object value = paymentValues.get(field);
                        if (value != null) {
                            Element element = doc.createElement(field);
                            element.appendChild(doc.createTextNode(String.valueOf(value)));
                            payment.appendChild(element);
                        }
                    }
                    receipt.appendChild(payment);
                }

                String[] fields = new String[]{"dateTimeReceipt", "discountSumReceipt", "numberDiscountCardReceipt"};
                for (String field : fields) {
                    Object value = receiptResult.getValue(i).get(field);
                    if (value != null) {
                        Element element = doc.createElement(field);
                        if (value instanceof Timestamp)
                            element.appendChild(doc.createTextNode(String.valueOf(((Timestamp) value).getTime())));
                        else
                            element.appendChild(doc.createTextNode(String.valueOf(value)));
                        receipt.appendChild(element);
                    }
                }
                rootElement.appendChild(receipt);

                receiptObjectsList.add(receiptObject);
            }

            // write the content into xml file
            TransformerFactory transfac = TransformerFactory.newInstance();
            Transformer trans = transfac.newTransformer();
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            trans.setOutputProperty(OutputKeys.INDENT, "yes");

            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(doc);
            trans.transform(source, result);
            String xmlString = sw.toString();
            File file = filePath == null ? File.createTempFile("export", ".xml") : new File(filePath + "//" + numberZReport.trim() + ".xml");
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(file), "UTF8"));
            writer.println(xmlString);
            writer.close();
            if (customPath) {
                files.put(numberZReport.trim() + ".xml", IOUtils.getFileBytes(file));
                context.delayUserInterfaction(new ExportFileClientAction(files));
            }

            for (DataObject receiptObject : receiptObjectsList)
                LM.findLCPByCompoundName("exportedIncrementReceipt").change(true, context, receiptObject);

            session.apply(context.getBL());

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }


}
