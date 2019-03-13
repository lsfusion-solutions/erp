package lsfusion.erp.region.by.certificate.declaration;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.DefaultImportDBFActionProperty;
import lsfusion.interop.action.ChooseObjectClientAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class ImportDeclarationAdjustmentDBFActionProperty extends DefaultImportDBFActionProperty {
    String charset = "cp866";
    private final ClassPropertyInterface declarationInterface;

    public ImportDeclarationAdjustmentDBFActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файл G47.DBF", "dbf");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            DataObject declarationObject = context.getDataKeyValue(declarationInterface);

            if (objectValue != null) {
                importDeclarationAdjustments(context, declarationObject, (RawFileData) objectValue.getValue());
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | xBaseJException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private void importDeclarationAdjustments(ExecutionContext context, DataObject declarationObject, RawFileData entry) throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, SQLHandledException {

        Map<String, List<List<Object>>> declarationAdjustmentsMap = readDeclarationAdjustmentsFromDBF(entry);

        List<List<Object>> data = chooseDeclaration(context, declarationAdjustmentsMap);

        if (data != null) {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberDeclarationAdjustmentField = new ImportField(findProperty("number[DeclarationAdjustment]"));
            ImportKey<?> declarationAdjustmentKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationAdjustment"),
                    findProperty("declarationAdjustment[Declaration,INTEGER]").getMapping(declarationObject, numberDeclarationAdjustmentField));
            keys.add(declarationAdjustmentKey);
            props.add(new ImportProperty(numberDeclarationAdjustmentField, findProperty("number[DeclarationAdjustment]").getMapping(declarationAdjustmentKey)));
            props.add(new ImportProperty(declarationObject, findProperty("declaration[DeclarationAdjustment]").getMapping(declarationAdjustmentKey)));
            fields.add(numberDeclarationAdjustmentField);

            ImportField numberDeclarationDetailField = new ImportField(findProperty("number[DeclarationDetail]"));
            ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationDetail"),
                    findProperty("declarationDetail[Declaration,INTEGER]").getMapping(declarationObject, numberDeclarationDetailField));
            keys.add(declarationDetailKey);
            props.add(new ImportProperty(declarationObject, findProperty("declaration[DeclarationDetail]").getMapping(declarationDetailKey)));
            fields.add(numberDeclarationDetailField);

            ImportField dutySumDeclarationAdjustmentField = new ImportField(findProperty("dutySum[DeclarationAdjustment, DeclarationDetail]"));
            props.add(new ImportProperty(dutySumDeclarationAdjustmentField, findProperty("dutySum[DeclarationAdjustment, DeclarationDetail]").getMapping(declarationAdjustmentKey, declarationDetailKey)));
            fields.add(dutySumDeclarationAdjustmentField);

            ImportField VATSumDeclarationAdjustmentField = new ImportField(findProperty("VATSum[DeclarationAdjustment, DeclarationDetail]"));
            props.add(new ImportProperty(VATSumDeclarationAdjustmentField, findProperty("VATSum[DeclarationAdjustment, DeclarationDetail]").getMapping(declarationAdjustmentKey, declarationDetailKey)));
            fields.add(VATSumDeclarationAdjustmentField);

            ImportField homeSumDeclarationAdjustmentField = new ImportField(findProperty("homeSum[DeclarationAdjustment, DeclarationDetail]"));
            props.add(new ImportProperty(homeSumDeclarationAdjustmentField, findProperty("homeSum[DeclarationAdjustment, DeclarationDetail]").getMapping(declarationAdjustmentKey, declarationDetailKey)));
            fields.add(homeSumDeclarationAdjustmentField);

            ImportTable table = new ImportTable(fields, data);

            IntegrationService service = new IntegrationService(context, table, keys, props);
            service.synchronize(true, false);
            context.requestUserInteraction(new MessageClientAction("Импорт успешно завершён", "Импорт КТС"));
        }
    }

    private Map<String, List<List<Object>>> readDeclarationAdjustmentsFromDBF(RawFileData entry) throws ScriptingErrorLog.SemanticErrorException, SQLException, IOException, xBaseJException, SQLHandledException {

        Map<String, List<List<Object>>> declarationsMap = new HashMap<>();
        File tempFile = null;
        DBF dbfFile = null;
        try {
            tempFile = File.createTempFile("tempTnved", ".dbf");
            entry.write(tempFile);

            dbfFile = new DBF(tempFile.getPath());
            int recordCount = dbfFile.getRecordCount();

            BigDecimal homeSum = null, dutySum = null, VATSum = null;

            String curNumberDeclaration = null;
            Integer curNumberDeclarationAdjustment = null;

            for (int i = 0; i < recordCount; i++) {

                dbfFile.read();

                String numberDeclaration = getDBFFieldValue(dbfFile, "Nomer_GTD", charset);

                Integer numberDeclarationAdjustment = getDBFIntegerFieldValue(dbfFile, "G32", charset);

                if ((curNumberDeclarationAdjustment != null && !curNumberDeclarationAdjustment.equals(numberDeclarationAdjustment)) || (curNumberDeclaration != null && !curNumberDeclaration.equals(numberDeclaration))) {
                    List<List<Object>> declarationEntry = declarationsMap.get(curNumberDeclaration);
                    if (declarationEntry == null)
                        declarationEntry = new ArrayList<>();
                    declarationEntry.add(Arrays.asList((Object) 1, curNumberDeclarationAdjustment, dutySum, VATSum, homeSum));
                    declarationsMap.put(curNumberDeclaration, declarationEntry);
                    dutySum = null;
                    VATSum = null;
                    homeSum = null;
                }
                curNumberDeclaration = numberDeclaration;
                curNumberDeclarationAdjustment = numberDeclarationAdjustment;

                String g471 = trim(getDBFFieldValue(dbfFile, "G471", charset));
                if (g471 != null) {
                    switch (g471) {
                        case "2010":
                            String g475 = trim(getDBFFieldValue(dbfFile, "G475", charset));
                            if(g475 != null && g475.equals("УМ")) {
                                homeSum = BigDecimal.ZERO;
                                dutySum = BigDecimal.ZERO;
                            } else {
                                homeSum = getDBFBigDecimalFieldValue(dbfFile, "G472", charset);
                                BigDecimal extraDutySum = getDBFBigDecimalFieldValue(dbfFile, "G474", charset);
                                if (dutySum == null)
                                    dutySum = extraDutySum;
                                else if (extraDutySum != null)
                                    dutySum = dutySum.add(extraDutySum);
                            }
                            break;
                        case "5010":
                            if (homeSum == null) homeSum = getDBFBigDecimalFieldValue(dbfFile, "G472", charset);
                            VATSum = getDBFBigDecimalFieldValue(dbfFile, "G474", charset);
                            break;
                    }
                }
            }

            if (curNumberDeclarationAdjustment != null) {
                List<List<Object>> declarationEntry = declarationsMap.get(curNumberDeclaration);
                if (declarationEntry == null)
                    declarationEntry = new ArrayList<>();
                declarationEntry.add(Arrays.asList((Object) 1, curNumberDeclarationAdjustment, dutySum, VATSum, homeSum));
                declarationsMap.put(curNumberDeclaration, declarationEntry);
            }
        } finally {
            if (dbfFile != null)
                dbfFile.close();
            if (tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }
        return declarationsMap;
    }

    private List<List<Object>> chooseDeclaration(ExecutionContext context, Map<String, List<List<Object>>> declarationMap) {
        List<List<Object>> data = null;
        Object[][] variants = new Object[declarationMap.size()][1];
        int i = 0;
        for (String key : declarationMap.keySet()) {
            variants[i][0] = key;
            i++;
        }
        if (declarationMap.isEmpty())
            context.requestUserInteraction(new MessageClientAction("Не найдено ни одной декларации во входном файле G47", "Ошибка"));
        else {
            Integer index = (Integer) (declarationMap.size() == 1 ? 0 :
                    context.requestUserInteraction(new ChooseObjectClientAction("Выберите декларацию", new String[]{"Номер декларации"}, variants)));
            if (index != null) {
                if (index == -1)
                    index = 0;

                data = declarationMap.get(variants[index][0]);
            }
        }
        return data;
    }
}