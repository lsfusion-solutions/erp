package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.IOUtils;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
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
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class ImportDeclarationDBFActionProperty extends DefaultImportActionProperty {
    private final ClassPropertyInterface declarationInterface;

    public ImportDeclarationDBFActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Declaration"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

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

    private void importDeclaration(ExecutionContext context, DataObject declarationObject, DBF dbfFile) throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException {

        List<List<Object>> data = readDeclarationFromDBF(dbfFile);

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        ImportField numberDeclarationDetailField = new ImportField(getLCP("numberDeclarationDetail"));
        ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("DeclarationDetail"),
                getLCP("declarationDetailDeclarationNumber").getMapping(declarationObject, numberDeclarationDetailField));
        keys.add(declarationDetailKey);
        props.add(new ImportProperty(declarationObject, getLCP("declarationDeclarationDetail").getMapping(declarationDetailKey)));
        fields.add(numberDeclarationDetailField);

        ImportField dutySumDeclarationDetailField = new ImportField(getLCP("dutySumDeclarationDetail"));
        props.add(new ImportProperty(dutySumDeclarationDetailField, getLCP("dutySumDeclarationDetail").getMapping(declarationDetailKey)));
        fields.add(dutySumDeclarationDetailField);

        ImportField VATSumDeclarationDetailField = new ImportField(getLCP("VATSumDeclarationDetail"));
        props.add(new ImportProperty(VATSumDeclarationDetailField, getLCP("VATSumDeclarationDetail").getMapping(declarationDetailKey)));
        fields.add(VATSumDeclarationDetailField);

        ImportField homeSumDeclarationDetailField = new ImportField(getLCP("homeSumDeclarationDetail"));
        props.add(new ImportProperty(homeSumDeclarationDetailField, getLCP("homeSumDeclarationDetail").getMapping(declarationDetailKey)));
        fields.add(homeSumDeclarationDetailField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        session.sql.pushVolatileStats(null);
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context.getBL());
        session.sql.popVolatileStats(null);
        session.close();
    }

    private List<List<Object>> readDeclarationFromDBF(DBF importFile) throws ScriptingErrorLog.SemanticErrorException, SQLException, IOException, xBaseJException {

        int recordCount = importFile.getRecordCount();

        List<List<Object>> data = new ArrayList<List<Object>>();

        BigDecimal dutySum = null;
        boolean second = true;
        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            String g471 = getFieldValue(importFile, "G471", "cp866", false, null);

            if (g471 != null && (g471.trim().equals("2010") || g471.trim().equals("5010"))) {
                second = !second;

                Integer numberDeclarationDetail = getIntegerFieldValue(importFile, "G32", "cp866", false, null);
                BigDecimal homeSum = getBigDecimalFieldValue(importFile, "G472", "cp866", false, null);
                BigDecimal g474 = getBigDecimalFieldValue(importFile, "G474", "cp866", false, null);  //dutySum - VATSum
                if (second) {
                    data.add(Arrays.asList((Object) numberDeclarationDetail, dutySum, g474, homeSum));
                } else {
                    dutySum = g474;
                }
            }
        }
        return data;
    }


    private String getFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() || (zeroIsNull && result.equals("0")) ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    private BigDecimal getBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new BigDecimal(result.replace(",", "."));
    }

    private Integer getIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new Double(result).intValue();
    }
}
