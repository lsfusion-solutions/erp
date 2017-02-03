package equ.api;

public class PriceCheckerInfo extends MachineryInfo {

    public PriceCheckerInfo(boolean enabled, boolean cleared, boolean succeeded, Integer numberGroup, Integer number,
                            String nameModel, String handlerModel, String port) {
        super(enabled, cleared, succeeded, numberGroup, number, nameModel, handlerModel, port, null);
    }
}
