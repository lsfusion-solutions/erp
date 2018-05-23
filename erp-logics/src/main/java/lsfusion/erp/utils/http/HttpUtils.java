package lsfusion.erp.utils.http;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;

public class HttpUtils {

    public static Response sendPostRequest(String url, String xml) throws IOException, JDOMException {
        return sendPostRequest(url, xml, null);
    }

    public static Response sendPostRequest(String url, String xml, Integer timeout) throws IOException, JDOMException {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(url);
        HttpEntity entity = new StringEntity(xml, "UTF-8");
        httpPost.addHeader("Content-type", "text/xml");
        httpPost.setEntity(entity);
        if (timeout != null) {
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build(); //15 minutes
            httpPost.setConfig(requestConfig);
        }
        HttpResponse httpResponse = httpClient.execute(httpPost);

        int status = httpResponse.getStatusLine().getStatusCode();
        String error = status == 200 ? null : httpResponse.getStatusLine().toString();
        Document document = new SAXBuilder().build(httpResponse.getEntity().getContent());
        return new Response(error, document);
    }
}