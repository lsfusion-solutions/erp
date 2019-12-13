package equ.clt.handler.astron;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class AstronSettings implements Serializable {
    private Integer timeout;
    public String groupMachineries = null;
    private String cashPayments;
    private String cardPayments;
    private String giftCardPayments;
    private boolean ignoreSalesInfoWithoutCashRegister;

    //если true, то выгружаем таблицы prclevel, sarea, sareaprc
    private boolean exportExtraTables;

    public AstronSettings() {
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Map<Integer, Integer> getGroupMachineryMap() {
        Map<Integer, Integer> groupMachineryMap = new HashMap<>();
        if(groupMachineries != null) {
            for (String groupMachinery : groupMachineries.split(",")) {
                String[] entry = trim(groupMachinery).split("->");
                if (entry.length == 2) {
                    Integer key = parseInt(trim(entry[0]));
                    Integer value = parseInt(trim(entry[1]));
                    if (key != null && value != null)
                        groupMachineryMap.put(key, value);
                }
            }
        }
        return groupMachineryMap;
    }

    public void setGroupMachineries(String groupMachineries) {
        this.groupMachineries = groupMachineries;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }


    public boolean isExportExtraTables() {
        return exportExtraTables;
    }

    public void setExportExtraTables(boolean exportExtraTables) {
        this.exportExtraTables = exportExtraTables;
    }

    public String getCashPayments() {
        return cashPayments;
    }

    public void setCashPayments(String cashPayments) {
        this.cashPayments = cashPayments;
    }

    public String getCardPayments() {
        return cardPayments;
    }

    public void setCardPayments(String cardPayments) {
        this.cardPayments = cardPayments;
    }

    public String getGiftCardPayments() {
        return giftCardPayments;
    }

    public void setGiftCardPayments(String giftCardPayments) {
        this.giftCardPayments = giftCardPayments;
    }

    public boolean isIgnoreSalesInfoWithoutCashRegister() {
        return ignoreSalesInfoWithoutCashRegister;
    }

    public void setIgnoreSalesInfoWithoutCashRegister(boolean ignoreSalesInfoWithoutCashRegister) {
        this.ignoreSalesInfoWithoutCashRegister = ignoreSalesInfoWithoutCashRegister;
    }
}