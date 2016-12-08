package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import lsfusion.base.BaseUtils;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


public class ImportXMLDeclarationActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface declarationInterface;
    List<Object> row;

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

                DataObject declaration = context.getDataKeyValue(declarationInterface);
                //ObjectValue customsZone = LM.findProperty("customsZoneDeclaration").readClasses(context.getSession(), declaration);
                for (byte[] file : fileList) {

                    List<List<Object>> data = new ArrayList<>();
                    Date defaultDate = new Date(2011-1900, 0, 1);

                    SAXBuilder builder = new SAXBuilder();
                    Document document = builder.build(new ByteArrayInputStream(file));
                    Element rootNode = document.getRootElement();
                    Namespace ns = rootNode.getNamespace("ESADout_CU");
                    Namespace gns = rootNode.getNamespace("catESAD_cu");
                    Namespace cu = rootNode.getNamespace("cat_ru");
                    rootNode = rootNode.getChild("ESADout_CUGoodsShipment", ns);
                    List list = rootNode.getChildren("ESADout_CUGoods", ns);
                    for (int i = 0; i < list.size(); i++) {
                        Element node = (Element) list.get(i);
                        row = new ArrayList<>();
                        List payment = node.getChildren("ESADout_CUCustomsPaymentCalculation", ns);

                        Double duty = null;
                        Double vat = null;
                        for (Object p : payment) {
                            String paymentModeCode = ((Element) p).getChildText("PaymentModeCode", gns);
                            if ("2010".equals(paymentModeCode)) {
                                duty = Double.valueOf(((Element) p).getChildText("PaymentAmount", gns));
                            } else if ("5010".equals(paymentModeCode))
                                vat = Double.valueOf(((Element) p).getChildText("PaymentAmount", gns));
                        }
                        Double sum = Double.valueOf(node.getChildText("CustomsCost", gns));
                        Integer number = Integer.valueOf(node.getChildText("GoodsNumeric", gns));
                        String description = node.getChildText("GoodsDescription", gns);
                        String TNVED = node.getChildText("GoodsTNVEDCode", gns);
                        String countryCode = node.getChildText("OriginCountryCode", gns);
                        Element goodsGroupDescription = node.getChild("GoodsGroupDescription", gns);
                        Element goodsGroupInformation = goodsGroupDescription.getChild("GoodsGroupInformation", gns);
                        Element goodsGroupQuantity = goodsGroupInformation.getChild("GoodsGroupQuantity", gns);
                        String uomName = goodsGroupQuantity.getChildText("MeasureUnitQualifierName", cu);
                        String uomCode = goodsGroupQuantity.getChildText("MeasureUnitQualifierCode", cu);

                        row.add(number);
                        row.add(description);
                        row.add(sum);
                        row.add(duty);
                        row.add(vat);
                        row.add(defaultDate);
                        row.add(TNVED);
                        row.add(countryCode);
                        row.add(uomName);
                        row.add(uomCode);
                        data.add(row);
                    }

                    ImportField userNumberField = new ImportField(findProperty("userNumber[DeclarationDetail]"));
                    ImportField nameCustomsField = new ImportField(findProperty("nameCustoms[DeclarationDetail]"));
                    ImportField sumDataField = new ImportField(findProperty("homeSum[DeclarationDetail]"));
                    ImportField sumDutyDataField = new ImportField(findProperty("dutySum[DeclarationDetail]"));
                    ImportField sumVATDataField = new ImportField(findProperty("VATSum[DeclarationDetail]"));
                    ImportField dateField = new ImportField(DateClass.instance);
                    ImportField codeCustomsGroupField = new ImportField(findProperty("codeCustomsGroup[DeclarationDetail]"));
                    ImportField sidOrigin2CountryField = new ImportField(findProperty("sidOrigin2Country[DeclarationDetail]"));
                    ImportField nameUOMField = new ImportField(findProperty("name[UOM]"));
                    ImportField UOMIDField = new ImportField(findProperty("id[UOM]"));

                    List<ImportProperty<?>> properties = new ArrayList<>();

                    ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationDetail"),
                            findProperty("declarationDetail[Declaration,INTEGER]").getMapping(declaration, userNumberField));

                    ImportKey<?> customsGroupKey = new ImportKey((ConcreteCustomClass) findClass("CustomsGroup"),
                            findProperty("customsGroup[STRING[10]]").getMapping(codeCustomsGroupField));

                    ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                            findProperty("countrySIDOrigin2[STRING[2]]").getMapping(sidOrigin2CountryField));

                    ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                            findProperty("UOM[VARSTRING[100]]").getMapping(UOMIDField));

                    properties.add(new ImportProperty(userNumberField, findProperty("userNumber[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(nameCustomsField, findProperty("nameCustoms[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(sumDataField, findProperty("homeSum[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(sumDutyDataField, findProperty("dutySum[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(sumVATDataField, findProperty("VATSum[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(declaration, findProperty("declaration[DeclarationDetail]").getMapping(declarationDetailKey)));

                    properties.add(new ImportProperty(nameUOMField, findProperty("name[UOM]").getMapping(UOMKey)));
                    properties.add(new ImportProperty(nameUOMField, findProperty("shortName[UOM]").getMapping(UOMKey)));
                    properties.add(new ImportProperty(UOMIDField, findProperty("id[UOM]").getMapping(UOMKey)));
                    properties.add(new ImportProperty(UOMIDField, findProperty("UOM[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("UOM")).getMapping(UOMKey)));

                    properties.add(new ImportProperty(codeCustomsGroupField, findProperty("code[CustomsGroup]").getMapping(customsGroupKey)));
                    properties.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
                    properties.add(new ImportProperty(sidOrigin2CountryField, findProperty("sidOrigin2Country[DeclarationDetail]").getMapping(declarationDetailKey)));
                    properties.add(new ImportProperty(sidOrigin2CountryField, findProperty("country[DeclarationDetail]").getMapping(declarationDetailKey),
                            object(findClass("Country")).getMapping(countryKey)));

                    List<ImportField> fields = BaseUtils.toList(userNumberField, nameCustomsField, sumDataField,
                            sumDutyDataField, sumVATDataField, dateField, codeCustomsGroupField, sidOrigin2CountryField,
                            nameUOMField, UOMIDField);
                    ImportKey<?>[] keysArray = new ImportKey<?>[]{declarationDetailKey, customsGroupKey, countryKey, UOMKey};

                    IntegrationService integrationService = new IntegrationService(context.getSession(), new ImportTable(fields, data), Arrays.asList(keysArray), properties);
                    integrationService.synchronize(true, false);
                }
            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException | SQLException | JDOMException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
