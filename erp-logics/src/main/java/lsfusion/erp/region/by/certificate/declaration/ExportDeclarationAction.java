package lsfusion.erp.region.by.certificate.declaration;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultExportAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;

public class ExportDeclarationAction extends DefaultExportAction {
    private final ClassPropertyInterface declarationInterface;
    String row;

    public ExportDeclarationAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        declarationInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            List<String> exportNames = BaseUtils.toList("numberDeclarationDetail", "nameCustomsDeclarationDetail",
                    "codeCustomsGroupDeclarationDetail", "sidCountryDeclarationDetail", "idUOMDeclarationDetail",
                    "shortNameUOMDeclarationDetail", "sidOrigin2CountryDeclarationDetail", "quantityDeclarationDetail",
                    "sumDeclarationDetail", "sumNetWeightDeclarationDetail", "sumGrossWeightDeclarationDetail");
            LP[] exportProperties = findProperties("number[DeclarationDetail]", "nameCustoms[DeclarationDetail]",
                    "codeCustomsGroup[DeclarationDetail]", "sidCountry[DeclarationDetail]", "idUOM[DeclarationDetail]",
                    "shortNameUOM[DeclarationDetail]", "sidOrigin2Country[DeclarationDetail]", "quantity[DeclarationDetail]",
                    "sum[DeclarationDetail]", "sumNetWeight[DeclarationDetail]", "sumGrossWeight[DeclarationDetail]");

            List<String> exportTitlesTSware = BaseUtils.toList("Порядковый номер декларируемого товара", "Наименование товара", "Вес брутто",
                    "Вес нетто", "Вес нетто без упаковки", "Фактурная стоимость товара", "Таможенная стоимость",
                    "Статистическая стоимость", "Код товара по ТН ВЭД ТС", "Запреты и ограничения", "Интеллектуальная собственность",
                    "Цифровой код страны происхождения товара", "Буквенный код страны происхождения товара",
                    "Код метода определения таможенной стоимости", "Название географического пункта",
                    "Код условий поставки по Инкотермс", "Код вида поставки товаров", "Количество мест",
                    "Вид грузовых мест", "Код валюты квоты", "Остаток квоты в валюте", "Остаток квоты в единице измерения",
                    "Код единицы измерения квоты", "Наименование единицы измерения квоты",
                    "Количество товара в специфических единицах измерения", "Количество подакцизного товара",
                    "Код специфических единиц измерения", "Краткое наименование специфических единиц измерения",
                    "Код единицы измерения подакцизного товара", "Наименование единицы измерения подакцизного товара",
                    "Количество товара в дополнительных единицах измерения", "Код дополнительной единицы измерения",
                    "Наименование дополнительной единицы измерения",
                    "Корректировки таможенной стоимости", "Количество акцизных марок", "Код предшествующей таможенной процедуры",
                    "Преференция код 1", "Преференция код 2", "Преференция код 3", "Преференция код 4",
                    "Код особенности перемещения товаров", "Запрашиваемый срок переработки", "Номер документа переработки",
                    "Дата документа переработки", "Место проведения операций переработки", "Количество товара переработки",
                    "Код единицы измерения количества товара переработки", "Краткое наименование единицы измерения количества товара переработки",
                    "Почтовый индекс организации осуществлявшей переработку", "Код страны переработки", "Наименование страны переработки",
                    "Наименование региона переработки", "Наименование населенного пункта переработки", "Улица и дом переработки",
                    "Наименование лица (отправителя) переработки", "УНП лица (отправителя) переработки",
                    "Почтовый индекс лица (отправителя) переработки", "Наименование региона лица (отправителя) переработки",
                    "Населенный пункт лица (отправителя) переработки", "Улица и дом лица (отправителя) переработки",
                    "Код страны регистрации лица (отправителя) переработки", "Название страны регистрации лица (отправителя) переработки",
                    "Номер документа, удостоверяющего личность физического лица  переработки", "Идентификационный номер физического лица переработки",
                    "Дата выдачи документа, удостоверяющего личность физического лица переработки",
                    "Код документа, удостоверяющего личность физического лица переработки");

