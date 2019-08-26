package equ.clt.handler.dreamkas;

import java.io.FileReader;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class JsonReadProcess {
    public String eMessage = "";
    public int nCount = 0;
    public int nSize = 0;
    private JSONParser parser = new JSONParser();
    private Object ojs = null;
    //  Возвращаемые типы
    public String cResult = "";            // Строка всегда
    public String tResult = "";            // Возвращаемый тип

    //  Загружаем строку JSON
    public boolean load(String cJson) {
        clsProp();
        boolean lRet = true;
        try {
            ojs = null;
            nCount = 0;
            nSize = 0;
            parser.reset();
            ojs = parser.parse(cJson);
            tResult = getObjType(ojs);
            if (tResult.equals("Array")) {
                JSONArray ob = (JSONArray) ojs;
                nCount = ob.size();
                nSize = nCount;
            } else {
                if (!tResult.equals("Object")) {
                    lRet = errBox("Ошибка структуры JSON");
                } else {
                    JSONObject ob = (JSONObject) ojs;
                    nSize = ob.size();
                }
            }
        } catch (Exception e) {
            lRet = errBox("Ошибка структуры JSON\n" + e.getMessage());
        }
        return lRet;
    }

    //  Загружаем JSON из файла
    public boolean loadFromFile(String cFName) {
        clsProp();
        boolean lRet;
        StringBuilder cJson = new StringBuilder();
        try {
            FileReader ob = new FileReader(cFName);
            int nch;
            while ((nch = ob.read()) != -1) {
                cJson.append((char) nch);
            }
            ob.close();
            lRet = load(cJson.toString());
        } catch (Exception e) {
            lRet = errBox("Ошибка загрузки JSON из файла\n" + e.getMessage());
        }
        return lRet;
    }

    //  Получить список ключей
    public boolean getKeys() {
        boolean lRet = true;
        Object o1;
        String c_type, cKey;
        if (ojs == null) return errBox("Не выполнен метод load");
        if (tResult.equals("Array")) return errBox("Неприменимо к массивам");
        clsProp();
        cResult = "";
        StringBuilder sResult = new StringBuilder();
        try {
            JSONObject ob = (JSONObject) ojs;
            for (Object o : ob.keySet()) {
                cKey = (String) o;
                if (sResult.length() > 0) sResult.append(",");
                o1 = ob.get(cKey);
                if (o1 == null) return errBox("Неизвестное значение " + cKey);
                c_type = getObjType(o1);
                switch (c_type) {
                    case "Array":
                        sResult.append("Array:");
                        break;
                    case "Object":
                        sResult.append("Object:");
                        break;
                    default:
                        sResult.append("Value:");
                        break;
                }
                sResult.append(cKey);
            }
        } catch (Exception e) {
            lRet = errBox("Ошибка получения списка ключей\n" + e.getMessage());
        }
        cResult = sResult.substring(0);
        return lRet;
    }

    //  Возвращает объект из массива, если входная структура массив
    public boolean getObjectFromArray(Integer nPos) {
        boolean lRet = true;
        if (!tResult.equals("Array")) {
            return errBox("Структура не массив");
        }
        if (nPos > nCount) {
            return errBox("Превышен индекс массива");
        }
        try {
            JSONArray oa = (JSONArray) ojs;
            JSONObject ob = (JSONObject) oa.get(nPos);
            cResult = ob.toJSONString();
        } catch (Exception e) {
            lRet = errBox("Ошибка выбора объект из массива\n" + e.getMessage());
        }
        return lRet;
    }

    //  Возвращает JSON структуру из пути к массиву или объекту
    public boolean getPathJson(String cPath) {
        boolean lRet = true;
        clsProp();
        Object o1;
        String c_type;
        int pos, i, i_max;
        if (ojs == null) return errBox("Не выполнен метод load");
        try {
            String[] a_parts = cPath.split("\\.");
            i_max = a_parts.length;
            JSONObject ob;
            ob = (JSONObject) ojs;
            cPath = "\n" + cPath;
            i = 0;
            for (String part : a_parts) {
                i += 1;
                pos = getNPos(part);
                nCount = -1;
                if (pos > -1) part = getPartName(part);
                o1 = ob.get(part);
                if (o1 == null) return errBox("Неизвестный значение в имени пути: " + part + cPath);
                c_type = getObjType(o1);
                if (c_type.equals("Array")) {
                    JSONArray oa = (JSONArray) ob.get(part);
                    nCount = oa.size();
                    nSize = nCount;
                    if (pos > nCount - 1) return errBox("Неверный индекс массива " + part + cPath);
                    if (pos > -1) {
                        ob = (JSONObject) oa.get(pos);
                        nSize = ob.size();
                    } else {
                        if (i == i_max) {
                            cResult = oa.toJSONString();
                            return true;
                        } else {
                            return errBox("Индекс массива " + part + " не определен");
                        }
                    }
                } else if (c_type.equals("Object")) {
                    ob = (JSONObject) o1;
                    nSize = ob.size();
                }
                if (i == i_max) {
                    cResult = ob.toJSONString();
                    return true;
                }
            }
            if (cResult == null) return errBox("JSON структура не найдена" + cPath);
        } catch (Exception e) {
            lRet = errBox(e.getMessage());
        }
        return lRet;
    }

    //  Получаем значение поля по указанному пути
    public boolean getPathValue(String cPath) {
        return getValue(1, cPath);
    }

    //  Получаем размерность массива по указанному пути
    public boolean getArraySize(String cPath) {
        return getValue(2, cPath);
    }

    //  Проверка, что указанный элемент существует
    public boolean chkElement(String cPath) {
        return getValue(3, cPath);
    }

    //  Возвращает значения для методов getPathValue (1) и getArraySize(2)
    private boolean getValue(int nFlag, String cPath) {
        boolean lRet = true;
        clsProp();
        Object o1;
        String c_type;
        int pos, i, i_max;
        if (ojs == null) return errBox("Не выполнен метод load");
        try {
            String[] a_parts = cPath.split("\\.");
            i_max = a_parts.length;
            JSONObject ob = (JSONObject) ojs;
            cPath = "\n" + cPath;
            i = 0;
            for (String part : a_parts) {
                i += 1;
                pos = getNPos(part);
                nCount = -1;
                if (pos > -1) part = getPartName(part);
                o1 = ob.get(part);
                if (o1 == null) return errBox("Неизвестный значение в имени пути: " + part + cPath);
                c_type = getObjType(o1);
                switch (c_type) {
                    case "Array":
                        JSONArray oa = (JSONArray) ob.get(part);
                        nCount = oa.size();
                        tResult = c_type;
                        if (pos > nCount - 1) return errBox("Неверный индекс массива " + part + cPath);
                        if (i == i_max) {
                            if (nFlag == 1) {
                                cResult = oa.get(pos).toString();
                                return true;
                            }
                            if (nFlag == 2) return true;            // Если ищем размер массива (nCount)
                            if (nFlag == 3) return true;            // Если проверяем, что элемент существует (массив)
                        }
                        ob = (JSONObject) oa.get(pos);
                        break;
                    case "Object":
                        tResult = c_type;
                        if (i == i_max) {
                            if (nFlag == 3) return true;            // Если проверяем, что элемент существует (объект)
                        }
                        ob = (JSONObject) o1;
                        break;
                    default:
                        if (i == i_max) {
                            if (nFlag == 1) {
                                cResult = o1.toString();            // Если ищем значение поля
                                tResult = "";
                                return true;
                            }
                            if (nFlag == 3) {
                                tResult = "Value";
                                return true;                        // Если проверяем, что элемент существует (Ключ)
                            }
                        }
                        break;
                }
            }
            if ((nFlag == 1) && (cResult == null)) {
                return errBox("Значение не получено" + cPath);
            } else if ((nFlag == 2) && (nCount == -1)) {
                return errBox("Ошибка заявленных пареметров\nМассив не найден" + cPath);
            }
        } catch (Exception e) {
            lRet = errBox(e.getMessage());
        }
        return lRet;
    }

    //  Возвращает тип объекта
    private String getObjType(Object ob) {
        String[] aType = ob.getClass().toString().split("\\.");
        return aType[aType.length - 1].substring(4);
    }

    //  Получить N - позиции в массиве
    private int getNPos(String cstr) {
        if (cstr.contains("[")) {
            return Integer.parseInt(cstr.substring(cstr.indexOf("[") + 1, cstr.indexOf("]")));
        }
        return -1;
    }

    //  Получить имя участака пути
    private String getPartName(String cstr) {
        return cstr.substring(0, cstr.indexOf("["));
    }

    //  Присваивает начальные значения свойствам класса
    private void clsProp() {
        eMessage = "";
        nCount = -1;
        nSize = -1;
        cResult = null;
        tResult = null;
    }

    //  для отладки
    void print(String cMsg) {
        System.out.println(": " + cMsg);
    }

    // Обработка ошибок
    private boolean errBox(String eMsg) {
        clsProp();
        if (!eMsg.contains("Ошибка")) eMsg = "Ошибка: " + eMsg;
        eMessage = eMsg;
        return false;
    }
}
