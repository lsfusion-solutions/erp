package lsfusion.erp.machinery.scales;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.bouncycastle.util.encoders.Hex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.util.Iterator;

public class WriteFileToSocketAction extends InternalAction {
    private static short cmdWrite = 0xF1;
    private final ClassPropertyInterface fileInterface;
    private final ClassPropertyInterface typeInterface;
    private final ClassPropertyInterface ipInterface;

    public WriteFileToSocketAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        fileInterface = i.next();
        typeInterface = i.next();
        ipInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        FileData fileData = (FileData) context.getKeyValue(fileInterface).getValue();
        Integer type = (Integer) context.getKeyValue(typeInterface).getValue();
        String ip = (String) context.getKeyValue(ipInterface).getValue();

        if (fileData != null && type != null && ip != null) {

            try {

                DataSocket socket = new DataSocket(ip);
                try {
                    socket.open();
                    int result = sendRecord(socket, cmdWrite, type.shortValue(), Hex.decode(new String(fileData.getRawFile().getBytes())));
                    if(result != 0) {
                        context.delayUserInteraction(new MessageClientAction("Файл не загружен. Ошибка: " + result, "Ошибка"));
                    }
                } finally {
                    socket.close();
                }
                findProperty("writeFileToSocketResult[]").change(true, context);

            } catch (Exception e) {
                ServerLoggers.printerLogger.error("Write to socket error", e);
                try {
                    findProperty("writeFileToSocketResult[]").change((Boolean) null, context);
                } catch (ScriptingErrorLog.SemanticErrorException ignored) {
                }
                if (e instanceof ConnectException) {
                    context.delayUserInteraction(new MessageClientAction(String.format("Сокет %s недоступен. \n%s", ip, e.getMessage()), "Ошибка"));
                } else {
                    throw Throwables.propagate(e);
                }
            }

        }
    }

    protected int sendRecord(DataSocket socket, short cmd, short file, byte[] record) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(record.length + 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) cmd);
        bytes.put((byte) file);
        bytes.put(record);
        return sendCommand(socket, bytes.array());
    }

    private int sendCommand(DataSocket socket, byte[] bytes) throws IOException {
        socket.outputStream.write(bytes);
        socket.outputStream.flush();
        return receiveReply(socket);
    }

    private int receiveReply(DataSocket socket) {
        try {
            byte[] buffer = new byte[10];
            socket.inputStream.read(buffer);
            return buffer[0] == 6 ? 0 : buffer[0]; //это либо байт ошибки, либо первый байт хвоста (:)
        } catch (Exception e) {
            return -1;
        }
    }

    public class DataSocket {
        String ip;
        Socket socket = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;

        public DataSocket(String ip) {
            this.ip = ip;
        }

        public void open() throws IOException {
            socket = new Socket();
            socket.setSoTimeout(60000);
            socket.connect(new InetSocketAddress(ip, getPort(ip)), 60000);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
        }

        public void close() throws IOException {
            if(outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if(inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
        }

        private int getPort(String ip) {
            String[] splittedIP = ip.split("\\.");
            return 2000 + Integer.parseInt(splittedIP[splittedIP.length - 1]);
        }
    }
}