            List<String> exportTitlesTSmarkings = BaseUtils.toList(
                    "Номер товара", "Наименование изготовителя", "Товарный знак", "Марка товара", "Модель товара", "Артикул товара",
                    "Стандарт (ГОСТ, ОСТ, СПП, СТО, ТУ)", "Сорт (группа сортов)", "Дата выпуска", "Количество товара",
                    "Краткое наименование единицы измерения", "Код единицы измерения", "Группа товаров");

            List<String> exportTitlesTSDocs44 = BaseUtils.toList("Номер товара", "Номер документа", "Дата документа",
                    "Код таможенного органа", "Код вида представляемого документа", "Дата начала действия документа",
                    "Дата окончания действия документа", "Дата представления недостающего документа", "Код срока временного ввоза",
                    "Заявляемый срок временного ввоза", "Код вида платежа (льготы)", "ОПЕРЕЖАЮЩАЯ ПОСТАВКА",
                    "Запрашиваемый срок переработки", "Код страны (сертификат происхождения)", "Код вида упрощений (реестр УЭО)", "Наименование документа");

            DataObject declarationObject = context.getDataKeyValue(declarationInterface);

            Map<String, RawFileData> files = new HashMap<>();
            File fileTSware = File.createTempFile("TSware", ".csv");
            PrintWriter writerTSware = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileTSware), "windows-1251"));
            File fileTSMarkings = File.createTempFile("TSmarkings", ".csv");
            PrintWriter writerTSmarkings = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileTSMarkings), "windows-1251"));
            File fileTSDocs44 = File.createTempFile("TSDocs44", ".csv");
            PrintWriter writerTSDocs44 = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(fileTSDocs44), "windows-1251"));

            row = "";
            for (String title : exportTitlesTSware)
                addStringCellToRow(title, ";");
            writerTSware.println(row);

            row = "";
            for (String title : exportTitlesTSmarkings)
                addStringCellToRow(title, ";");
            writerTSmarkings.println(row);

            row = "";
            for (String title : exportTitlesTSDocs44)
                addStringCellToRow(title, ";");
            writerTSDocs44.println(row);

            LP<?> isDeclarationDetail = is(findClass("DeclarationDetail"));
            ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isDeclarationDetail.getMapKeys();
            KeyExpr key = keys.singleValue();
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            for (int j = 0; j < exportProperties.length; j++) {
                query.addProperty(exportNames.get(j), exportProperties[j].getExpr(context.getModifier(), key));
            }
            query.and(isDeclarationDetail.getExpr(key).getWhere());
            query.and(findProperty("declaration[DeclarationDetail]").getExpr(context.getModifier(), key).compare(declarationObject.getExpr(), Compare.EQUALS));
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

            TreeMap<Integer, Map<String, Object>> sortedRows = new TreeMap<>();

            for (int i=0,size=result.size();i<size;i++) {
                ImMap<Object, Object> values = result.getValue(i);
                Map<String, Object> valuesRow = new HashMap<>();
                for (String propertySID : exportNames)
                    valuesRow.put(propertySID, values.get(propertySID));
                valuesRow.put("declarationDetailID", result.getKey(i).getValue(0));
                sortedRows.put((Integer)values.get("numberDeclarationDetail"), valuesRow);
            }

            row = "";
            addConstantStringCellToRow("1", ";");
            addStringCellToRow(null, ";");
            addStringCellToRow(null, ";");
            writerTSDocs44.println(row);
            for (int i = 0; i < 9; i++) {
                writerTSDocs44.println("");
            }

            for (Map.Entry<Integer, Map<String, Object>> entry : sortedRows.entrySet()) {

                //Creation of TSDocs44.csv
                KeyExpr invoiceExpr = new KeyExpr("invoice");
                ImRevMap<Object, KeyExpr> invoiceKeys = MapFact.singletonRev("invoice", invoiceExpr);

                QueryBuilder<Object, Object> invoiceQuery = new QueryBuilder<>(invoiceKeys);
                invoiceQuery.addProperty("seriesNumberInvoice", findProperty("seriesNumber[Purchase.Invoice]").getExpr(invoiceExpr));
                invoiceQuery.addProperty("dateInvoice", findProperty("date[Purchase.Invoice]").getExpr(invoiceExpr));

                invoiceQuery.and(findProperty("in[DeclarationDetail,UserInvoice]").getExpr(new DataObject((Long)entry.getValue().get("declarationDetailID"), (ConcreteCustomClass) findClass("DeclarationDetail")).getExpr(), invoiceExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> invoiceResult = invoiceQuery.execute(context);


                for (ImMap<Object, Object> invoiceValues : invoiceResult.valueIt()) {
                    row = "";
                    addStringCellToRow(entry.getKey(), ";");//numberDeclarationDetail
                    addStringCellToRow(invoiceValues.get("seriesNumberInvoice"), ";");
                    addStringCellToRow(invoiceValues.get("dateInvoice"), ";");
                    addStringCellToRow(null, ";");
                    addConstantStringCellToRow("04021", ";");

                    writerTSDocs44.println(row);
                }

                //Creation of TSware.csv
                row = "";
                Map<String, Object> values = entry.getValue();
                addStringCellToRow(entry.getKey(), ";"); //numberDeclarationDetail

                addStringCellToRow(values.get("nameCustomsDeclarationDetail"), ";");

                addNumericCellToRow(values.get("sumGrossWeightDeclarationDetail"), ";", 3);
                addNumericCellToRow(values.get("sumNetWeightDeclarationDetail"), ";", 3);
                addNumericCellToRow(values.get("sumNetWeightDeclarationDetail"), ";", 3); //Вес нетто без упаковки
                addNumericCellToRow(values.get("sumDeclarationDetail"), ";", 7);
                addStringCellToRow(null, ";"); //Таможенная стоимость
                addStringCellToRow(null, ";"); //Статистическая стоимость
                addStringCellToRow(values.get("codeCustomsGroupDeclarationDetail"), ";");
                addStringCellToRow(null, ";"); //Запреты и ограничения
                addStringCellToRow(null, ";"); //Интеллектуальная собственность
                addStringCellToRow(values.get("sidCountryDeclarationDetail"), ";");
                addStringCellToRow(values.get("sidOrigin2CountryDeclarationDetail"), ";");
                addConstantStringCellToRow("1", ";"); //Код метода определения таможенной стоимости
                addStringCellToRow(null, ";"); //Название географического пункта
                addStringCellToRow(null, ";"); //Код условий поставки по Инкотермс
                addStringCellToRow(null, ";"); //Код вида поставки товаров
                addStringCellToRow(null, ";"); //Количество мест
                addStringCellToRow(null, ";"); //Вид грузовых мест
                addStringCellToRow(null, ";"); //Код валюты квоты
                addStringCellToRow(null, ";"); //Остаток квоты в валюте
                addStringCellToRow(null, ";"); //Остаток квоты в единице измерения
                addStringCellToRow(null, ";"); //Код единицы измерения квоты
                addStringCellToRow(null, ";"); //Наименование единицы измерения квоты
                addStringCellToRow(null, ";"); //Количество товара в специфических единицах измерения
                addStringCellToRow(null, ";"); //Количество подакцизного товара
                addConstantStringCellToRow("166", ";");  //Код специфических единиц измерения
                addConstantStringCellToRow("КГ", ";");  //Краткое наименование специфических единиц измерения
                addStringCellToRow(null, ";"); //Код единицы измерения подакцизного товара
                addStringCellToRow(null, ";"); //Наименование единицы измерения подакцизного товара
                addNumericCellToRow(values.get("quantityDeclarationDetail"), ";", 0);
                addStringCellToRow(null, ";");
                addStringCellToRow(null, ";");

                addStringCellToRow(null, ";"); //Корректировки таможенной стоимости
                addStringCellToRow(null, ";"); //Количество акцизных марок
                addConstantStringCellToRow("00", ";"); //Код предшествующей таможенной процедуры
                addConstantStringCellToRow("ОО", ";"); //Преференция код 1
                addConstantStringCellToRow("ОО", ";"); //Преференция код 2
                addConstantStringCellToRow("-", ";"); //Преференция код 3
                addConstantStringCellToRow("ОО", ";"); //Преференция код 4
                addConstantStringCellToRow("000", ";"); //Код особенности перемещения товаров
                addStringCellToRow(null, ";"); //Запрашиваемый срок переработки
                addStringCellToRow(null, ";"); //Номер документа переработки
                addStringCellToRow(null, ";"); //Дата документа переработки
                addStringCellToRow(null, ";"); //Место проведения операций переработки
                addStringCellToRow(null, ";"); //Количество товара переработки
                addStringCellToRow(null, ";"); //Код единицы измерения количества товара переработки
                addStringCellToRow(null, ";"); //Краткое наименование единицы измерения количества товара переработки
                addStringCellToRow(null, ";"); //Почтовый индекс организации осуществлявшей переработку
                addStringCellToRow(null, ";"); //Код страны переработки
                addStringCellToRow(null, ";"); //Наименование страны переработки
                addStringCellToRow(null, ";"); //Наименование региона переработки
                addStringCellToRow(null, ";"); //Наименование населенного пункта переработки
                addStringCellToRow(null, ";"); //Улица и дом переработки
                addStringCellToRow(null, ";"); //Наименование лица (отправителя) переработки
                addStringCellToRow(null, ";"); //УНП лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Почтовый индекс лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Наименование региона лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Населенный пункт лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Улица и дом лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Код страны регистрации лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Название страны регистрации лица (отправителя) переработки
                addStringCellToRow(null, ";"); //Номер документа, удостоверяющего личность физического лица  переработки
                addStringCellToRow(null, ";"); //Идентификационный номер физического лица переработки
                addStringCellToRow(null, ";"); //Дата выдачи документа, удостоверяющего личность физического лица переработки
                addStringCellToRow(null, ";"); //Код документа, удостоверяющего личность физического лица переработки

                writerTSware.println(row);

                //Creation of TSmarkings.csv
                row = "";
                addStringCellToRow(entry.getKey(), ";"); //numberDeclarationDetail
                addConstantStringCellToRow("ПРОИЗВОДИТЕЛЬ НЕИЗВЕСТЕН", ";"); //Наименование изготовителя
                //addStringCellToRow(null, ";");
                addStringCellToRow(null, ";"); //Товарный знак
                addStringCellToRow(null, ";"); //Марка товара
                addStringCellToRow(null, ";"); //Модель товара
                addStringCellToRow(null, ";"); //Артикул товара
                addStringCellToRow(null, ";"); //Стандарт (ГОСТ, ОСТ, СПП, СТО, ТУ)
                addStringCellToRow(null, ";"); //Сорт (группа сортов)
                addStringCellToRow(null, ";"); //Дата выпуска
                addNumericCellToRow(values.get("quantityDeclarationDetail"), ";", 0); //Количество товара
                addStringCellToRow(values.get("shortNameUOMDeclarationDetail"), ";"); //Краткое наименование единицы измерения
                addStringCellToRow(values.get("idUOMDeclarationDetail"), ";"); //Код единицы измерения
                addStringCellToRow(null, ";"); //Группа товаров

                writerTSmarkings.println(row);
            }

            writerTSware.close();
            writerTSmarkings.close();
            writerTSDocs44.close();

            files.put("TSware.csv", new RawFileData(fileTSware));
            safeFileDelete(fileTSware);
            files.put("TSmarkings.csv", new RawFileData(fileTSMarkings));
            safeFileDelete(fileTSMarkings);
            files.put("TSDocs44.csv", new RawFileData(fileTSDocs44));
            safeFileDelete(fileTSDocs44);
            context.delayUserInterfaction(new ExportFileClientAction(files));

        } catch (IOException | SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    public void addCellToRow(Object cell, Boolean isNumeric, Integer precision, String prefix, String separator, Boolean useSeparatorIfNull) {
        if (cell != null) {
            if (prefix != null)
                row += prefix;
            if (!isNumeric)
                row += cell.toString().trim();
            else {
                String bigDecimal = new BigDecimal(cell.toString()).setScale(precision, RoundingMode.HALF_DOWN).toString().replace('.', ',');
                while (bigDecimal.endsWith("0") && bigDecimal.length() > 1)
                    bigDecimal = bigDecimal.substring(0, bigDecimal.length() - 1);
                row += bigDecimal;
            }
            row += separator;
        } else if (useSeparatorIfNull)
            row += separator;
    }

    public void addStringCellToRow(Object cell, String separator) {
        addCellToRow(cell, false, null, null, separator, true);
    }

    public void addNumericCellToRow(Object cell, String separator, int precision) {
        addCellToRow(cell, true, precision, null, separator, true);
    }

    public void addConstantStringCellToRow(String constant, String separator) {
        row += constant + separator;
    }

}
