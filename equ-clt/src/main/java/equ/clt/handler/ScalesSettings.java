package equ.clt.handler;

import java.io.Serializable;

public class ScalesSettings implements Serializable{

    private boolean usePLUNumberInMessage;
    private boolean newLineNoSubstring;
    private boolean useSockets;
    private boolean allowParallel;
    private boolean capitalLetters;
    private Integer advancedClearMaxPLU;
    private boolean notInvertPrices; //временная опция для BizerbaBS

    //количество байт в строке состава. По умолчанию 1500, максимально для всех Bizerba 1500.
    private Integer descriptionLineLength;

    //если true, то "оптимизируем" текст состава - заменяем похожие кириллические символы на латинские
    //латинские занимают 1 байт вместо 2
    private boolean useDescriptionOptimizer;

    private Integer priceMultiplier;

    public ScalesSettings() {}

    //конструктор оставлен для обратной совместимости
    public ScalesSettings(boolean usePLUNumberInMessage, boolean newLineNoSubstring, boolean useSockets, boolean allowParallel, Integer advancedClearMaxPLU) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
        this.newLineNoSubstring = newLineNoSubstring;
        this.useSockets = useSockets;
        this.allowParallel = allowParallel;
        this.advancedClearMaxPLU = advancedClearMaxPLU;
    }

    public boolean isUsePLUNumberInMessage() {
        return usePLUNumberInMessage;
    }

    public void setUsePLUNumberInMessage(boolean usePLUNumberInMessage) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
    }

    public boolean isNewLineNoSubstring() {
        return newLineNoSubstring;
    }

    public void setNewLineNoSubstring(boolean newLineNoSubstring) {
        this.newLineNoSubstring = newLineNoSubstring;
    }

    public boolean isUseSockets() {
        return useSockets;
    }

    public void setUseSockets(boolean useSockets) {
        this.useSockets = useSockets;
    }

    public boolean isAllowParallel() {
        return allowParallel;
    }

    public void setAllowParallel(boolean allowParallel) {
        this.allowParallel = allowParallel;
    }

    public boolean isCapitalLetters() {
        return capitalLetters;
    }

    public void setCapitalLetters(boolean capitalLetters) {
        this.capitalLetters = capitalLetters;
    }

    public Integer getAdvancedClearMaxPLU() {
        return advancedClearMaxPLU;
    }

    public void setAdvancedClearMaxPLU(Integer advancedClearMaxPLU) {
        this.advancedClearMaxPLU = advancedClearMaxPLU;
    }

    public boolean isNotInvertPrices() {
        return notInvertPrices;
    }

    public void setNotInvertPrices(boolean notInvertPrices) {
        this.notInvertPrices = notInvertPrices;
    }

    public Integer getDescriptionLineLength() {
        return descriptionLineLength;
    }

    public void setDescriptionLineLength(Integer descriptionLineLength) {
        this.descriptionLineLength = descriptionLineLength;
    }

    public Integer getPriceMultiplier() {
        return priceMultiplier;
    }

    public void setPriceMultiplier(Integer priceMultiplier) {
        this.priceMultiplier = priceMultiplier;
    }

    public boolean isUseDescriptionOptimizer() {
        return useDescriptionOptimizer;
    }

    public void setUseDescriptionOptimizer(boolean useDescriptionOptimizer) {
        this.useDescriptionOptimizer = useDescriptionOptimizer;
    }
}
