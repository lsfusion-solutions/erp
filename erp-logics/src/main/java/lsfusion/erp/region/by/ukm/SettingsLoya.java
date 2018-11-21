package lsfusion.erp.region.by.ukm;

public class SettingsLoya {
    String url;
    Integer partnerId;
    String sessionKey;
    boolean logRequests;
    String error;

    public SettingsLoya(String url, Integer partnerId, String sessionKey, boolean logRequests, String error) {
        this.url = url;
        this.partnerId = partnerId;
        this.sessionKey = sessionKey;
        this.logRequests = logRequests;
        this.error = error;
    }
}