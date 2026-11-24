package eepromprogrammer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * Dark theme built only on the JDK: Metal with a dark colour theme plus a few
 * small flat UI delegates (buttons, scrollbars, tabs, progress bar, combo box).
 */
public final class Theme {

    // ------------------------------------------------------------ palette

    public static final Color BG = new Color(0x1B1F23);
    public static final Color PANEL = new Color(0x22272D);
    public static final Color SURFACE = new Color(0x15191C);
    public static final Color SURFACE_ALT = new Color(0x1A1F23);
    public static final Color BORDER = new Color(0x343C44);
    public static final Color CONTROL = new Color(0x2C333A);
    public static final Color CONTROL_HOVER = new Color(0x37404A);
    public static final Color CONTROL_PRESS = new Color(0x242A30);
    public static final Color CONTROL_DISABLED = new Color(0x23292E);
    public static final Color TEXT = new Color(0xDCE3E9);
    public static final Color TEXT_DIM = new Color(0x8C99A5);
    public static final Color TEXT_DISABLED = new Color(0x56616B);
    public static final Color ACCENT = new Color(0x2F7FD1);
    public static final Color ACCENT_HOVER = new Color(0x3D8FE3);
    public static final Color ACCENT_PRESS = new Color(0x266BB2);
    public static final Color SELECTION = new Color(0x2B5580);
    public static final Color OK = new Color(0x66BB6A);
    public static final Color ERR = new Color(0xEF5350);
    public static final Color WARN = new Color(0xFFA726);
    public static final Color DANGER_HOVER = new Color(0x4A2429);

    public static Font UI_FONT = new Font(Font.DIALOG, Font.PLAIN, 13);
    public static Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    /** Client property for buttons: "primary" (blue) or "danger" (red text). */
    public static final String KIND = "flat.kind";

    private Theme() {
    }

    // ------------------------------------------------------------ install

    public static void install() {
        UI_FONT = pickFont(13, "Segoe UI", "Tahoma", "DejaVu Sans", Font.DIALOG);
        MONO_FONT = pickFont(13, "Consolas", "Lucida Console", "DejaVu Sans Mono", Font.MONOSPACED);

        MetalLookAndFeel.setCurrentTheme(new DarkMetalTheme());
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            return;
        }
        UIDefaults d = UIManager.getDefaults();
        String p = Theme.class.getName();

        d.put("ButtonUI", p + "$FlatButtonUI");
        d.put("ToggleButtonUI", p + "$FlatButtonUI");
        d.put("ScrollBarUI", p + "$FlatScrollBarUI");
        d.put("TabbedPaneUI", p + "$FlatTabbedPaneUI");
        d.put("ProgressBarUI", p + "$FlatProgressBarUI");
        d.put("ComboBoxUI", p + "$FlatComboBoxUI");
        d.put("SplitPaneUI", "javax.swing.plaf.basic.BasicSplitPaneUI");

