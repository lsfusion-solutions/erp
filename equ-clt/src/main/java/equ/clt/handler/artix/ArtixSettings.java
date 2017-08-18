package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    private String globalExchangeDirectory;
    private boolean deleteDiscountCardsBeforeAdd;

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }

    public boolean isDeleteDiscountCardsBeforeAdd() {
        return deleteDiscountCardsBeforeAdd;
    }

    public void setDeleteDiscountCardsBeforeAdd(boolean deleteDiscountCardsBeforeAdd) {
        this.deleteDiscountCardsBeforeAdd = deleteDiscountCardsBeforeAdd;
    }
}