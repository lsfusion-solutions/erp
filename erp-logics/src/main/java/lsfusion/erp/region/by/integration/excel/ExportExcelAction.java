package lsfusion.erp.region.by.integration.excel;

import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteClientAction;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public abstract class ExportExcelAction extends DefaultIntegrationAction {
    
    public abstract Pair<String, RawFileData> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException;

    public static RawFileData createFile(List<String> columns, List<List<String>> rows) throws IOException, WriteException {
        File file = File.createTempFile("export", ".xls");
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

        RawFileData result = new RawFileData(file);
        if(!file.delete())
            file.deleteOnExit();
        return result;
    }

    public ExportExcelAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            Pair<String, RawFileData> fileEntry = createFile(context);
            context.delayUserInterfaction(new WriteClientAction(fileEntry.second, fileEntry.first, "xls", false, true));
        } catch (IOException | WriteException e) {
            throw new RuntimeException(e);
        }
    }

    protected String formatValue(Object value) {
        if (value == null) return "";
        return String.valueOf(value);
    }
}