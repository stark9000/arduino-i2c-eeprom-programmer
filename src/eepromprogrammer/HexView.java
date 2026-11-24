package eepromprogrammer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.BitSet;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

/** Hex / binary table view of a byte array, with progress and difference highlighting. */
public final class HexView extends JPanel {

    static final Color C_BG = Theme.SURFACE;
    static final Color C_ROW_ALT = Theme.SURFACE_ALT;
    static final Color C_TEXT = Theme.TEXT;
    static final Color C_DIFF_BG = new Color(0x5C1E24);
    static final Color C_DIFF_FG = new Color(0xFF8A80);
    static final Color C_CUR_BG = new Color(0x9A5A0C);
    static final Color C_CUR_FG = Color.WHITE;
    static final Color C_DONE_BG = new Color(0x1D3B22);
    static final Color C_BLANK_FG = new Color(0x58636D);
    static final Color C_ADDR_BG = Theme.PANEL;
    static final Color C_ADDR_FG = new Color(0x7F8E9B);
    static final Color C_ASCII_FG = new Color(0x9FB0BE);
    static final Color C_SEL_BG = Theme.SELECTION;
    static final Color C_SEP = new Color(0x2A3138);

    private static final Font MONO = Theme.MONO_FONT;

    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final CardLayout cards = new CardLayout();
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);

    private byte[] data;
    private boolean binary;
    private boolean editable;
    private BitSet diff = new BitSet();
    private int doneEnd = -1;        // bytes [0, doneEnd) finished in current operation
    private int curStart = -1, curEnd = -1;
    private Runnable onEdit;

    public HexView(String emptyText) {
        super();
        setLayout(cards);
        emptyLabel.setText("<html><div style='text-align:center;color:#7F8E9B'>" + emptyText + "</div></html>");
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(14f));

        table.setFont(MONO);
        table.setRowHeight(20);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);
        table.getTableHeader().setFont(MONO.deriveFont(Font.BOLD));
        table.setDefaultRenderer(Object.class, new Renderer());
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().setBackground(C_BG);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(sp, BorderLayout.CENTER);

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBackground(C_BG);
        empty.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        empty.add(emptyLabel, BorderLayout.CENTER);

        add(empty, "empty");
        add(tablePanel, "table");
        cards.show(this, "empty");
        applyColumnWidths();
    }

    // ------------------------------------------------------------ data

    public void setData(byte[] d) {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        data = d;
        diff = new BitSet();
        clearProgress();
        model.fireTableStructureChanged();
        applyColumnWidths();
        cards.show(this, d == null ? "empty" : "table");
    }

    public byte[] getData() {
        return data;
    }

    public boolean hasData() {
        return data != null;
    }

    public void setEditable(boolean e) {
        editable = e;
    }

    public void setOnEdit(Runnable r) {
        onEdit = r;
    }

    public void setBinary(boolean b) {
        if (binary == b) return;
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        binary = b;
        model.fireTableStructureChanged();
        applyColumnWidths();
    }

    /** Repaint bytes [from, to) after the array changed. */
    public void refreshRange(int from, int to) {
        if (data == null || to <= from) return;
        model.fireTableRowsUpdated(from / perRow(), (to - 1) / perRow());
    }

    // ------------------------------------------------------------ highlighting

    public void setDiff(BitSet b) {
        diff = b == null ? new BitSet() : b;
        table.repaint();
    }

    public BitSet getDiff() {
        return diff;
    }

    /** Mark [0,done) as finished and [done, done+curLen) as in progress, and keep it in view. */
    public void setProgress(int done, int curLen) {
        doneEnd = done;
        curStart = done;
        curEnd = done + curLen;
        table.repaint();
        if (data != null && done < data.length) {
            Rectangle r = table.getCellRect(done / perRow(), 0, true);
            Rectangle vis = table.getVisibleRect();
            if (r.y < vis.y || r.y + r.height > vis.y + vis.height) {
                r.y = Math.max(0, r.y - vis.height / 3);
                r.height = vis.height;
                table.scrollRectToVisible(r);
            }
        }
    }

    public void clearProgress() {
        doneEnd = -1;
        curStart = curEnd = -1;
        table.repaint();
    }

    // ------------------------------------------------------------ navigation / selection

    public void goTo(int addr) {
        if (data == null || addr < 0 || addr >= data.length) return;
        int row = addr / perRow(), col = addr % perRow() + 1;
        table.changeSelection(row, col, false, false);
        Rectangle r = table.getCellRect(row, col, true);
        Rectangle vis = table.getVisibleRect();
        r.y = Math.max(0, r.y - vis.height / 3);
        r.height = vis.height;
        table.scrollRectToVisible(r);
        table.requestFocusInWindow();
    }

    public void scrollToTop() {
        table.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
    }

    /** @return {start, endExclusive} of the selected bytes, or null */
    public int[] selectedRange() {
        if (data == null) return null;
        int r0 = table.getSelectionModel().getMinSelectionIndex();
        int r1 = table.getSelectionModel().getMaxSelectionIndex();
        int c0 = table.getColumnModel().getSelectionModel().getMinSelectionIndex();
        int c1 = table.getColumnModel().getSelectionModel().getMaxSelectionIndex();
        if (r0 < 0 || c0 < 0) return null;
        int pr = perRow();
        c0 = Math.max(1, Math.min(pr, c0));
        c1 = Math.max(1, Math.min(pr, c1));
        int a = r0 * pr + c0 - 1;      // first selected byte
        int b = r1 * pr + c1;          // one past the last selected byte
        a = Math.max(0, Math.min(a, data.length - 1));
        b = Math.max(a + 1, Math.min(b, data.length));
        return new int[]{a, b};
    }

    // ------------------------------------------------------------ internals

    /** 16 bytes per row in hex, 8 in binary (so it fits a 1366 px screen). */
    private int perRow() {
        return binary ? 8 : 16;
    }

    private void applyColumnWidths() {
        TableColumnModel cm = table.getColumnModel();
        int pr = perRow();
        if (cm.getColumnCount() < pr + 2) return;
        cm.getColumn(0).setPreferredWidth(76);
        for (int i = 1; i <= pr; i++) cm.getColumn(i).setPreferredWidth(binary ? 84 : 30);
        cm.getColumn(pr + 1).setPreferredWidth(binary ? 100 : 150);
    }

    private final class Model extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return data == null ? 0 : (data.length + perRow() - 1) / perRow();
        }

        @Override
        public int getColumnCount() {
            return perRow() + 2;
        }

        @Override
        public String getColumnName(int c) {
            if (c == 0) return "Address";
            if (c == perRow() + 1) return "ASCII";
            return String.format("%X", c - 1);
        }

        @Override
        public Object getValueAt(int r, int c) {
            int pr = perRow();
            int base = r * pr;
            if (c == 0) return String.format("%05X", base);
            if (c == pr + 1) {
                StringBuilder sb = new StringBuilder(pr);
                for (int i = 0; i < pr && base + i < data.length; i++) {
                    int b = data[base + i] & 0xFF;
                    sb.append(b >= 32 && b < 127 ? (char) b : '.');
                }
                return sb.toString();
            }
            int a = base + c - 1;
            if (a >= data.length) return "";
            int b = data[a] & 0xFF;
            if (!binary) return String.format("%02X", b);
            String s = Integer.toBinaryString(b);
            return "00000000".substring(s.length()) + s;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return editable && c >= 1 && c <= perRow() && r * perRow() + c - 1 < data.length;
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            String s = String.valueOf(v).trim().replace(" ", "");
            int a = r * perRow() + c - 1;
            try {
                int val;
                if (binary || (s.length() > 2 && s.matches("[01]+"))) val = Integer.parseInt(s, 2);
                else val = Integer.parseInt(s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s, 16);
                if (val < 0 || val > 255) throw new NumberFormatException();
                if ((data[a] & 0xFF) != val) {
                    data[a] = (byte) val;
                    fireTableRowsUpdated(r, r);
                    if (onEdit != null) onEdit.run();
                }
            } catch (NumberFormatException e) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    private final class Renderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, value, sel, false, row, col);
            int pr = perRow();
            boolean ascii = col == pr + 1;
            setFont(MONO);
            setHorizontalAlignment(ascii ? LEFT : CENTER);
            setBorder(BorderFactory.createEmptyBorder(0, ascii ? 10 : 0, 0, 0));

            if (col == 0) {
                setBackground(C_ADDR_BG);
                setForeground(C_ADDR_FG);
                return this;
            }
            Color bg = (row & 1) == 1 ? C_ROW_ALT : C_BG;
            Color fg = C_TEXT;
            if (ascii) {
                fg = C_ASCII_FG;
            } else {
                int a = row * pr + col - 1;
                if (data != null && a < data.length) {
                    if ((data[a] & 0xFF) == 0xFF) fg = C_BLANK_FG;
                    if (a >= curStart && a < curEnd) {
                        bg = C_CUR_BG;
                        fg = C_CUR_FG;
                    } else if (a < doneEnd) {
                        bg = C_DONE_BG;
                    }
                    if (diff.get(a)) {
                        bg = C_DIFF_BG;
                        fg = C_DIFF_FG;
                    }
                }
                if (col == 8 || col == pr) {
                    setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, C_SEP));
                }
            }
            if (sel) {
                bg = C_SEL_BG;
                fg = Color.WHITE;
            }
            setBackground(bg);
            setForeground(fg);
            return this;
        }
    }
}
