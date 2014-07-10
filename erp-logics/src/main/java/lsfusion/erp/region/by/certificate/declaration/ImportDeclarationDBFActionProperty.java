package lsfusion.erp.region.by.certificate.declaration;

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

    public ImportDeclarationDBFActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("Declaration"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файл G47.DBF", "DBF");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            DataObject declarationObject = context.getDataKeyValue(declarationInterface);

            if (objectValue != null) {

                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                
                for (byte[] entry : fileList) {

                    File tempFile = File.createTempFile("tempTnved", ".dbf");
                    IOUtils.putFileBytes(tempFile, entry);

                    DBF dbfFile = new DBF(tempFile.getPath());

                    importDeclaration(context, declarationObject, dbfFile);
                    
                    tempFile.delete();

                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        }
    }

    private void importDeclaration(ExecutionContext context, DataObject declarationObject, DBF dbfFile) throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, SQLHandledException {

        DataSession session = context.createSession();
        
        List<List<Object>> data = readDeclarationFromDBF(session, declarationObject, dbfFile);

        KeyExpr declarationDetailExpr = new KeyExpr("DeclarationDetail");
        ImRevMap<Object, KeyExpr> declarationDetailKeys = MapFact.singletonRev((Object) "declarationDetail", declarationDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(declarationDetailKeys);
        
        query.and(findProperty("declarationDeclarationDetail").getExpr(context.getModifier(), declarationDetailExpr).compare(declarationObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        if(result.size() != data.size())
            context.requestUserInteraction(new MessageClientAction(String.format("Разное количество строк во входном файле G47 (%s) и в базе (%s)", data.size(), result.size()), "Ошибка"));
        
        else {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField numberDeclarationDetailField = new ImportField(findProperty("numberDeclarationDetail"));
            ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) findClass("DeclarationDetail"),
                    findProperty("declarationDetailDeclarationNumber").getMapping(declarationObject, numberDeclarationDetailField));
            keys.add(declarationDetailKey);
            props.add(new ImportProperty(declarationObject, findProperty("declarationDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(numberDeclarationDetailField);

            ImportField dutySumDeclarationDetailField = new ImportField(findProperty("dutySumDeclarationDetail"));
            props.add(new ImportProperty(dutySumDeclarationDetailField, findProperty("dutySumDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(dutySumDeclarationDetailField);

            ImportField VATSumDeclarationDetailField = new ImportField(findProperty("VATSumDeclarationDetail"));
            props.add(new ImportProperty(VATSumDeclarationDetailField, findProperty("VATSumDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(VATSumDeclarationDetailField);

            ImportField homeSumDeclarationDetailField = new ImportField(findProperty("homeSumDeclarationDetail"));
            props.add(new ImportProperty(homeSumDeclarationDetailField, findProperty("homeSumDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(homeSumDeclarationDetailField);

            ImportTable table = new ImportTable(fields, data);

            session.pushVolatileStats("DBF_DN");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private List<List<Object>> readDeclarationFromDBF(DataSession session, DataObject declarationObject, DBF importFile) throws ScriptingErrorLog.SemanticErrorException, SQLException, IOException, xBaseJException, SQLHandledException {

        int recordCount = importFile.getRecordCount();
        
        BigDecimal homeSum = null, dutySum = null, VATSum = null;
        List<List<Object>> data = new ArrayList<List<Object>>();
        
        Integer curNumber = null;
        
        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            Integer numberDeclarationDetail = getDBFIntegerFieldValue(importFile, "G32", charset);

            if (curNumber != null && !curNumber.equals(numberDeclarationDetail)) {
                data.add(Arrays.asList((Object) curNumber, dutySum, VATSum, homeSum));
                dutySum = null;
                VATSum = null;
                homeSum = null;
            }
            curNumber = numberDeclarationDetail;

            String g471 = trim(getDBFFieldValue(importFile, "G471", charset));
                    
            if (g471 != null) {
                if (g471.equals("2010")) {
                    homeSum = getDBFBigDecimalFieldValue(importFile, "G472", charset);
                    dutySum = getDBFBigDecimalFieldValue(importFile, "G474", charset);
                } else if (g471.equals("5010")) {
                    if (homeSum == null) homeSum = getDBFBigDecimalFieldValue(importFile, "G472", charset);
                    VATSum = getDBFBigDecimalFieldValue(importFile, "G474", charset);
                } else if(g471.equals("1010")) {
                    BigDecimal g474 = getDBFBigDecimalFieldValue(importFile, "G474", charset);  //dutySum - VATSum
                    findProperty("registrationSumDeclaration").change(g474, session, declarationObject);
                }
            }
        }

        if (curNumber != null) {
            data.add(Arrays.asList((Object) curNumber, dutySum, VATSum, homeSum));
        }

        importFile.close();
        return data;
    }
}
