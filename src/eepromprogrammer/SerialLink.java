package eepromprogrammer;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

/** jSSC serial port that delivers complete text lines. */
public final class SerialLink implements LineChannel, SerialPortEventListener {

    public static final int BAUD = 115200;

    private SerialPort port;
    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<String>();
    private final StringBuilder partial = new StringBuilder();

    public static String[] listPorts() {
        String[] names = SerialPortList.getPortNames();
        return names == null ? new String[0] : names;
    }

    public void open(String name) throws IOException {
        close();
        SerialPort p = new SerialPort(name);
        try {
            p.openPort();
            // DTR high: the Nano resets and prints READY after ~1.5 s
            p.setParams(BAUD, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE, true, true);
            p.purgePort(SerialPort.PURGE_RXCLEAR | SerialPort.PURGE_TXCLEAR);
            p.addEventListener(this, SerialPort.MASK_RXCHAR);
        } catch (SerialPortException e) {
            try {
                if (p.isOpened()) p.closePort();
            } catch (SerialPortException ignored) {
            }
            throw new IOException(describe(e), e);
        }
        port = p;
        lines.clear();
        synchronized (partial) {
            partial.setLength(0);
        }
    }

    public boolean isOpen() {
        return port != null && port.isOpened();
    }

    public String portName() {
        return port == null ? "" : port.getPortName();
    }

    public void close() {
        SerialPort p = port;
        port = null;
        if (p != null && p.isOpened()) {
            try {
                p.removeEventListener();
            } catch (SerialPortException ignored) {
            }
            try {
                p.closePort();
            } catch (SerialPortException ignored) {
            }
        }
    }

    @Override
    public void writeLine(String line) throws IOException {
        SerialPort p = port;
        if (p == null) throw new IOException("Not connected");
        try {
            p.writeBytes((line + "\n").getBytes("US-ASCII"));
        } catch (SerialPortException e) {
            throw new IOException(describe(e), e);
        }
    }

    @Override
    public String readLine(long timeoutMs) throws InterruptedException {
        return lines.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void clearInput() {
        lines.clear();
    }

    @Override
    public void serialEvent(SerialPortEvent e) {
        if (!e.isRXCHAR() || e.getEventValue() <= 0) return;
        SerialPort p = port;
        if (p == null) return;
        try {
            byte[] buf = p.readBytes();
            if (buf == null) return;
            synchronized (partial) {
                for (byte b : buf) {
                    char c = (char) (b & 0xFF);
                    if (c == '\n') {
                        String s = partial.toString().trim();
                        partial.setLength(0);
                        if (!s.isEmpty()) lines.offer(s);
                    } else if (c != '\r') {
                        partial.append(c);
                    }
                }
            }
        } catch (SerialPortException ignored) {
        }
    }

    private static String describe(SerialPortException e) {
        String t = e.getExceptionType();
        if (SerialPortException.TYPE_PORT_BUSY.equals(t)) return "Port is busy (open in another program, e.g. Arduino IDE serial monitor?)";
        if (SerialPortException.TYPE_PORT_NOT_FOUND.equals(t)) return "Port not found (unplugged?)";
        return t != null ? t : String.valueOf(e.getMessage());
    }
}