        Border line = BorderFactory.createLineBorder(BORDER);
        Border field = new BorderUIResource(BorderFactory.createCompoundBorder(line,
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        put(d, "Panel.background", BG);
        put(d, "Label.foreground", TEXT);
        put(d, "Label.disabledForeground", TEXT_DISABLED);
        put(d, "Button.foreground", TEXT);
        put(d, "ToggleButton.foreground", TEXT);
        d.put("Button.border", new BorderUIResource(BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        d.put("ToggleButton.border", new BorderUIResource(BorderFactory.createEmptyBorder(5, 12, 5, 12)));

        for (String k : new String[]{"TextField", "FormattedTextField", "PasswordField", "TextArea"}) {
            put(d, k + ".background", SURFACE);
            put(d, k + ".foreground", TEXT);
            put(d, k + ".caretForeground", TEXT);
            put(d, k + ".selectionBackground", SELECTION);
            put(d, k + ".selectionForeground", Color.WHITE);
            put(d, k + ".inactiveForeground", TEXT_DISABLED);
            put(d, k + ".inactiveBackground", CONTROL_DISABLED);
        }
        d.put("TextField.border", field);
        d.put("FormattedTextField.border", field);
        d.put("PasswordField.border", field);

        put(d, "Table.background", SURFACE);
        put(d, "Table.foreground", TEXT);
        put(d, "Table.gridColor", BORDER);
        put(d, "Table.selectionBackground", SELECTION);
        put(d, "Table.selectionForeground", Color.WHITE);
        d.put("Table.focusCellHighlightBorder", new BorderUIResource(BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        put(d, "TableHeader.background", PANEL);
        put(d, "TableHeader.foreground", TEXT_DIM);
        d.put("TableHeader.cellBorder", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER), BorderFactory.createEmptyBorder(3, 4, 3, 4))));

        put(d, "List.background", SURFACE);
        put(d, "List.foreground", TEXT);
        put(d, "List.selectionBackground", SELECTION);
        put(d, "List.selectionForeground", Color.WHITE);

        put(d, "ComboBox.background", CONTROL);
        put(d, "ComboBox.foreground", TEXT);
        put(d, "ComboBox.selectionBackground", SELECTION);
        put(d, "ComboBox.selectionForeground", Color.WHITE);
        put(d, "ComboBox.disabledBackground", CONTROL_DISABLED);
        put(d, "ComboBox.disabledForeground", TEXT_DISABLED);
        put(d, "ComboBox.buttonBackground", CONTROL);
        d.put("ComboBox.border", new BorderUIResource(BorderFactory.createCompoundBorder(line,
                BorderFactory.createEmptyBorder(1, 4, 1, 0))));
        d.put("PopupMenu.border", new BorderUIResource(line));
        put(d, "PopupMenu.background", SURFACE);

        d.put("ScrollPane.border", new BorderUIResource(line));
        put(d, "ScrollPane.background", BG);
        put(d, "Viewport.background", SURFACE);
        d.put("ScrollBar.width", 12);
        put(d, "ScrollBar.background", SURFACE);
        put(d, "ScrollBar.track", SURFACE);
        put(d, "ScrollBar.thumb", new Color(0x3B444D));

        put(d, "SplitPane.background", BG);
        d.put("SplitPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        d.put("SplitPaneDivider.border", new BorderUIResource(BorderFactory.createEmptyBorder()));

        put(d, "TabbedPane.background", BG);
        put(d, "TabbedPane.foreground", TEXT);

        put(d, "ProgressBar.foreground", ACCENT);
        put(d, "ProgressBar.background", CONTROL);
        put(d, "ProgressBar.selectionForeground", TEXT);
        put(d, "ProgressBar.selectionBackground", TEXT);
        d.put("ProgressBar.border", new BorderUIResource(BorderFactory.createEmptyBorder()));

        put(d, "ToolTip.background", new Color(0x2B3238));
        put(d, "ToolTip.foreground", TEXT);
        d.put("ToolTip.border", new BorderUIResource(BorderFactory.createCompoundBorder(line,
                BorderFactory.createEmptyBorder(4, 6, 4, 6))));

        put(d, "OptionPane.background", BG);
        put(d, "OptionPane.messageForeground", TEXT);
        put(d, "Separator.foreground", BORDER);
        put(d, "Separator.background", BG);

        put(d, "FileChooser.listViewBackground", SURFACE);
        d.put("OptionPane.questionIcon", new DialogIcon('?', ACCENT));
        d.put("OptionPane.informationIcon", new DialogIcon('i', ACCENT));
        d.put("OptionPane.warningIcon", new DialogIcon('!', WARN));
        d.put("OptionPane.errorIcon", new DialogIcon('x', ERR));
        put(d, "TitledBorder.titleColor", TEXT_DIM);
    }

    private static void put(UIDefaults d, String key, Color c) {
        if (c != null) d.put(key, new ColorUIResource(c));
    }

    private static Font pickFont(int size, String... names) {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String n : names) {
            if (have.contains(n) || n.equals(Font.DIALOG) || n.equals(Font.MONOSPACED)) return new Font(n, Font.PLAIN, size);
        }
        return new Font(Font.DIALOG, Font.PLAIN, size);
    }

    static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map) g.addRenderingHints((Map<?, ?>) hints);
        else g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // ------------------------------------------------------------ icons

    private static final Map<String, ImageIcon> ICONS = new HashMap<String, ImageIcon>();

    public static ImageIcon icon(String name) {
        ImageIcon i = ICONS.get(name);
        if (i == null) {
            URL u = Theme.class.getResource("icons/" + name + ".png");
            i = u == null ? new ImageIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)) : new ImageIcon(u);
            ICONS.put(name, i);
        }
        return i;
    }

    /** Same icon at 35% opacity, for disabled buttons. */
    public static Icon faded(ImageIcon src) {
        BufferedImage b = new BufferedImage(src.getIconWidth(), src.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.drawImage(src.getImage(), 0, 0, null);
        g.dispose();
        return new ImageIcon(b);
    }

    public static void setIcon(AbstractButton b, String name) {
        ImageIcon i = icon(name);
        b.setIcon(i);
        b.setDisabledIcon(faded(i));
        b.setIconTextGap(8);
    }

    public static List<Image> appIcons() {
        List<Image> list = new ArrayList<Image>();
        for (int s : new int[]{16, 20, 24, 32, 48, 64, 128, 256}) {
            URL u = Theme.class.getResource("icons/app_" + s + ".png");
            if (u == null) continue;
            try {
                list.add(ImageIO.read(u));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    /** Round 32 px dialog icon with a symbol, drawn in code. */
    static final class DialogIcon implements Icon, javax.swing.plaf.UIResource {
        private final char symbol;
        private final Color color;

        DialogIcon(char symbol, Color color) {
            this.symbol = symbol;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.translate(x, y);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (symbol == '!') {
                Path2D.Double t = new Path2D.Double();
                t.moveTo(16, 3);
                t.lineTo(30, 28);
                t.lineTo(2, 28);
                t.closePath();
                g2.draw(t);
                g2.drawLine(16, 12, 16, 19);
                g2.fillOval(14, 22, 4, 4);
            } else {
                g2.drawOval(2, 2, 28, 28);
                if (symbol == 'x') {
                    g2.drawLine(11, 11, 21, 21);
                    g2.drawLine(21, 11, 11, 21);
                } else if (symbol == 'i') {
                    g2.fillOval(14, 8, 4, 4);
                    g2.drawLine(16, 15, 16, 23);
                } else {
                    Path2D.Double q = new Path2D.Double();
                    q.moveTo(11.5, 12.5);
                    q.curveTo(11.5, 6.5, 21, 6.5, 20.5, 12.5);
                    q.curveTo(20.2, 16, 16, 16, 16, 19.5);
                    g2.draw(q);
                    g2.fillOval(14, 22, 4, 4);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 32;
        }

        @Override
        public int getIconHeight() {
            return 32;
        }
    }

    // ------------------------------------------------------------ Metal colour theme

    static final class DarkMetalTheme extends DefaultMetalTheme {
        @Override
        public String getName() {
            return "Dark";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return new ColorUIResource(ACCENT);
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return new ColorUIResource(BORDER);
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return new ColorUIResource(CONTROL);
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return new ColorUIResource(BG);
        }

        @Override
        protected ColorUIResource getBlack() {
            return new ColorUIResource(TEXT);
        }

        @Override
        protected ColorUIResource getWhite() {
            return new ColorUIResource(SURFACE);
        }

        @Override
        public ColorUIResource getInactiveControlTextColor() {
            return new ColorUIResource(TEXT_DISABLED);
        }

        @Override
        public ColorUIResource getInactiveSystemTextColor() {
            return new ColorUIResource(TEXT_DISABLED);
        }

        @Override
        public ColorUIResource getMenuSelectedBackground() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        public FontUIResource getControlTextFont() {
            return new FontUIResource(UI_FONT);
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return new FontUIResource(UI_FONT);
        }

        @Override
        public FontUIResource getUserTextFont() {
            return new FontUIResource(UI_FONT);
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return new FontUIResource(UI_FONT);
        }

        @Override
        public FontUIResource getWindowTitleFont() {
            return new FontUIResource(UI_FONT.deriveFont(Font.BOLD));
        }

        @Override
        public FontUIResource getSubTextFont() {
            return new FontUIResource(UI_FONT.deriveFont(11f));
        }
    }

    // ------------------------------------------------------------ flat button

    public static class FlatButtonUI extends BasicButtonUI {

        public static ComponentUI createUI(JComponent c) {
            return new FlatButtonUI();
        }

        @Override
        protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            String kind = String.valueOf(b.getClientProperty(KIND));
            boolean primary = "primary".equals(kind);
            boolean danger = "danger".equals(kind);
            boolean pressed = m.isPressed() && m.isArmed();

            Color bg;
            if (!b.isEnabled()) bg = CONTROL_DISABLED;
            else if (primary) bg = pressed ? ACCENT_PRESS : m.isRollover() ? ACCENT_HOVER : ACCENT;
            else if (pressed) bg = CONTROL_PRESS;
            else if (m.isSelected()) bg = SELECTION;
            else if (m.isRollover()) bg = danger ? DANGER_HOVER : CONTROL_HOVER;
            else bg = CONTROL;

            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 8, 8);
            if (!primary && b.isEnabled()) {
                g2.setColor(m.isSelected() ? ACCENT : BORDER);
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
            }
            if (b.isFocusOwner() && b.isFocusable() && !(b instanceof javax.swing.JToggleButton)) {
                g2.setColor(ACCENT_HOVER);
                g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, 7, 7);
            }
            g2.dispose();
            paint(g, c);
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, Rectangle r, String text) {
            Graphics2D g2 = (Graphics2D) g;
            antialias(g2);
            String kind = String.valueOf(b.getClientProperty(KIND));
            Color fg;
            if (!b.isEnabled()) fg = TEXT_DISABLED;
            else if ("primary".equals(kind)) fg = Color.WHITE;
            else if ("danger".equals(kind)) fg = ERR;
            else fg = b.getForeground();
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            BasicGraphicsUtils.drawStringUnderlineCharAt(g2, text, b.getDisplayedMnemonicIndex(),
                    r.x + getTextShiftOffset(), r.y + fm.getAscent() + getTextShiftOffset());
        }

        @Override
        protected void paintButtonPressed(Graphics g, AbstractButton b) {
        }

        @Override
        protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
        }
    }

    // ------------------------------------------------------------ flat scrollbar

    public static class FlatScrollBarUI extends BasicScrollBarUI {

        public static ComponentUI createUI(JComponent c) {
            return new FlatScrollBarUI();
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0x3B444D);
            trackColor = SURFACE;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            Dimension z = new Dimension(0, 0);
            b.setPreferredSize(z);
            b.setMinimumSize(z);
            b.setMaximumSize(z);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            g2.setColor(isThumbRollover() || isDragging ? new Color(0x56616C) : thumbColor);
            boolean v = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int pad = 3;
            if (v) g2.fillRoundRect(r.x + pad, r.y + 2, r.width - 2 * pad, r.height - 4, 6, 6);
            else g2.fillRoundRect(r.x + 2, r.y + pad, r.width - 4, r.height - 2 * pad, 6, 6);
            g2.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(12, 28);
        }
    }

    // ------------------------------------------------------------ flat tabs

    public static class FlatTabbedPaneUI extends BasicTabbedPaneUI {

        public static ComponentUI createUI(JComponent c) {
            return new FlatTabbedPaneUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(6, 14, 6, 14);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(1, 0, 0, 0);
            tabPane.setOpaque(false);
        }

        @Override
        protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
            g.setColor(selected ? PANEL : BG);
            g.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
            if (selected) {
                g.setColor(ACCENT);
                g.fillRect(x, y + h - 2, w, 2);
            }
        }

        @Override
        protected void paintText(Graphics g, int placement, Font font, FontMetrics fm, int index, String title,
                Rectangle textRect, boolean selected) {
            Graphics2D g2 = (Graphics2D) g;
            antialias(g2);
            g2.setFont(font);
            g2.setColor(selected ? TEXT : TEXT_DIM);
            g2.drawString(title, textRect.x, textRect.y + fm.getAscent());
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index,
                Rectangle iconRect, Rectangle textRect, boolean selected) {
        }

        @Override
        protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            int y = calculateTabAreaHeight(placement, runCount, maxTabHeight);
            g.setColor(BORDER);
            g.fillRect(0, y, tabPane.getWidth(), 1);
        }

        @Override
        protected int getTabLabelShiftY(int placement, int index, boolean selected) {
            return 0;
        }
    }

    // ------------------------------------------------------------ flat progress bar

    public static class FlatProgressBarUI extends BasicProgressBarUI {

        public static ComponentUI createUI(JComponent c) {
            return new FlatProgressBarUI();
        }

        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            JProgressBar pb = (JProgressBar) c;
            Graphics2D g2 = (Graphics2D) g.create();
            antialias(g2);
            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(CONTROL);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            int amount = getAmountFull(new Insets(0, 0, 0, 0), w, h);
            if (amount > 0) {
                g2.setColor(pb.getForeground());
                g2.fillRoundRect(0, 0, Math.max(8, amount), h, 8, 8);
            }
            if (pb.isStringPainted()) {
                String s = pb.getString();
                g2.setFont(pb.getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(TEXT);
                g2.drawString(s, (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------ flat combo box

    public static class FlatComboBoxUI extends BasicComboBoxUI {

        public static ComponentUI createUI(JComponent c) {
            return new FlatComboBoxUI();
        }

        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                public void paint(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    antialias(g2);
                    g2.setColor(isEnabled() ? TEXT_DIM : TEXT_DISABLED);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(cx - 4, cy - 2);
                    p.lineTo(cx, cy + 2);
                    p.lineTo(cx + 4, cy - 2);
                    g2.draw(p);
                    g2.dispose();
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setPreferredSize(new Dimension(22, 22));
            return b;
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false);   // no blue "focused" fill
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(comboBox.isEnabled() ? CONTROL : CONTROL_DISABLED);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            g.setColor(comboBox.isEnabled() ? CONTROL : CONTROL_DISABLED);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            super.paint(g, c);
        }
    }
}
