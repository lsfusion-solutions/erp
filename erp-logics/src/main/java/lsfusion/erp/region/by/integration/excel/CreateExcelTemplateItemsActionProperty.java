package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;

public class CreateExcelTemplateItemsActionProperty extends CreateExcelTemplateActionProperty {

    public CreateExcelTemplateItemsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Pair<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importItemsTemplate",
                Arrays.asList("Код товара", "Код группы", "Наименование",
                        "Код ед.изм.", "Название бренда", "Код бренда", "Страна", "Штрихкод", "Дата", "Весовой",
                        "Вес нетто", "Вес брутто", "Состав", "НДС, %", "Код посуды", "Цена посуды", "НДС посуды, %",
                        "Код нормы отходов", "Оптовая наценка", "Розничная наценка", "Кол-во в упаковке"),
                Arrays.asList(Arrays.asList("1111", "2222", "Товар 1", "UOM1", "HugoBoss", "B1", "Беларусь",
                        "481011200174", "01.01.2012", "1", "0,1", "0,1", "100% cotton", "20", "", "", "", "RW1", "30", "20", "12")));
    }
}