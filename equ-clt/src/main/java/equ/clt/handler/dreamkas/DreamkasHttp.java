package equ.clt.handler.dreamkas;

import org.apache.http.Consts;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Класс обработчика выполнения HTTP(S) запросов
public class DreamkasHttp {
    public String cResult = "";                               // Строка результата
    public int nStatus = 0;                                // Статус выполнения запроса
    public String cStatus = "";                               // Статус выполнения запроса
    public String eMessage = "";                             // Текст ошибки
    private String cAHeader = "";                               // Строка заголовков см. addHeader()
    private String[] tQuery = {"GET", "POST", "DELETE", "PATCH"};  // Массив типов обрабатываемых запросов

    //    Очищает строку заголовков
    public void clsHeader() {
        cAHeader = "";
    }

    // Создает строку заголовков
    public void addHeader(String cName, String cValue) {
        cAHeader += cName + "\t" + cValue + "\n";
    }

    // Очищает свойства класса перед выполнением
    private void clsProp() {
        eMessage = "";
        nStatus = 0;
        cStatus = "";
        cResult = "";
    }

    public boolean send(String cMethod, String cURL, String cData) {
        cMethod = cMethod.toUpperCase().trim();
        if (!Arrays.asList(tQuery).contains(cMethod))
            return errBox("Неизвестный метод: " + cMethod);
        clsProp();
        boolean lRet = true;
        HttpGet ob_get = null;
        HttpPost ob_post = null;
        HttpDeleteWithBody ob_del = null;
        HttpPatch ob_patch = null;
        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault();
            switch (cMethod) {
                case "GET":
                    ob_get = new HttpGet(cURL);
                    break;
                case "POST":
                    ob_post = new HttpPost(cURL);
                    break;
                case "DELETE":
                    ob_del = new HttpDeleteWithBody(cURL);
                    break;
                case "PATCH":
                    ob_patch = new HttpPatch(cURL);
                    break;
                default:
                    return false;
            }
            // добавляем заголовки к запросу
            if (cAHeader.length() > 0) {
                for (String c1 : cAHeader.split("\n")) {
                    String[] c2 = c1.split("\t");
                    switch (cMethod) {
                        case "GET":
                            ob_get.addHeader(c2[0], c2[1]);
                            break;
                        case "POST":
                            ob_post.addHeader(c2[0], c2[1]);
                            break;
                        case "DELETE":
                            ob_del.addHeader(c2[0], c2[1]);
                            break;
                        case "PATCH":
                            ob_patch.addHeader(c2[0], c2[1]);
                            break;
                    }
                }
            }
            // выполняем запрос
            if (cMethod.equals("GET")) {
                response = httpclient.execute(ob_get);
            } else {
                StringEntity entity = new StringEntity(cData, ContentType.create("plain/text", Consts.UTF_8));
                entity.setChunked(true);
                switch (cMethod) {
                    case "POST":
                        ob_post.setEntity(entity);
                        response = httpclient.execute(ob_post);
                        break;
                    case "PATCH":
                        ob_patch.setEntity(entity);
                        response = httpclient.execute(ob_patch);
                        break;
                    case "DELETE":
                        ob_del.setEntity(entity);
                        response = httpclient.execute(ob_del);
                        break;
                }
            }
            nStatus = response.getStatusLine().getStatusCode();
            cStatus = response.getStatusLine().toString();
            //  читаем ответ
            if (!cStatus.toUpperCase().contains("NO CONTENT")) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
                cResult = result.toString();
            }
        } catch (Exception e) {
            lRet = errBox("WEB " + cMethod + " не выполнен, Проверьте доступ " + e.getMessage());
        }
        return lRet;
    }

    // Обработчик ошибок
    private boolean errBox(String eMsg) {
        eMessage = eMsg;
        return false;
    }
}

class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
    public static final String METHOD_NAME = "DELETE";

    public String getMethod() {
        return METHOD_NAME;
    }

    public HttpDeleteWithBody(final String uri) {
        super();
        setURI(URI.create(uri));
    }

//    public HttpDeleteWithBody(final URI uri) {
//        super();
//        setURI(uri);
//    }
//
//    public HttpDeleteWithBody() {
//        super();
//    }
}
