package eepromprogrammer;

import java.io.IOException;

/**
 * Talks to the Arduino firmware (v2). Every call is a blocking
 * request/response with timeout and retries; run it off the EDT.
 */
public final class Programmer {

    public static final int FIRMWARE_VERSION = 2;
    public static final int BLOCK = 64;        // bytes per R / W command
    public static final int BULK = 4096;       // bytes per B / F / C command

    private static final int RETRIES = 3;

    /** Thrown when the chip does not hold what was written. */
    public static final class VerifyException extends IOException {
        public final int address;

        VerifyException(int address) {
            super(String.format("Read-back mismatch at 0x%05X", address));
            this.address = address;
        }
    }

    private final LineChannel ch;

    public Programmer(LineChannel ch) {
        this.ch = ch;
    }

    // ------------------------------------------------------------ connection

    /**
     * Waits for the board after the port is opened (the Nano resets) and
     * checks the firmware version.
     *
     * @return firmware version reported by the board
     */
    public int handshake() throws IOException, InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < end) {
            String l = ch.readLine(Math.max(1, end - System.currentTimeMillis()));
            if (l == null) break;           // no reset (DTR not wired) - just ask
            if (l.equals("READY")) break;
        }
        IOException last = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                String r = transact("V", 800);
                if (r.startsWith("VER:")) return Integer.parseInt(r.substring(4).trim());
                last = new IOException("Unexpected reply to V: " + r);
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("No answer from the board. Is eeprom_programmer.ino (v2) uploaded? ("
                + (last == null ? "" : last.getMessage()) + ")");
    }

    public void setChip(ChipType chip) throws IOException, InterruptedException {
        expectOk(transact("T:" + hex(chip.size), 800));
    }

    /** @return detected size in bytes, or 0 if no chip answers */
    public int detect() throws IOException, InterruptedException {
        String r = transact("S", 3000);
        if (r.equals("ERR:NOCHIP")) return 0;
        if (r.startsWith("SIZE:")) return Integer.parseInt(r.substring(5), 16);
        throw new IOException("Detect failed: " + r);
    }

    // ------------------------------------------------------------ data

    public byte[] read(int addr, int len) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                String r = transact("R:" + hex(addr) + ":" + hex(len), 800);
                if (!r.startsWith("D:")) throw new IOException("Read failed at " + addrStr(addr) + ": " + r);
                String[] p = r.split(":");
                if (p.length != 3 || p[1].length() != len * 2) throw new IOException("Garbled read reply");
                byte[] d = new byte[len];
                int sum = 0;
                for (int k = 0; k < len; k++) {
                    d[k] = (byte) Integer.parseInt(p[1].substring(2 * k, 2 * k + 2), 16);
                    sum += d[k] & 0xFF;
                }
                if ((sum & 0xFF) != Integer.parseInt(p[2], 16)) throw new IOException("Read checksum error");
                return d;
            } catch (NumberFormatException e) {
                last = new IOException("Garbled read reply");
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    public void write(int addr, byte[] data, int off, int len) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder(len * 2 + 16);
        sb.append("W:").append(hex(addr)).append(':');
        int sum = 0;
        for (int i = 0; i < len; i++) {
            int b = data[off + i] & 0xFF;
            sb.append(HEX[b >> 4]).append(HEX[b & 15]);
            sum += b;
        }
        sb.append(':').append(String.format("%02X", sum & 0xFF));
        String cmd = sb.toString();

        IOException last = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                String r = transact(cmd, 1500);
                if (r.equals("OK")) return;
                if (r.startsWith("ERR:VERIFY:")) throw new VerifyException(Integer.parseInt(r.substring(11), 16));
                last = new IOException("Write failed at " + addrStr(addr) + ": " + explain(r));
            } catch (VerifyException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** @return -1 if every byte equals val, else the first address that does not */
    public int blankCheck(int addr, int len, int val) throws IOException, InterruptedException {
        String r = transact("B:" + hex(addr) + ":" + hex(len) + ":" + hex(val), 5000);
        if (r.equals("OK")) return -1;
        if (r.startsWith("NE:")) return Integer.parseInt(r.substring(3), 16);
        throw new IOException("Blank check failed: " + explain(r));
    }

    public void fill(int addr, int len, int val) throws IOException, InterruptedException {
        String r = transact("F:" + hex(addr) + ":" + hex(len) + ":" + hex(val), 20000);
        if (r.startsWith("ERR:VERIFY:")) throw new VerifyException(Integer.parseInt(r.substring(11), 16));
        if (!r.equals("OK")) throw new IOException("Fill failed: " + explain(r));
    }

    public int crc(int addr, int len, int init) throws IOException, InterruptedException {
        String r = transact("C:" + hex(addr) + ":" + hex(len) + ":" + hex(init), 5000);
        if (!r.startsWith("CRC:")) throw new IOException("Checksum failed: " + explain(r));
        return Integer.parseInt(r.substring(4), 16);
    }

    // ------------------------------------------------------------ helpers

    private String transact(String cmd, long timeoutMs) throws IOException, InterruptedException {
        ch.clearInput();
        ch.writeLine(cmd);
        long end = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long left = end - System.currentTimeMillis();
            String r = left > 0 ? ch.readLine(left) : null;
            if (r == null) throw new IOException("Timeout - no reply to " + cmd.charAt(0) + " command");
            if (r.equals("READY")) throw new IOException("Board reset unexpectedly (power or USB problem?)");
            return r;
        }
    }

    private static void expectOk(String r) throws IOException {
        if (!r.equals("OK")) throw new IOException(explain(r));
    }

    static String explain(String r) {
        if (r.equals("ERR:I2C")) return "chip not answering on I2C (check wiring, WP pin and chip type)";
        if (r.equals("ERR:RANGE")) return "address outside the selected chip";
        if (r.equals("ERR:NOCFG")) return "no chip selected on the board";
        if (r.equals("ERR:CHK")) return "serial data corrupted";
        return r;
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static String hex(int v) {
        return Integer.toHexString(v).toUpperCase();
    }

    static String addrStr(int a) {
        return String.format("0x%05X", a);
    }

    /** CRC-16/CCITT-FALSE, same as the firmware. */
    public static int crc16(byte[] data, int off, int len, int crc) {
        for (int i = 0; i < len; i++) {
            crc ^= (data[off + i] & 0xFF) << 8;
            for (int k = 0; k < 8; k++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) : (crc << 1);
                crc &= 0xFFFF;
            }
        }
        return crc;
    }
}
