package eepromprogrammer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Load / save raw binary and Intel HEX images. */
public final class HexFiles {

    private static final Charset ASCII = Charset.forName("US-ASCII");

    private HexFiles() {
    }

    public static boolean isIntelHexName(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".hex") || n.endsWith(".ihx") || n.endsWith(".ihex");
    }

    public static byte[] load(File f) throws IOException {
        byte[] raw = Files.readAllBytes(f.toPath());
        if (isIntelHexName(f) || looksLikeIntelHex(raw)) return parseIntelHex(raw);
        return raw;
    }

    public static void save(File f, byte[] data) throws IOException {
        if (isIntelHexName(f)) {
            Files.write(f.toPath(), toIntelHex(data).getBytes(ASCII));
        } else {
            Files.write(f.toPath(), data);
        }
    }

    private static boolean looksLikeIntelHex(byte[] raw) {
        int i = 0;
        while (i < raw.length && (raw[i] == ' ' || raw[i] == '\r' || raw[i] == '\n' || raw[i] == '\t')) i++;
        if (i >= raw.length || raw[i] != ':') return false;
        for (int k = 0; k < Math.min(raw.length, 512); k++) {
            int c = raw[k] & 0xFF;
            boolean ok = c == ':' || c == '\r' || c == '\n' || c == ' ' || c == '\t'
                    || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
            if (!ok) return false;
        }
        return true;
    }

    /** Gaps are filled with 0xFF (blank EEPROM). */
    public static byte[] parseIntelHex(byte[] raw) throws IOException {
        List<String> lines = Arrays.asList(new String(raw, ASCII).split("\\r?\\n"));
        byte[] img = new byte[0];
        int top = 0;
        int base = 0;
        int lineNo = 0;
        for (String s : lines) {
            lineNo++;
            s = s.trim();
            if (s.isEmpty()) continue;
            if (s.charAt(0) != ':' || s.length() < 11 || (s.length() & 1) == 0) {
                throw new IOException("Intel HEX: bad record on line " + lineNo);
            }
            byte[] rec = new byte[(s.length() - 1) / 2];
            int sum = 0;
            try {
                for (int i = 0; i < rec.length; i++) {
                    rec[i] = (byte) Integer.parseInt(s.substring(1 + 2 * i, 3 + 2 * i), 16);
                    sum += rec[i] & 0xFF;
                }
            } catch (NumberFormatException e) {
                throw new IOException("Intel HEX: bad hex digit on line " + lineNo);
            }
            if ((sum & 0xFF) != 0) throw new IOException("Intel HEX: checksum error on line " + lineNo);
            int n = rec[0] & 0xFF;
            if (rec.length != n + 5) throw new IOException("Intel HEX: wrong length on line " + lineNo);
            int off = ((rec[1] & 0xFF) << 8) | (rec[2] & 0xFF);
            int type = rec[3] & 0xFF;
            switch (type) {
                case 0x00:
                    int start = base + off;
                    if (start + n > 16 * 1024 * 1024) throw new IOException("Intel HEX: address too large");
                    if (start + n > img.length) {
                        int old = img.length;
                        img = Arrays.copyOf(img, Math.max(start + n, old * 2));
                        Arrays.fill(img, old, img.length, (byte) 0xFF);
                    }
                    System.arraycopy(rec, 4, img, start, n);
                    top = Math.max(top, start + n);
                    break;
                case 0x01:
                    return Arrays.copyOf(img, top);
                case 0x02:
                    base = (((rec[4] & 0xFF) << 8) | (rec[5] & 0xFF)) << 4;
                    break;
                case 0x04:
                    base = (((rec[4] & 0xFF) << 8) | (rec[5] & 0xFF)) << 16;
                    break;
                default:
                    break; // 03 / 05 start address records: ignore
            }
        }
        return Arrays.copyOf(img, top);
    }

    public static String toIntelHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        int upper = -1;
        for (int a = 0; a < data.length; a += 16) {
            int hi = a >>> 16;
            if (hi != upper) {
                upper = hi;
                record(sb, 0, 0x04, new byte[]{(byte) (hi >> 8), (byte) hi}, 0, 2);
            }
            record(sb, a & 0xFFFF, 0x00, data, a, Math.min(16, data.length - a));
        }
        sb.append(":00000001FF\r\n");
        return sb.toString();
    }

    private static void record(StringBuilder sb, int off, int type, byte[] d, int from, int n) {
        int sum = n + (off >> 8) + (off & 0xFF) + type;
        sb.append(':').append(String.format("%02X%04X%02X", n, off, type));
        for (int i = 0; i < n; i++) {
            int b = d[from + i] & 0xFF;
            sum += b;
            sb.append(String.format("%02X", b));
        }
        sb.append(String.format("%02X", (-sum) & 0xFF)).append("\r\n");
    }
}
