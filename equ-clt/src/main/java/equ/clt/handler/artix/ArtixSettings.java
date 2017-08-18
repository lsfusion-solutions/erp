package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    private String globalExchangeDirectory;
    private boolean clearDiscountCardsBeforeAdd;

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }

    public boolean isClearDiscountCardsBeforeAdd() {
        return clearDiscountCardsBeforeAdd;
    }

    public void setClearDiscountCardsBeforeAdd(boolean clearDiscountCardsBeforeAdd) {
        this.clearDiscountCardsBeforeAdd = clearDiscountCardsBeforeAdd;
    }
}