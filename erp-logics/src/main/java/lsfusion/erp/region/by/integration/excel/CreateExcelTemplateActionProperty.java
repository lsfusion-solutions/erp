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
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public abstract class CreateExcelTemplateActionProperty extends InternalAction {

    public abstract Pair<String, RawFileData> createFile() throws IOException, WriteException;

    public static Pair<String, RawFileData> createFile(String fileName, List<String> columns, List<List<String>> defaultRows) throws IOException, WriteException {
        File file = null;
        try {
            file = File.createTempFile(fileName, ".xls");

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

            for (int j = 0; j < defaultRows.size(); j++)
                for (int i = 0; i < defaultRows.get(j).size(); i++) {
                    sheet.addCell(new jxl.write.Label(i, j + 1, defaultRows.get(j).get(i)));
                }

            workbook.write();
            workbook.close();
        } finally {
            if (file != null && !file.delete()) {
                file.deleteOnExit();
            }
        }
        return Pair.create(fileName, new RawFileData(file));
    }

    public CreateExcelTemplateActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            Pair<String, RawFileData> fileEntry = createFile();
            context.delayUserInterfaction(new WriteClientAction(fileEntry.second, fileEntry.first, "xls", false, true));
        } catch (IOException | WriteException e) {
            throw new RuntimeException(e);
        }
    }
}