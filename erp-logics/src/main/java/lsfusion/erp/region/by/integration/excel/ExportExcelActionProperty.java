package lsfusion.erp.region.by.integration.excel;

import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import lsfusion.base.IOUtils;
import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ExportExcelActionProperty extends DefaultIntegrationActionProperty {
    
    public abstract Map<String, byte[]> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException;

    public static Map<String, byte[]> createFile(String fileName, List<String> columns, List<List<String>> rows) throws IOException, WriteException {
        File file = File.createTempFile(fileName, ".xls");
        WorkbookSettings ws = new WorkbookSettings();
        ws.setGCDisabled(true);
        WritableWorkbook workbook = Workbook.createWorkbook(file, ws);
        WritableSheet sheet = workbook.createSheet("List 1", 0);
        CellView cv = new CellView();
        cv.setAutosize(true);

        for (int i = 0; i < columns.size(); i++) {
            sheet.addCell(new jxl.write.Label(i, 0, columns.get(i)));
            sheet.setColumnView(i, cv);
        }

        for (int j = 0; j < rows.size(); j++)
            for (int i = 0; i < rows.get(j).size(); i++) {
                sheet.addCell(new jxl.write.Label(i, j + 1, rows.get(j).get(i)));
            }

        workbook.write();
        workbook.close();

        Map<String, byte[]> result = new HashMap<>();
        result.put(fileName + ".xls", IOUtils.getFileBytes(file));
        file.delete();

        return result;
    }

    public ExportExcelActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            context.delayUserInterfaction(new ExportFileClientAction(createFile(context)));
        } catch (IOException | WriteException e) {
            throw new RuntimeException(e);
        }
    }

    protected String formatValue(Object value) {
        if (value == null) return "";
        return String.valueOf(value);
    }
}