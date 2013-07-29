package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.commons.lang.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportTNVEDCustomsExceptionsActionProperty extends ScriptingActionProperty {

    public ImportTNVEDCustomsExceptionsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {
            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы DBF", "DBF");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                for (byte[] file : fileList) {
                    importVATException(context, file);
                }
            }

        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void importVATException(ExecutionContext<ClassPropertyInterface> context, byte[] fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, ParseException {

        List<List<Object>> data = importVATExceptionFromDBF(context, fileBytes);

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        ImportField codeCustomsGroupField = new ImportField(LM.findLCPByCompoundName("codeCustomsGroup"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsGroup"),
                LM.findLCPByCompoundName("customsGroupCode").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idVATCustomsExceptionField = new ImportField(LM.findLCPByCompoundName("idVATCustomsException"));
        ImportKey<?> VATCustomsExceptionKey = new ImportKey((CustomClass) LM.findClassByCompoundName("VATCustomsException"),
                LM.findLCPByCompoundName("VATCustomsExceptionId").getMapping(idVATCustomsExceptionField));
        keys.add(VATCustomsExceptionKey);
        props.add(new ImportProperty(idVATCustomsExceptionField, LM.findLCPByCompoundName("idVATCustomsException").getMapping(VATCustomsExceptionKey)));
        fields.add(idVATCustomsExceptionField);

        ImportField nameVATCustomsExceptionField = new ImportField(LM.findLCPByCompoundName("nameVATCustomsException"));
        props.add(new ImportProperty(nameVATCustomsExceptionField, LM.findLCPByCompoundName("nameVATCustomsException").getMapping(VATCustomsExceptionKey)));
        fields.add(nameVATCustomsExceptionField);

        ImportField rangeField = new ImportField(LM.findLCPByCompoundName("dataValueSupplierVATCustomsGroupDate"));
        ImportKey<?> rangeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(rangeField));
        props.add(new ImportProperty(rangeField, LM.findLCPByCompoundName("rangeVATCustomsException").getMapping(VATCustomsExceptionKey),
                LM.object(LM.findClassByCompoundName("Range")).getMapping(rangeKey)));
        props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("customsGroupVATCustomsException").getMapping(VATCustomsExceptionKey),
                LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(rangeField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, LM.findLCPByCompoundName("dateFromVATCustomsException").getMapping(VATCustomsExceptionKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, LM.findLCPByCompoundName("dateToVATCustomsException").getMapping(VATCustomsExceptionKey)));
        fields.add(dateToCustomsGroupField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context.getBL());
        session.close();
    }

    private List<List<Object>> importVATExceptionFromDBF(ExecutionContext context, byte[] fileBytes) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        Set<String> tnvedSet = getTNVEDSet(context);

        File tempFile = File.createTempFile("tempTnved", ".dbf");
        IOUtils.putFileBytes(tempFile, fileBytes);

        DBF file = new DBF(tempFile.getPath());

        List<List<Object>> data = new ArrayList<List<Object>>();
        Map<String, List<Object>> dataVATMap = new HashMap<String, List<Object>>();

        int recordCount = file.getRecordCount();
        for (int i = 1; i <= recordCount; i++) {
            file.read();

            Integer type = Integer.parseInt(new String(file.getField("PP").getBytes(), "Cp866").trim());
            String codeCustomsGroup = new String(file.getField("G33").getBytes(), "Cp866").trim();
            String name = new String(file.getField("TEXT").getBytes(), "Cp866").trim();
            BigDecimal stav1 = new BigDecimal(new String(file.getField("STAV1").getBytes(), "Cp866").trim());
            Date dateFrom = new Date(DateUtils.parseDate(new String(file.getField("DATE1").getBytes(), "Cp866").trim(), new String[]{"yyyyMMdd"}).getTime());
            Date dateTo = new Date(DateUtils.parseDate(new String(file.getField("DATE2").getBytes(), "Cp866").trim(), new String[]{"yyyyMMdd"}).getTime());

            if (type.equals(4)) {
                if(codeCustomsGroup.length()==10)
                    data.add(Arrays.asList((Object) codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo) + name, name, stav1, dateFrom, dateTo));
                else
                    dataVATMap.put(codeCustomsGroup, Arrays.asList((Object) codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo) + name, name, stav1, dateFrom, dateTo));
            }
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

    private Set<String> getTNVEDSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Set<String> tnvedSet = new HashSet<String>();

        LCP<?> isCustomsGroup = LM.is(getClass("CustomsGroup"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isCustomsGroup.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("codeCustomsGroup", getLCP("codeCustomsGroup").getExpr(context.getModifier(), key));
        query.and(isCustomsGroup.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.values()) {
            String tnved = (String) entry.get("codeCustomsGroup");
            if (tnved != null && tnved.trim().length() == 10)
                tnvedSet.add(tnved.trim());
        }
        return tnvedSet;
    }
}