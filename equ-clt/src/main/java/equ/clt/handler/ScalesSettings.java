package equ.clt.handler;

import java.io.Serializable;

public class ScalesSettings implements Serializable{
    
    private boolean allowParallel; //todo: not used, remove

    //если true, то каждое слово наименования - с большой буквы
    private boolean capitalLetters;

    private boolean notInvertPrices; //временная опция для BizerbaBS

    //количество байт в строке состава. По умолчанию 1500, максимально для всех Bizerba 1500.
    private Integer descriptionLineLength;

    //если true, то "оптимизируем" текст состава - заменяем похожие кириллические символы на латинские
    //латинские занимают 1 байт вместо 2
    private boolean useDescriptionOptimizer;

    public ScalesSettings() {}

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

    public boolean isUseDescriptionOptimizer() {
        return useDescriptionOptimizer;
    }

    public void setUseDescriptionOptimizer(boolean useDescriptionOptimizer) {
        this.useDescriptionOptimizer = useDescriptionOptimizer;
    }
}
