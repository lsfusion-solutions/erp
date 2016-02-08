package lsfusion.erp.region.by.certificate.declaration;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportDBFActionProperty;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImportDeclarationDBFActionProperty extends DefaultImportDBFActionProperty {
    String charset = "cp866";
    private final ClassPropertyInterface declarationInterface;

    public ImportDeclarationDBFActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файл G47.DBF", "dbf");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            DataObject declarationObject = context.getDataKeyValue(declarationInterface);

            if (objectValue != null) {

                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                
                for (byte[] entry : fileList) {

                    importDeclaration(context, declarationObject, entry);

                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | xBaseJException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private void importDeclaration(ExecutionContext context, DataObject declarationObject, byte[] entry) throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, SQLHandledException {

        List<List<Object>> data = readDeclarationFromDBF(context, declarationObject, entry);

        KeyExpr declarationDetailExpr = new KeyExpr("DeclarationDetail");
        ImRevMap<Object, KeyExpr> declarationDetailKeys = MapFact.singletonRev((Object) "declarationDetail", declarationDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(declarationDetailKeys);

        query.and(findProperty("declaration[DeclarationDetail]").getExpr(context.getModifier(), declarationDetailExpr).compare(declarationObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        if(result.size() != data.size())
            context.requestUserInteraction(new MessageClientAction(String.format("Разное количество строк во входном файле G47 (%s) и в базе (%s)", data.size(), result.size()), "Ошибка"));

        else {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberDeclarationDetailField = new ImportField(findProperty("number[DeclarationDetail]"));
            ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationDetail"),
                    findProperty("declarationDetail[Declaration,INTEGER]").getMapping(declarationObject, numberDeclarationDetailField));
            keys.add(declarationDetailKey);
            props.add(new ImportProperty(declarationObject, findProperty("declaration[DeclarationDetail]").getMapping(declarationDetailKey)));
            fields.add(numberDeclarationDetailField);

            ImportField dutySumDeclarationDetailField = new ImportField(findProperty("dutySum[DeclarationDetail]"));
            props.add(new ImportProperty(dutySumDeclarationDetailField, findProperty("dutySum[DeclarationDetail]").getMapping(declarationDetailKey)));
            fields.add(dutySumDeclarationDetailField);

            ImportField VATSumDeclarationDetailField = new ImportField(findProperty("VATSum[DeclarationDetail]"));
            props.add(new ImportProperty(VATSumDeclarationDetailField, findProperty("VATSum[DeclarationDetail]").getMapping(declarationDetailKey)));
            fields.add(VATSumDeclarationDetailField);

            ImportField homeSumDeclarationDetailField = new ImportField(findProperty("homeSum[DeclarationDetail]"));
            props.add(new ImportProperty(homeSumDeclarationDetailField, findProperty("homeSum[DeclarationDetail]").getMapping(declarationDetailKey)));
            fields.add(homeSumDeclarationDetailField);

            ImportTable table = new ImportTable(fields, data);

            String resultMessage;
            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("DBF_DN");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                resultMessage = session.applyMessage(context);
                session.popVolatileStats();
            }
            if(resultMessage == null) {
                context.requestUserInteraction(new MessageClientAction("Импорт успешно завершён", "Импорт из декларанта"));
            }
        }
    }

    private List<List<Object>> readDeclarationFromDBF(ExecutionContext context, DataObject declarationObject, byte[] entry) throws ScriptingErrorLog.SemanticErrorException, SQLException, IOException, xBaseJException, SQLHandledException {
        
        List<List<Object>> data = new ArrayList<>();
        File tempFile = null;
        DBF dbfFile = null;
        try {
            tempFile = File.createTempFile("tempTnved", ".dbf");
            IOUtils.putFileBytes(tempFile, entry);

            dbfFile = new DBF(tempFile.getPath());
            int recordCount = dbfFile.getRecordCount();

            BigDecimal homeSum = null, dutySum = null, VATSum = null;

            Integer curNumber = null;

            for (int i = 0; i < recordCount; i++) {

                dbfFile.read();

                Integer numberDeclarationDetail = getDBFIntegerFieldValue(dbfFile, "G32", charset);

                if (curNumber != null && !curNumber.equals(numberDeclarationDetail)) {
                    data.add(Arrays.asList((Object) curNumber, dutySum, VATSum, homeSum));
                    dutySum = null;
                    VATSum = null;
                    homeSum = null;
                }
                curNumber = numberDeclarationDetail;

                String g471 = trim(getDBFFieldValue(dbfFile, "G471", charset));

                if (g471 != null) {
                    if (g471.equals("2010")) {
                        homeSum = getDBFBigDecimalFieldValue(dbfFile, "G472", charset);
                        BigDecimal extraDutySum = getDBFBigDecimalFieldValue(dbfFile, "G474", charset);
                        if (dutySum == null)
                            dutySum = extraDutySum;
                        else
                            if (extraDutySum != null)
                                dutySum = dutySum.add(extraDutySum);
                    } else if (g471.equals("5010")) {
                        if (homeSum == null) homeSum = getDBFBigDecimalFieldValue(dbfFile, "G472", charset);
                        VATSum = getDBFBigDecimalFieldValue(dbfFile, "G474", charset);
                    } else if (g471.equals("1010")) {
                        BigDecimal g474 = getDBFBigDecimalFieldValue(dbfFile, "G474", charset);  //dutySum - VATSum
                        findProperty("registrationSum[Declaration]").change(g474, context, declarationObject);
                    }
                }
            }

            if (curNumber != null) {
                data.add(Arrays.asList((Object) curNumber, dutySum, VATSum, homeSum));
            }
        } finally {
            if(dbfFile != null)
                dbfFile.close();
            if(tempFile != null)
                tempFile.delete();
        }
        return data;
    }
}
