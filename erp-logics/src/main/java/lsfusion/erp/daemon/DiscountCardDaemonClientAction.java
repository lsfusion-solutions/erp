package lsfusion.erp.daemon;

import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.ExecuteClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.Serializable;

public class DiscountCardDaemonClientAction extends ExecuteClientAction {

    @Override
    public void execute(ClientActionDispatcher dispatcher) {
        final DiscountCardDaemonListener discountCardDaemonListener = new DiscountCardDaemonListener();
        discountCardDaemonListener.setEventBus(dispatcher.getEventBus());
        discountCardDaemonListener.start();
        dispatcher.addCleanListener(() -> uninstall(discountCardDaemonListener));
    }

    private static class DiscountCardDaemonListener extends AbstractDaemonListener implements Serializable, KeyEventDispatcher {

        public static final String SCANNER_SID = "SCANNER";
        private static boolean recording;
        private static boolean isNew;
        private static String input = "";

        public DiscountCardDaemonListener() {
        }

        @Override
        public String start() {
            install(this);
            return null;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if(e.getID() == KeyEvent.KEY_PRESSED) {
                if(e.getKeyChar() == ';' || e.getKeyChar() == 'ж' || e.getKeyChar() == 'Ж' || e.getKeyChar() == '%') {
                    if(!recording) {
                        recording = true;
                        isNew = e.getKeyChar() == '%';
                    }
                    e.consume();
                }
                else if(e.getKeyChar() == '\n') {
                    recording = false;
                    if(input.length() > 2 && input.charAt(input.length() - 2) == 65535
                            && ((input.charAt(input.length() - 1) == '?') || input.charAt(input.length() - 1) == ',')) {
                        if (isNew) {
                            if (input.startsWith("70833700"))
                                input = "70833700";
                            else if (input.startsWith('\uFFFF' + "Z7083370") || input.startsWith('\uFFFF' + "Я7083370"))
                                input = "Z7083370";
                            else
                                input = input.substring(0, input.length() - 2);
                        } else {
                            input = input.substring(0, input.length() - 2);
                        }
                        ServerLoggers.systemLogger.info(input);
                        eventBus.fireValueChanged(SCANNER_SID, input);
                        input = "";
                        e.consume();
                    }
                } else if(recording) {
                    input += e.getKeyChar();
                    e.consume();
                }
            }
            return false;
        }
    }

    private static void install(KeyEventDispatcher keyEventDispatcher) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
    }

    private static void uninstall(KeyEventDispatcher keyEventDispatcher) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
    }
}