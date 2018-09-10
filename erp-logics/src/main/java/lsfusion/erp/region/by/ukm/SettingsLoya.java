package lsfusion.erp.region.by.ukm;

public class SettingsLoya {
    String url;
    Integer partnerId;
    String sessionKey;
    String error;

    public SettingsLoya(String url, Integer partnerId, String sessionKey, String error) {
        this.url = url;
        this.partnerId = partnerId;
        this.sessionKey = sessionKey;
        this.error = error;
    }
}