package eepromprogrammer;

/** Supported I2C EEPROMs (A0..A2 tied to GND). */
public enum ChipType {

    C24C01("24C01", 128),
    C24C02("24C02", 256),
    C24C04("24C04", 512),
    C24C08("24C08", 1024),
    C24C16("24C16", 2048),
    C24C32("24C32", 4096),
    C24C64("24C64", 8192),
    C24C128("24C128", 16384),
    C24C256("24C256", 32768),
    C24C512("24C512", 65536),
    C24CM01("24CM01", 131072),
    C24CM02("24CM02", 262144);

    public final String label;
    public final int size;

    ChipType(String label, int size) {
        this.label = label;
        this.size = size;
    }

    public int kbit() {
        return size * 8 / 1024;
    }

    public int pageSize() {
        if (size <= 256) return 8;
        if (size <= 2048) return 16;
        if (size <= 8192) return 32;
        if (size <= 32768) return 64;
        if (size <= 65536) return 128;
        return 256;
    }

    /** e.g. "32 KB · 256 Kbit · page 64 B" */
    public String details() {
        String bytes = size >= 1024 ? (size / 1024) + " KB" : size + " B";
        String bits = kbit() >= 1024 ? (kbit() / 1024) + " Mbit" : kbit() + " Kbit";
        return bytes + "  ·  " + bits + "  ·  " + String.format("%,d", size) + " bytes  ·  page " + pageSize() + " B";
    }

    public static ChipType bySize(int size) {
        for (ChipType c : values()) if (c.size == size) return c;
        return null;
    }

    public static ChipType byLabel(String label) {
        for (ChipType c : values()) if (c.label.equalsIgnoreCase(label)) return c;
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
