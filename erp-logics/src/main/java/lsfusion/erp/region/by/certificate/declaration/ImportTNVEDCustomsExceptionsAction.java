package lsfusion.erp.region.by.certificate.declaration;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.lang3.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportTNVEDCustomsExceptionsAction extends InternalAction {
    private static final String charset = "Cp866";

    public ImportTNVEDCustomsExceptionsAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файлы DBF", "dbf");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            if (objectValue != null) {
                importVATException(context, (RawFileData) objectValue.getValue());
            }

        } catch (xBaseJException | IOException | ScriptingErrorLog.SemanticErrorException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    private void importVATException(ExecutionContext<ClassPropertyInterface> context, RawFileData fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, ParseException, SQLHandledException {

        List<List<Object>> data = importVATExceptionFromDBF(context, fileBytes);

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField codeCustomsGroupField = new ImportField(findProperty("code[CustomsGroup]"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"),
                findProperty("customsGroup[BPSTRING[10]]").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idVATCustomsExceptionField = new ImportField(findProperty("id[VATCustomsException]"));
        ImportKey<?> VATCustomsExceptionKey = new ImportKey((CustomClass) findClass("VATCustomsException"),
                findProperty("VATCustomsException[STRING[100]]").getMapping(idVATCustomsExceptionField));
        keys.add(VATCustomsExceptionKey);
        props.add(new ImportProperty(idVATCustomsExceptionField, findProperty("id[VATCustomsException]").getMapping(VATCustomsExceptionKey)));
        fields.add(idVATCustomsExceptionField);

        ImportField nameVATCustomsExceptionField = new ImportField(findProperty("name[VATCustomsException]"));
        props.add(new ImportProperty(nameVATCustomsExceptionField, findProperty("name[VATCustomsException]").getMapping(VATCustomsExceptionKey)));
        fields.add(nameVATCustomsExceptionField);

        ImportField rangeField = new ImportField(findProperty("dataValueSupplierVAT[CustomsGroup,DATE]"));
        ImportKey<?> rangeKey = new ImportKey((CustomClass) findClass("Range"),
                findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(rangeField));
        props.add(new ImportProperty(rangeField, findProperty("range[VATCustomsException]").getMapping(VATCustomsExceptionKey),
                object(findClass("Range")).getMapping(rangeKey)));
        props.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[VATCustomsException]").getMapping(VATCustomsExceptionKey),
                object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(rangeField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, findProperty("dateFrom[VATCustomsException]").getMapping(VATCustomsExceptionKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, findProperty("dateTo[VATCustomsException]").getMapping(VATCustomsExceptionKey)));
        fields.add(dateToCustomsGroupField);

        ImportTable table = new ImportTable(fields, data);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private List<List<Object>> importVATExceptionFromDBF(ExecutionContext context, RawFileData fileBytes) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> data = new ArrayList<>();
        Map<String, List<Object>> dataVATMap = new HashMap<>();
        Set<String> tnvedSet = getTNVEDSet(context);

        File tempFile = null;
        DBF dbfFile = null;
        try {
            tempFile = File.createTempFile("tempTnved", ".dbf");
            fileBytes.write(tempFile);

            dbfFile = new DBF(tempFile.getPath());
            int recordCount = dbfFile.getRecordCount();

            for (int i = 1; i <= recordCount; i++) {
                dbfFile.read();

                Integer type = Integer.parseInt(new String(dbfFile.getField("PP").getBytes(), charset).trim());
                String codeCustomsGroup = new String(dbfFile.getField("G33").getBytes(), charset).trim();
                String name = new String(dbfFile.getField("TEXT").getBytes(), charset).trim();
                BigDecimal stav1 = new BigDecimal(new String(dbfFile.getField("STAV1").getBytes(), charset).trim());
                String dateFromValue = new String(dbfFile.getField("DATE1").getBytes(), charset).trim();
                String dateToValue = new String(dbfFile.getField("DATE2").getBytes(), charset).trim();
                if(!dateFromValue.isEmpty() && !dateToValue.isEmpty()) {
                    Date dateFrom = new Date(DateUtils.parseDate(dateFromValue, new String[]{"yyyyMMdd"}).getTime());
                    Date dateTo = new Date(DateUtils.parseDate(dateToValue, new String[]{"yyyyMMdd"}).getTime());
                    if (type.equals(4)) {
                        if (codeCustomsGroup.length() == 10)
                            data.add(Arrays.asList(codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo) + name, name, stav1, dateFrom, dateTo));
                        else
                            dataVATMap.put(codeCustomsGroup, Arrays.asList(codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo) + name, name, stav1, dateFrom, dateTo));
                    }
                }
            }
        } finally {
            if(dbfFile != null)
                dbfFile.close();
            if(tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }
        
        for (Map.Entry<String, List<Object>> entry : dataVATMap.entrySet()) {
            for(String tnved : tnvedSet) {
                if( tnved.startsWith(entry.getKey())) {
                    List<Object> attributes = entry.getValue();
                    data.add(Arrays.asList(tnved, attributes.get(1), attributes.get(2), attributes.get(3), attributes.get(4), attributes.get(5)));
                }
            }
        }

        return data;
    }

    private Set<String> getTNVEDSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Set<String> tnvedSet = new HashSet<>();

        LP<?> isCustomsGroup = is(findClass("CustomsGroup"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isCustomsGroup.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("codeCustomsGroup", findProperty("code[CustomsGroup]").getExpr(context.getModifier(), key));
        query.and(isCustomsGroup.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        for (ImMap<Object, Object> entry : result.values()) {
            String tnved = (String) entry.get("codeCustomsGroup");
            if (tnved != null && tnved.trim().length() == 10)
                tnvedSet.add(tnved.trim());
        }
        return tnvedSet;
    }
}