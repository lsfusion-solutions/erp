package lsfusion.erp.region.by.certificate.declaration;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.http.client.utils.DateUtils;
import org.jdom.*;
import org.jdom.input.SAXBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ImportXMLDeclarationActionProperty extends DefaultImportActionProperty {
    private final ClassPropertyInterface declarationInterface;

    public ImportXMLDeclarationActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы XML", "xml");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                DataObject declarationObject = context.getDataKeyValue(declarationInterface);
                for (byte[] file : fileList) {

                    List<List<Object>> data = new ArrayList<>();

                    SAXBuilder builder = new SAXBuilder();
                    Document document = builder.build(new ByteArrayInputStream(file));
                    Element rootNode = document.getRootElement();
                    Namespace ns = rootNode.getNamespace("ESADout_CU");
                    Namespace gns = rootNode.getNamespace("catESAD_cu");
                    Namespace cu = rootNode.getNamespace("cat_ru");
                    rootNode = rootNode.getChild("ESADout_CUGoodsShipment", ns);
                    List list = rootNode.getChildren("ESADout_CUGoods", ns);
                    for (Object aList : list) {
                        Element node = (Element) aList;
                        List payment = node.getChildren("ESADout_CUCustomsPaymentCalculation", ns);

                        BigDecimal duty = null;
                        BigDecimal sumVAT = null;
                        BigDecimal valueVAT = null;
                        for (Object p : payment) {
                            String paymentModeCode = ((Element) p).getChildText("PaymentModeCode", gns);
                            if ("2010".equals(paymentModeCode)) {
                                duty = parseBigDecimal(((Element) p).getChildText("PaymentAmount", gns));
                            } else if ("5010".equals(paymentModeCode)) {
                                sumVAT = parseBigDecimal(((Element) p).getChildText("PaymentAmount", gns));
                                valueVAT = parseBigDecimal(((Element) p).getChildText("Rate", gns));
                            }
                        }
                        BigDecimal sum = parseBigDecimal(node.getChildText("CustomsCost", gns));
                        Integer number = Integer.valueOf(node.getChildText("GoodsNumeric", gns));
                        String description = node.getChildText("GoodsDescription", gns);
                        String TNVED = node.getChildText("GoodsTNVEDCode", gns);
                        String countryCode = node.getChildText("OriginCountryCode", gns);
                        Element goodsGroupDescription = node.getChild("GoodsGroupDescription", gns);
                        Element goodsGroupInformation = goodsGroupDescription.getChild("GoodsGroupInformation", gns);
                        Element goodsGroupQuantity = goodsGroupInformation.getChild("GoodsGroupQuantity", gns);
                        String uomName = goodsGroupQuantity.getChildText("MeasureUnitQualifierName", cu);
                        String uomCode = goodsGroupQuantity.getChildText("MeasureUnitQualifierCode", cu);

                        data.add(Arrays.<Object>asList(number, description, sum, duty, valueVAT, sumVAT, TNVED, countryCode, uomCode, uomName));
                    }

                    Date declarationDate = null;
                    String declarationNumber = null;
                    for (Object element : document.getContent()) {
                        if (element instanceof Comment) {
                            Pattern commentPattern = Pattern.compile("(.*)\\:\\s(.*)");
                            Matcher commentMatcher = commentPattern.matcher(((Comment) element).getText());
                            if (commentMatcher.matches()) {
                                String key = commentMatcher.group(1);
                                if(key.contains("DT_DATE"))
                                    declarationDate = new Date(DateUtils.parseDate(trim(commentMatcher.group(2)), new String[]{"dd.MM.yyyy"}).getTime());
                                else if(key.contains("NOM_REG"))
                                    declarationNumber = trim(commentMatcher.group(2));

                            }
                        }
                    }

                    List<ImportProperty<?>> properties = new ArrayList<>();
                    List<ImportField> fields = new ArrayList<>();
                    List<ImportKey<?>> keys = new ArrayList<>();

                    ImportField userNumberField = new ImportField(findProperty("userNumber[DeclarationDetail]"));
                    ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationDetail"),
                            findProperty("declarationDetail[Declaration,INTEGER]").getMapping(declarationObject, userNumberField));
                    keys.add(declarationDetailKey);
                    properties.add(new ImportProperty(userNumberField, findProperty("userNumber[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(declarationObject, findProperty("declaration[DeclarationDetail]").getMapping(declarationDetailKey)));
                    fields.add(userNumberField);

                    ImportField nameCustomsField = new ImportField(findProperty("nameCustoms[DeclarationDetail]"));
                    properties.add(new ImportProperty(nameCustomsField, findProperty("nameCustoms[DeclarationDetail]").getMapping(declarationDetailKey)));
                    fields.add(nameCustomsField);

                    ImportField sumDataField = new ImportField(findProperty("homeSum[DeclarationDetail]"));
                    properties.add(new ImportProperty(sumDataField, findProperty("homeSum[DeclarationDetail]").getMapping(declarationDetailKey)));
                    fields.add(sumDataField);

                    ImportField sumDutyDataField = new ImportField(findProperty("dutySum[DeclarationDetail]"));
                    properties.add(new ImportProperty(sumDutyDataField, findProperty("dutySum[DeclarationDetail]").getMapping(declarationDetailKey)));
                    fields.add(sumDutyDataField);

                    ImportField valueVATUserInvoiceDetailField = new ImportField(findProperty("percentVAT[DeclarationDetail]"));
                    ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                            findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATUserInvoiceDetailField));
                    keys.add(VATKey);
                    properties.add(new ImportProperty(valueVATUserInvoiceDetailField, findProperty("VAT[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("Range")).getMapping(VATKey)));
                    fields.add(valueVATUserInvoiceDetailField);

                    ImportField sumVATDataField = new ImportField(findProperty("VATSum[DeclarationDetail]"));
                    properties.add(new ImportProperty(sumVATDataField, findProperty("VATSum[DeclarationDetail]").getMapping(declarationDetailKey)));
                    fields.add(sumVATDataField);

                    ImportField codeCustomsGroupField = new ImportField(findProperty("codeCustomsGroup[DeclarationDetail]"));
                    ImportKey<?> customsGroupKey = new ImportKey((ConcreteCustomClass) findClass("CustomsGroup"),
                            findProperty("customsGroup[STRING[10]]").getMapping(codeCustomsGroupField));
                    keys.add(customsGroupKey);
                    properties.add(new ImportProperty(codeCustomsGroupField, findProperty("code[CustomsGroup]").getMapping(customsGroupKey)));
                    properties.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
                    fields.add(codeCustomsGroupField);

                    ImportField sidOrigin2CountryField = new ImportField(findProperty("sidOrigin2Country[DeclarationDetail]"));
                    ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                            findProperty("countrySIDOrigin2[STRING[2]]").getMapping(sidOrigin2CountryField));
                    keys.add(countryKey);
                    properties.add(new ImportProperty(sidOrigin2CountryField, findProperty("sidOrigin2Country[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(sidOrigin2CountryField, findProperty("country[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("Country")).getMapping(countryKey)));
                    fields.add(sidOrigin2CountryField);

                    ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
                    ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                            findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
                    keys.add(UOMKey);
                    properties.add(new ImportProperty(idUOMField, findProperty("id[UOM]").getMapping(UOMKey)));
                    properties.add(new ImportProperty(idUOMField, findProperty("UOM[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("UOM")).getMapping(UOMKey)));
                    fields.add(idUOMField);

                    ImportField nameUOMField = new ImportField(findProperty("name[UOM]"));
                    properties.add(new ImportProperty(nameUOMField, findProperty("name[UOM]").getMapping(UOMKey)));
                    properties.add(new ImportProperty(nameUOMField, findProperty("shortName[UOM]").getMapping(UOMKey)));
                    fields.add(nameUOMField);

                    IntegrationService integrationService = new IntegrationService(context.getSession(), new ImportTable(fields, data), keys, properties);
                    integrationService.synchronize(true, false);

                    if(declarationDate != null)
                        findProperty("date[Declaration]").change(declarationDate, context, declarationObject);
                    if(declarationNumber != null)
                        findProperty("number[Declaration]").change(declarationNumber, context, declarationObject);
                    findProperty("isExported[Declaration]").change(true, context, declarationObject);
                }
            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException | SQLException | JDOMException e) {
            throw Throwables.propagate(e);
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null ? null : new BigDecimal(trim(value).replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
