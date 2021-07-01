package lsfusion.erp.region.by.mila;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.io.FileUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Iterator;

public class ImportMilaAction extends InternalAction {

    StringBuilder cResult = new StringBuilder();    // Строка в формате JSON - результат работы
    String errMsg = "";                             // Текст сообщений об ошибках
    String baseUrl = "https://mila.by";             // Базовый URL для подстановки
    boolean addLog = true;                          // Включает дополнительное логирование в файл addLogFile
    String addLogFile = "logs/import_mila.log";     // Имя файла дополнительного логирования
    int maxPage = 0;                                // Ограничение на количество принимаемых страниц в группе, 0 - все
    int maxGoods = 0;                               // Ограничение на количество товаров на странице, 0 - все
    ioFile ioFile = new ioFile();                   // Для записи результатов
    Long nTimeStamp = null;                         // Начало временной метки для доп. лог файла

    public ImportMilaAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            ioFile.lAdd = false;
            File tmpFile = File.createTempFile("mila_js", ".txt");
            try {
                saveResult(tmpFile);
                saveLog(0,"","",0);
                ioFile.lAdd = true;
                if (!getGroups(baseUrl, tmpFile)) {
                    ERPLoggers.importLogger.error(errMsg);
                }
                saveResult(tmpFile);
                findProperty("importMilaFile[]").change(new FileData(new RawFileData(tmpFile), "json"), context);
            } finally {
                tmpFile.deleteOnExit();
            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    // временно - сохраняет офлайн, для последующего использования
    void saveDoc(String url, String fName) {
        readSite oRs = new readSite();
        oRs.loadUrl(url);
        oRs.saveDoc(fName);
    }

    // сохраняет лог
    void saveLog(Integer nLevel,String cText,String cUrl, Integer flag) {
        if (!addLog) return; // дополнительное логирование отменено
        String ch ="", ch2 = "";
        if (flag != 0) ch = new String(new char[nLevel * 4]).replace("\0"," ");
        ch2 = ch;
        ioFile log = new ioFile();
        ioFile.lAdd = true;
        if (flag == 0) {
            try {
                File fLog = new File(addLogFile);
                if (fLog.exists()) fLog.delete();
            } catch (Exception e) {
                // ничего делать не будем
            }
            cText = "Старт: " + new Timestamp(System.currentTimeMillis()) + "\n---------------------------------------";
        }
        switch (flag) {
            case 1: {
                nTimeStamp = new Timestamp(System.currentTimeMillis()).getTime();
                ch = ch.substring(0, ch.length() - 2) + ":";
                break;
            }
            case 2: {
                Double nResult = (double) (new Timestamp(System.currentTimeMillis()).getTime() - nTimeStamp) / 1000;
                cText += ", " + nResult.toString();
                break;
            }
        }
        log.strToFile(ch + cText + "\n",addLogFile);
        if (cUrl.length() > 0) log.strToFile(ch2 + cUrl + "\n",addLogFile);
    }

    //  Парсировка товарных групп
    private boolean getGroups(String url, File tmpFile) throws IOException {
        boolean lRet = true;
        String c_class, c_url;
        readSite oRS = new readSite();
        saveLog(1,"Получение списка групп", url,1);
        if (!oRS.loadUrl(url+"/catalog/")) return errbox(oRS.errMsg);
        saveLog(1,"getGroups, ok", "", 2);
        addKeyValue("{", "title", oRS.doc.title(), ",", true);
        addKeyValue("", "groups", "", "[\n", true);
        int i = 0;
        for (Element item : oRS.doc.getElementsByClass("contact-item")) {
            c_url = getItemValue(item, "a", "href", null);
            if (c_url.length() < 2) continue;  // так не должно быть, но на всякий случай
            c_url = baseUrl + c_url;
            i += 1;
            if (i > 1) cResult.append(",\n");
            addKeyValue("{", "gname", item.text(), ",", true);
            addKeyValue("", "url", c_url, ",", true);
            addKeyValue("", "goods", "", "[\n", true);
            saveResult(tmpFile);
            // вызываем поиск максимального количеста страниц, принадлежащих группе
            saveLog(2,"Получение списка страниц группы", c_url, 1);
            lRet = getPagesGoods(c_url, tmpFile); // c_url
            cResult.append("]}");   // закрываем goods
            saveResult(tmpFile);
            if (!lRet) break;       // даже если ошибка, то все равно массив группы будет закрыт
        }
        cResult.append("]}"); // закрываем groups
        saveResult(tmpFile);
        return lRet;
    }

    //  Вызов первой страницы с товарами.
    //  Метод запустит последовательную обработку всех последующих страниц группы
    private boolean getPagesGoods(String url, File tmpFile) throws IOException {
        boolean lRet = true;
        int n1 = -1, n2 = 1;
        String c_url, c_num;
        readSite oRS = new readSite();
        if (!oRS.loadUrl(url)) return errbox(oRS.errMsg);
        saveLog(2,"getPagesGoods, ok", "", 2);
        for (Element item : oRS.doc.getElementsByClass("pagination")) {
            c_num = item.text();
            n1 = str2Int(c_num);
            if (n1 > n2) n2 = n1;
        }
        for (int i = 1; i <= n2; ++i) {
            if ((maxPage > 0) && (i > maxPage)) break;
            c_url = url + "?page=" + i;
            int attempt = 0;
            do {
                attempt++;
                if (attempt > 10) break;
                ERPLoggers.importLogger.info(c_url);
                saveLog(3,"Чтение списка товаров группы", c_url, 1);
                lRet = getGoods(c_url, i, tmpFile);
            } while (!lRet);
        }
        return lRet;
    }

    //  Парсировка товаров
    private boolean getGoods(String url, int nPage, File tmpFile) throws IOException {
        boolean lRet = true;
        String c_class, c_url, c_name;
        readSite oRS = new readSite();
        if (!oRS.loadUrl(url)) return errbox(oRS.errMsg);
        saveLog(3,"getGoods, ok", "", 2);
        int i = 0;
        for (Element item : oRS.doc.getElementsByClass("showcase-element")) {
            i += 1;
            if ((maxGoods > 0) && (i > maxGoods)) break;
            if ((i == 1) && (nPage > 1) && (cResult.length() < 2 || cResult.charAt(cResult.length() - 2) != ',')) cResult.append(",\n");
            if (i > 1 && (cResult.length() < 2 || cResult.charAt(cResult.length() - 2) != ',')) cResult.append(",\n");
            c_url = getItemValue(item, "a", "href", null);
            if (c_url.length() > 0) {
                c_url = baseUrl + c_url;
                saveLog(4,"Получение расширенной информации по товару", c_url, 1);
                lRet = getProduct(c_url); // расширенная информация по товару
                if (lRet) saveResult(tmpFile);
            } else {
                lRet = errbox("getGoods: URL товара не найден");
            }
            if (!lRet) break;
        }
        return lRet;
    }

    //  Получаем расширенную информацию по конкретному товару
    private boolean getProduct(String url) {
        readSite oRS = new readSite();
        if (!oRS.loadUrl(url)) return errbox(oRS.errMsg);
        saveLog(4,"getProduct, ok", "", 2);
        addKeyValue("{","tname",oRS.doc.getElementsByTag("h1").first().text(),",", true);
        addKeyValue("","tcode",oRS.doc.getElementsByAttribute("data-product-id").first().attr("data-product-id"),",", true);
        addKeyValue("","barcode",oRS.doc.getElementsByClass("ean").first().child(1).text(),",", true);
        addKeyValue("","url",url,"}", true);

        return true;
    }

    //  Возвращает значение элемента, как текст. Используется, чтобы не бросать Exception
    private String getItemValue(Element item, String cExpr, String cAtr, Integer nIndex) {
        Elements items = item.select(cExpr);
        if (items.size() == 0) return " ";                         // такая ветка не существует
        if (nIndex == null) {                                      // только одно значение
            if (cAtr.length() == 0) return items.text() + " ";
            return items.attr(cAtr);
        } else {                                                   // несколько значений, поэтому выбираем по индексу
            if (nIndex <= items.size() - 1) {
                if (cAtr.length() == 0) return items.get(nIndex).text() + " ";
                return items.get(nIndex).attr(cAtr);
            }
        }
        return " ";
    }

    private String getPriceValue(Element item, String cExpr, String cAtr, Integer nIndex) {
        String result = getItemValue(item, cExpr, cAtr, nIndex);
        if (result == null || result.trim().isEmpty())
            result = "0.00";
        return result;
    }

    private void print(String cMsg) {
        ERPLoggers.importLogger.info(cMsg);
    }

    //  Возвращает выражение цены
    private String getPrice(String price) {
        String cRet = "0.00";
        if (price.contains("руб.")) price = price.substring(0, price.length() - 5);
        price = price.trim();
        if (price.length() > 2)
            cRet = price.substring(0, price.length() - 2) + "." + price.substring(price.length() - 2);
        return cRet.trim();
    }

    //  Конструктур JSON выражений
    private void addKeyValue(String ch1, String key, String value, String ch2, boolean quote) {
        cResult.append(ch1);
        cResult.append("\"").append(key).append("\":");
        if (value.length() > 0) {
            value = value.replace("\"", "'").replace("\\","/");
            if (quote) cResult.append("\"");
            cResult.append(value);
            if (quote) cResult.append("\"");
        }
        cResult.append(ch2);
    }

    //  Переводит строку в Integer
    private int str2Int(String cValue) {
        int nRet = 0;
        cValue = cValue.trim();
        if (cValue.length() == 0) return nRet;
        try {
            nRet = Integer.parseInt(cValue);
        } catch (Exception e) {
            nRet = 0;
        }
        return nRet;
    }

    //  Сохраняет результат
    private void saveResult(File tmpFile) throws IOException {
        FileUtils.writeStringToFile(tmpFile, cResult.toString(), true);
        cResult = new StringBuilder();
    }

    //  Обработка ошибок
    private boolean errbox(String cMsg) {
        errMsg = cMsg;
        return false;
    }

    // ======================================
// Класс парсеровки сайта: через URL или локально, из файла
    class readSite {
        Document doc = null;
        Integer timeout = 100000;
        String cAgent = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36";
        String errMsg = "Неизвестная ошибка";

        //  Производит чтение страницы, по выбранному URL
        boolean loadUrl(String url) {
            try {
                Connection connection = Jsoup.connect(url);
                connection.timeout(timeout);
                connection.userAgent(cAgent);
                doc = connection.get();
                return true;
            } catch (Exception e) {
                errBox(e.getMessage());
            }
            return false;
        }

        // Записывает ранее считанную страницу из WEB в текстовый документ
        boolean saveDoc(String fileName) {
            boolean lRet = true;
            if (doc == null) return errBox("Документ не существует");
            try {
                ioFile o1 = new ioFile();
                o1.lAdd = false;
                o1.strToFile(doc.outerHtml(), fileName);
            } catch (Exception e) {
                lRet = errBox(e.getMessage());
            }
            return lRet;
        }

        // Загружает из файла html страницу и создает объект doc
        boolean loadDoc(String fileName) {
            boolean lRet = true;
            String cPage;
            ioFile o1 = new ioFile();
            doc = null;
            if (!o1.fileToStr(fileName)) {
                return errBox(o1.errMsg);
            }
            try {
                doc = Jsoup.parse(o1.cResult, "UTF8");
                if (doc == null) lRet = errBox("Ошибка загрузки документа");
            } catch (Exception e) {
                lRet = errBox(e.getMessage());
            }
            return lRet;
        }

        // Обработчик ошибок
        private boolean errBox(String cMsg) {
            errMsg = cMsg;
            return false;
        }
    }

    // ======================================
// Класс для записи или чтения из файла
    class ioFile {
        String errMsg;
        String cResult;
        boolean lAdd = true;

        // Запись текстовой строки в текстовый файл
        boolean strToFile(String cText, String fileName) {
            boolean lRet = true;
            try {
                FileWriter ob = new FileWriter(fileName, lAdd);
                ob.append(cText);
                ob.close();
            } catch (Exception e) {
                lRet = errBox(e.getMessage());
            }
            return lRet;
        }

        // Чтение файл в текстовый буфер cResult
        boolean fileToStr(String fileName) {
            cResult = "";
            boolean lRet = true;
            try {
                FileReader ob = new FileReader(fileName);
                int nch;
                while ((nch = ob.read()) != -1) {
                    cResult += ((char) nch);
                }
            } catch (Exception e) {
                lRet = errBox(e.getMessage());
                cResult = "";
            }
            return lRet;
        }

        // Обработчик ошибок
        boolean errBox(String cMsg) {
            errMsg = cMsg;
            return false;
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}
