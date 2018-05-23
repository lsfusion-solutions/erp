package lsfusion.erp.utils.http;

import org.jdom.Document;

public class Response {
    public String error;
    public Document response;

    public Response(String error, Document response) {
        this.error = error;
        this.response = response;
    }
}