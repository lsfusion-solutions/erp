package lsfusion.erp.region.by.certificate.declaration;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.DefaultImportAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImportDeclarationAdjustmentXMLAction extends DefaultImportAction {
    private final ClassPropertyInterface declarationInterface;

    public ImportDeclarationAdjustmentXMLAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        declarationInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            ObjectValue objectValue = requestUserData(context, "Файлы XML", "xml");
            if (objectValue != null) {
                DataObject declarationObject = context.getDataKeyValue(declarationInterface);
                RawFileData file = (RawFileData) objectValue.getValue();

                List<List<Object>> data = new ArrayList<>();

                SAXBuilder builder = new SAXBuilder();
                Document document = builder.build(file.getInputStream());
                Element rootNode = document.getRootElement();
                Namespace ns = rootNode.getNamespace("KDTout");
                Namespace gns = rootNode.getNamespace("catESAD_cu");
                Namespace catRu = rootNode.getNamespace("cat_ru");

                Element registryNumberNode = rootNode.getChild("GtdRegistryNumber", ns);
                Date registryDate = parseDate(registryNumberNode.getChildText("RegistrationDate", catRu));

                rootNode = rootNode.getChild("KDToutGoodsShipment", ns);

                List<Element> list = rootNode.getChildren("KDToutGoods", ns);
                for (Element node : list) {
                    List<Element> payment = node.getChildren("KDToutCustomsPaymentCalculation", ns);

                    Double duty = null;
                    Double vat = null;
                    for (Element p : payment) {
                        String paymentModeCode = p.getChildText("PaymentModeCode", gns);
                        if ("2010".equals(paymentModeCode)) {
                            duty = Double.valueOf(p.getChildText("PaymentAmount", gns));
                        } else if ("5010".equals(paymentModeCode))
                            vat = Double.valueOf(p.getChildText("PaymentAmount", gns));
                    }
                    Double sum = Double.valueOf(node.getChildText("CustomsCost", gns));
                    Integer number = Integer.valueOf(node.getChildText("GoodsNumeric", gns));

                    data.add(Arrays.asList(1, number, sqlDateToLocalDate(registryDate), duty, vat, sum));
                }

                List<ImportProperty<?>> properties = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                ImportField numberDeclarationAdjustmentField = new ImportField(findProperty("number[DeclarationAdjustment]"));
                ImportKey<?> declarationAdjustmentKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationAdjustment"),
                        findProperty("declarationAdjustment[Declaration,INTEGER]").getMapping(declarationObject, numberDeclarationAdjustmentField));
                keys.add(declarationAdjustmentKey);
                properties.add(new ImportProperty(numberDeclarationAdjustmentField, findProperty("number[DeclarationAdjustment]").getMapping(declarationAdjustmentKey)));
                properties.add(new ImportProperty(declarationObject, findProperty("declaration[DeclarationAdjustment]").getMapping(declarationAdjustmentKey)));
                fields.add(numberDeclarationAdjustmentField);

                ImportField numberDeclarationDetailField = new ImportField(findProperty("number[DeclarationDetail]"));
                ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationDetail"),
                        findProperty("declarationDetail[Declaration,INTEGER]").getMapping(declarationObject, numberDeclarationDetailField));
                keys.add(declarationDetailKey);
                properties.add(new ImportProperty(declarationObject, findProperty("declaration[DeclarationDetail]").getMapping(declarationDetailKey)));
                fields.add(numberDeclarationDetailField);

                ImportField dateDeclarationAdjustment = new ImportField(findProperty("date[DeclarationAdjustment]"));
                properties.add(new ImportProperty(dateDeclarationAdjustment, findProperty("date[DeclarationAdjustment]").getMapping(declarationAdjustmentKey)));
                fields.add(dateDeclarationAdjustment);

                ImportField dutySumDeclarationAdjustmentField = new ImportField(findProperty("dutySum[DeclarationAdjustment, DeclarationDetail]"));
                properties.add(new ImportProperty(dutySumDeclarationAdjustmentField, findProperty("dutySum[DeclarationAdjustment, DeclarationDetail]").getMapping(declarationAdjustmentKey, declarationDetailKey)));
                fields.add(dutySumDeclarationAdjustmentField);

                ImportField VATSumDeclarationAdjustmentField = new ImportField(findProperty("VATSum[DeclarationAdjustment, DeclarationDetail]"));
                properties.add(new ImportProperty(VATSumDeclarationAdjustmentField, findProperty("VATSum[DeclarationAdjustment, DeclarationDetail]").getMapping(declarationAdjustmentKey, declarationDetailKey)));
                fields.add(VATSumDeclarationAdjustmentField);

                ImportField homeSumDeclarationAdjustmentField = new ImportField(findProperty("homeSum[DeclarationAdjustment, DeclarationDetail]"));
                properties.add(new ImportProperty(homeSumDeclarationAdjustmentField, findProperty("homeSum[DeclarationAdjustment, DeclarationDetail]").getMapping(declarationAdjustmentKey, declarationDetailKey)));
                fields.add(homeSumDeclarationAdjustmentField);

                integrationServiceSynchronize(context, fields, data, keys, properties);
                context.requestUserInteraction(new MessageClientAction("Импорт успешно завершён", "Импорт КТС"));
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
