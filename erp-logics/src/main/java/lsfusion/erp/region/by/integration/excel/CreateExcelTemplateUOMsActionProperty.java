package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;

public class CreateExcelTemplateUOMsActionProperty extends CreateExcelTemplateActionProperty {

    public CreateExcelTemplateUOMsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Pair<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importUOMsTemplate",
                Arrays.asList("Код ед.изм.", "Ед.изм.", "Краткая ед.изм."),
                Arrays.asList(Arrays.asList("UOM1", "Штука", "шт")));
    }
}