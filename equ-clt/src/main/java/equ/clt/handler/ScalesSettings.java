package equ.clt.handler;

import java.io.Serializable;

//actually is BizerbaSettings
public class ScalesSettings implements Serializable{

    //если true, то каждое слово наименования - с большой буквы
    private boolean capitalLetters;

    private boolean notInvertPrices; //временная опция для BizerbaBS

    //количество байт в строке состава. По умолчанию 1500, максимально для всех Bizerba 1500.
    private Integer descriptionLineLength;

    //если true, то "оптимизируем" текст состава - заменяем похожие кириллические символы на латинские
    //латинские занимают 1 байт вместо 2
    private boolean useDescriptionOptimizer;

    //если задано и больше 0, то команда loadPLU отправляется с заданным таймаутом
    private Long sendCommandTimeout;

    //Загружать в таблицу STST scales.number
    private boolean loadStaticTextScalesNumber;

    public ScalesSettings() {}

    public boolean isCapitalLetters() {
        return capitalLetters;
    }

    public void setCapitalLetters(boolean capitalLetters) {
        this.capitalLetters = capitalLetters;
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

    @SuppressWarnings("unused")
    public void setDescriptionLineLength(Integer descriptionLineLength) {
        this.descriptionLineLength = descriptionLineLength;
    }

    public boolean isUseDescriptionOptimizer() {
        return useDescriptionOptimizer;
    }

    @SuppressWarnings("unused")
    public void setUseDescriptionOptimizer(boolean useDescriptionOptimizer) {
        this.useDescriptionOptimizer = useDescriptionOptimizer;
    }

    public Long getSendCommandTimeout() {
        return sendCommandTimeout;
    }

    @SuppressWarnings("unused")
    public void setSendCommandTimeout(Long sendCommandTimeout) {
        this.sendCommandTimeout = sendCommandTimeout;
    }

    @SuppressWarnings("unused")
    public boolean isLoadStaticTextScalesNumber() {
        return loadStaticTextScalesNumber;
    }

    @SuppressWarnings("unused")
    public void setLoadStaticTextScalesNumber(boolean loadStaticTextScalesNumber) {
        this.loadStaticTextScalesNumber = loadStaticTextScalesNumber;
    }
}
