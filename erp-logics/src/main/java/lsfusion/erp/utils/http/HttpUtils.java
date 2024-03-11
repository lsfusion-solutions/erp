package lsfusion.erp.utils.http;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpUtils {

    public static Response sendPostRequest(String url, String xml) throws IOException, JDOMException {
        try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            HttpEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
            httpPost.addHeader("Content-Type", "text/xml");
            httpPost.setEntity(entity);
            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

            int status = httpResponse.getCode();
            String error = status == 200 ? null : httpResponse.getReasonPhrase();
            Document document = new SAXBuilder().build(httpResponse.getEntity().getContent());
            return new Response(error, document);
        }
    }
}