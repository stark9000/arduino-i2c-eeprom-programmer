package eepromprogrammer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/** EEPROM Programmer main window. */
public final class MainWindow extends JFrame {

    private static final Color OK_GREEN = Theme.OK;
    private static final Color ERR_RED = Theme.ERR;
    private static final Color WARN_ORANGE = Theme.WARN;
    private static final Color MUTED = Theme.TEXT_DIM;

    // ------------------------------------------------------------ state

    private final SerialLink link = new SerialLink();
    private final Programmer prog = new Programmer(link);
    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    private volatile boolean connected;
    private volatile boolean busy;
    private volatile boolean cancel;
    private long jobStart;
    private long lastUiUpdate;

    private File bufferFile;
    private boolean bufferDirty;
    private String chipSource = "";
    private boolean suppressChipEvent;

    private interface Job {
        void run() throws Exception;
    }

    private static final class CancelledException extends Exception {
    }

    // ------------------------------------------------------------ widgets

    private final JComboBox<String> portBox = new JComboBox<String>();
    private final JButton refreshBtn = new JButton();
    private final JButton connectBtn = new JButton("Connect");
    private final JLabel connLabel = new JLabel();
    private final JComboBox<ChipType> chipBox = new JComboBox<ChipType>(ChipType.values());
    private final JButton detectBtn = new JButton("Detect");
    private final JLabel chipInfo = new JLabel();

    private final JButton openBtn = actionButton("Open file…", "Load a .bin or Intel .hex file into the buffer  (Ctrl+O)");
    private final JButton saveBufBtn = actionButton("Save buffer…", "Save the buffer as .bin or .hex  (Ctrl+S)");
    private final JButton saveChipBtn = actionButton("Save chip read…", "Save the last chip read as .bin or .hex");

    private final JButton readBtn = actionButton("Read", "Read the whole chip into the Chip tab  (F5)");
    private final JButton writeBtn = actionButton("Write", "Write the buffer to the chip, every block is read back and checked  (F6)");
    private final JButton verifyBtn = actionButton("Verify", "Read the chip and compare it with the buffer  (F7)");
    private final JButton blankBtn = actionButton("Blank check", "Check that every byte is 0xFF");
    private final JButton eraseBtn = actionButton("Erase", "Fill the whole chip with 0xFF");
    private final JButton crcBtn = actionButton("Checksum", "CRC-16 of the chip, compared with the buffer");

    private final JButton newBtn = actionButton("New blank", "New buffer the size of the chip, filled with 0xFF");
    private final JButton copyBtn = actionButton("Chip → buffer", "Copy the last chip read into the buffer for editing or writing");
    private final JButton fillBtn = actionButton("Fill…", "Fill an address range (or the selection) with a value");
    private final JButton compareBtn = actionButton("Compare", "Highlight bytes that differ between buffer and last chip read");

    private final JButton cancelBtn = new JButton("Cancel  (Esc)");

    private final HexView bufferView = new HexView(
            "The buffer is empty.<br><br>Open a file (Ctrl+O), drop a file here,<br>or click <b>New blank</b>.");
    private final HexView chipView = new HexView(
            "Nothing read yet.<br><br>Connect the programmer and click <b>Read</b>.");
    private final JTabbedPane tabs = new JTabbedPane();

    private final JToggleButton hexBtn = new JToggleButton("Hex");
    private final JToggleButton binBtn = new JToggleButton("Binary");
    private final JTextField gotoField = new JTextField(7);
    private final JButton nextDiffBtn = new JButton("Next difference");
    private final JLabel diffLabel = new JLabel(" ");

    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel resultLabel = new JLabel(" ");
    private final JTextArea logArea = new JTextArea();

    // ------------------------------------------------------------ construction

    public MainWindow() {
        super("EEPROM Programmer");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });

        setIconImages(Theme.appIcons());
        buildUi();
        applyIcons();
        wireActions();
        loadPrefs();
        refreshPorts();
        updateChipInfo();
        updateTitles();
        setConnState("Not connected", MUTED);
        updateUi();

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1300, scr.width - 40), Math.min(710, scr.height - 50));
        setMinimumSize(new Dimension(980, 560));
        setLocationRelativeTo(null);
        log("EEPROM Programmer ready. Select the port and click Connect.");
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // ---- top bar
        JPanel top = new JPanel(new BorderLayout());
        JPanel conn = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        conn.add(bold("Port"));
        portBox.setPrototypeDisplayValue("/dev/ttyUSB00");
        conn.add(portBox);
        conn.add(refreshBtn);
        refreshBtn.setToolTipText("Refresh the port list");
        refreshBtn.setPreferredSize(new Dimension(34, 30));
        connectBtn.setPreferredSize(new Dimension(132, 30));
        portBox.setPreferredSize(new Dimension(130, 28));
        chipBox.setPreferredSize(new Dimension(100, 28));
        conn.add(connectBtn);
        conn.add(Box.createHorizontalStrut(6));
        connLabel.setFont(connLabel.getFont().deriveFont(Font.BOLD));
        conn.add(connLabel);

        JPanel chip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        chip.add(bold("Chip"));
        chipBox.setMaximumRowCount(12);
        chip.add(chipBox);
        detectBtn.setToolTipText("Detect the chip size (temporarily changes 2–3 bytes and restores them)");
        chip.add(detectBtn);
        chipInfo.setForeground(MUTED);
        chip.add(chipInfo);

        top.add(conn, BorderLayout.WEST);
        top.add(chip, BorderLayout.EAST);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)));
        root.add(top, BorderLayout.NORTH);

        // ---- left action panel
        JPanel left = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(2, 0, 2, 0);
        addSection(left, g, "CHIP", readBtn, writeBtn, verifyBtn, blankBtn, eraseBtn, crcBtn);
        addSection(left, g, "FILE", openBtn, saveBufBtn, saveChipBtn);
        addSection(left, g, "BUFFER", newBtn, copyBtn, fillBtn, compareBtn);
        g.weighty = 1;
        left.add(Box.createVerticalGlue(), g);
        g.weighty = 0;
        g.insets = new Insets(8, 0, 0, 0);
        cancelBtn.putClientProperty(Theme.KIND, "danger");
        cancelBtn.setHorizontalAlignment(SwingConstants.LEFT);
        cancelBtn.setPreferredSize(new Dimension(160, 34));
        left.add(cancelBtn, g);
        writeBtn.putClientProperty(Theme.KIND, "primary");
        writeBtn.setFont(writeBtn.getFont().deriveFont(Font.BOLD));
        readBtn.setFont(readBtn.getFont().deriveFont(Font.BOLD));
        eraseBtn.putClientProperty(Theme.KIND, "danger");
        left.setOpaque(true);
        left.setBackground(Theme.PANEL);
        left.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        left.setPreferredSize(new Dimension(192, 10));
        root.add(left, BorderLayout.WEST);

        // ---- center: view bar + tabs
        JPanel viewBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ButtonGroup bg = new ButtonGroup();
        bg.add(hexBtn);
        bg.add(binBtn);
        hexBtn.setSelected(true);
        JPanel seg = new JPanel(new GridLayout(1, 2, 0, 0));
        seg.add(hexBtn);
        seg.add(binBtn);
        viewBar.add(new JLabel("View"));
        viewBar.add(seg);
        viewBar.add(Box.createHorizontalStrut(14));
        viewBar.add(new JLabel("Go to address", Theme.icon("goto"), SwingConstants.LEADING));
        gotoField.setToolTipText("Hex address, Enter to jump  (Ctrl+G)");
        viewBar.add(gotoField);
        viewBar.add(Box.createHorizontalStrut(14));
        viewBar.add(nextDiffBtn);
        diffLabel.setFont(diffLabel.getFont().deriveFont(Font.BOLD));
        viewBar.add(diffLabel);

        bufferView.setEditable(true);
        tabs.addTab("Buffer", bufferView);
        tabs.addTab("Chip", chipView);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(viewBar, BorderLayout.NORTH);
        center.add(tabs, BorderLayout.CENTER);

        // ---- bottom: progress, result, log
        progress.setStringPainted(true);
        progress.setString("Idle");
        progress.setPreferredSize(new Dimension(420, 24));
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 13f));
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.add(progress, BorderLayout.WEST);
        status.add(resultLabel, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setRows(6);
        logArea.setFont(Theme.MONO_FONT.deriveFont(12f));
        logArea.setMargin(new Insets(4, 6, 4, 6));
        JScrollPane logScroll = new JScrollPane(logArea);
        JButton clearLog = new JButton("Clear log");
        Theme.setIcon(clearLog, "clear");
        clearLog.addActionListener(e -> logArea.setText(""));
        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.add(new JLabel("Log"), BorderLayout.WEST);
        logHeader.add(clearLog, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(status, BorderLayout.NORTH);
        JPanel logPanel = new JPanel(new BorderLayout(0, 2));
        logPanel.add(logHeader, BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);
        bottom.add(logPanel, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, bottom);
        split.setResizeWeight(0.78);
        split.setBorder(null);
        split.setDividerSize(6);
        root.add(split, BorderLayout.CENTER);

        // ---- drag & drop a file anywhere
        TransferHandler dnd = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty() && !busy) openFile(files.get(0));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        root.setTransferHandler(dnd);
        bufferView.setTransferHandler(dnd);
    }

    private static JButton actionButton(String text, String tip) {
        JButton b = new JButton(text);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(160, 30));
        b.setFocusable(false);
        return b;
    }

    private static void addSection(JPanel p, GridBagConstraints g, String title, JButton... buttons) {
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(MUTED);
        g.insets = new Insets(p.getComponentCount() == 0 ? 0 : 12, 2, 3, 0);
        p.add(l, g);
        g.insets = new Insets(2, 0, 2, 0);
        for (JButton b : buttons) p.add(b, g);
    }

    private static JLabel bold(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private void applyIcons() {
        Theme.setIcon(refreshBtn, "refresh");
        Theme.setIcon(connectBtn, "connect");
        Theme.setIcon(detectBtn, "detect");
        Theme.setIcon(readBtn, "read");
        Theme.setIcon(writeBtn, "write");
        Theme.setIcon(verifyBtn, "verify");
        Theme.setIcon(blankBtn, "blank");
        Theme.setIcon(eraseBtn, "erase");
        Theme.setIcon(crcBtn, "checksum");
        Theme.setIcon(openBtn, "open");
        Theme.setIcon(saveBufBtn, "save");
        Theme.setIcon(saveChipBtn, "save_chip");
        Theme.setIcon(newBtn, "new");
        Theme.setIcon(copyBtn, "copy");
        Theme.setIcon(fillBtn, "fill");
        Theme.setIcon(compareBtn, "compare");
        Theme.setIcon(cancelBtn, "cancel");
        Theme.setIcon(nextDiffBtn, "next_diff");
        Theme.setIcon(hexBtn, "hex");
        Theme.setIcon(binBtn, "binary");
    }

    // ------------------------------------------------------------ wiring

    private void wireActions() {
        refreshBtn.addActionListener(e -> refreshPorts());
        connectBtn.addActionListener(e -> {
            if (connected) disconnect();
            else connect();
        });
        chipBox.addActionListener(e -> chipChanged());
        detectBtn.addActionListener(e -> detect());

        readBtn.addActionListener(e -> readChip());
        writeBtn.addActionListener(e -> writeChip());
        verifyBtn.addActionListener(e -> verifyChip());
        blankBtn.addActionListener(e -> blankCheck());
        eraseBtn.addActionListener(e -> eraseChip());
        crcBtn.addActionListener(e -> checksum());

        openBtn.addActionListener(e -> openFileDialog());
        saveBufBtn.addActionListener(e -> saveDialog(true));
        saveChipBtn.addActionListener(e -> saveDialog(false));
        newBtn.addActionListener(e -> newBlank());
        copyBtn.addActionListener(e -> copyChipToBuffer());
        fillBtn.addActionListener(e -> fillDialog());
        compareBtn.addActionListener(e -> compare(true));
        cancelBtn.addActionListener(e -> requestCancel());

        hexBtn.addActionListener(e -> setBinary(false));
        binBtn.addActionListener(e -> setBinary(true));
        gotoField.addActionListener(e -> goTo());
        nextDiffBtn.addActionListener(e -> nextDiff());
        tabs.addChangeListener(e -> updateDiffLabel());

        bufferView.setOnEdit(() -> {
            bufferDirty = true;
            updateTitles();
        });

        key("ctrl O", "open", this::openFileDialog);
        key("ctrl S", "save", () -> saveDialog(true));
        key("F5", "read", this::readChip);
        key("F6", "write", this::writeChip);
        key("F7", "verify", this::verifyChip);
        key("ESCAPE", "cancel", this::requestCancel);
        key("ctrl G", "goto", () -> {
            gotoField.requestFocusInWindow();
            gotoField.selectAll();
        });
    }

    private void key(String stroke, String name, final Runnable r) {
        JComponent c = getRootPane();
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    // ------------------------------------------------------------ prefs

    private void loadPrefs() {
        ChipType c = ChipType.byLabel(prefs.get("chip", "24C256"));
        suppressChipEvent = true;
        chipBox.setSelectedItem(c != null ? c : ChipType.C24C256);
        suppressChipEvent = false;
        if (prefs.getBoolean("binary", false)) {
            binBtn.setSelected(true);
            setBinary(true);
        }
    }

    private File lastDir() {
        String d = prefs.get("dir", null);
        return d == null ? null : new File(d);
    }

    // ------------------------------------------------------------ connection

    private void refreshPorts() {
        String keep = (String) portBox.getSelectedItem();
        if (keep == null) keep = prefs.get("port", null);
        String[] ports = SerialLink.listPorts();
        portBox.setModel(new DefaultComboBoxModel<String>(ports));
        if (ports.length == 0) {
            portBox.setModel(new DefaultComboBoxModel<String>(new String[]{"(no ports)"}));
        } else if (keep != null) {
            portBox.setSelectedItem(keep);
        }
        updateUi();
    }

    private void connect() {
        final String port = (String) portBox.getSelectedItem();
        if (port == null || port.startsWith("(")) {
            setResult("No serial port. Plug in the Nano and click Refresh.", false);
            return;
        }
        prefs.put("port", port);
        setConnState("Connecting…", WARN_ORANGE);
        runJob("Connect", () -> {
            try {
                link.open(port);
                log("Opened " + port + ", waiting for the board…");
                int v = prog.handshake();
                if (v != Programmer.FIRMWARE_VERSION) {
                    throw new IOException("Board has firmware v" + v + ", this app needs v"
                            + Programmer.FIRMWARE_VERSION + ". Upload eeprom_programmer.ino.");
                }
                connected = true;
                ui(() -> setConnState("● " + port + "  ·  firmware v" + v, OK_GREEN));
                log("Board answered (firmware v" + v + ").");
            } catch (Exception e) {
                link.close();
                connected = false;
                ui(() -> setConnState("Not connected", MUTED));
                throw e;
            }
            detectNow(true);
        });
    }

    private void disconnect() {
        if (busy) return;
        link.close();
        connected = false;
        setConnState("Not connected", MUTED);
        log("Disconnected.");
        setResult(" ", null);
        updateUi();
    }

    private void setConnState(String text, Color c) {
        connLabel.setText(text);
        connLabel.setForeground(c);
    }

    // ------------------------------------------------------------ chip selection

    private ChipType chip() {
        return (ChipType) chipBox.getSelectedItem();
    }

    private void updateChipInfo() {
        chipInfo.setText(chip().details());
    }

    private void chipChanged() {
        updateChipInfo();
        prefs.put("chip", chip().label);
        if (suppressChipEvent) return;
        if (bufferView.hasData() && bufferView.getData().length > chip().size) {
            log(String.format("Note: the buffer (%,d B) is larger than %s (%,d B).",
                    bufferView.getData().length, chip().label, chip().size));
        }
        if (connected && !busy) {
            final ChipType c = chip();
            runJob("Select chip", () -> {
                prog.setChip(c);
                log("Chip set to " + c.label + ".");
                ui(() -> setResult(c.label + " selected", true));
            });
        }
    }

    private void selectChipQuietly(ChipType c) {
        suppressChipEvent = true;
        chipBox.setSelectedItem(c);
        suppressChipEvent = false;
        updateChipInfo();
    }

    private void detect() {
        if (!connected || busy) return;
        runJob("Detect", () -> detectNow(false));
    }

    /** Runs on the job thread. */
    private void detectNow(boolean onConnect) throws Exception {
        int size = prog.detect();
        final ChipType found = ChipType.bySize(size);
        if (found == null) {
            final ChipType keep = chip();
            prog.setChip(keep);
            log(size == 0 ? "No chip answers on I2C (check wiring / chip seated)." : "Unknown size " + size + " bytes.");
            ui(() -> setResult("No chip detected - using " + keep.label + " as selected", false));
            return;
        }
        log("Detected " + found.label + " (" + String.format("%,d", found.size) + " bytes).");
        ui(() -> {
            selectChipQuietly(found);
            setResult((onConnect ? "Connected  ·  " : "") + "Detected " + found.label, true);
        });
    }

    // ------------------------------------------------------------ chip operations

    private void readChip() {
        if (!connected || busy) return;
        final ChipType c = chip();
        final byte[] img = new byte[c.size];
        Arrays.fill(img, (byte) 0xFF);
        chipView.setData(img);
        chipSource = c.label + " read";
        updateTitles();
        tabs.setSelectedComponent(chipView);
        runJob("Read", () -> {
            try {
                for (int a = 0; a < c.size; a += Programmer.BLOCK) {
                    checkCancel();
                    int n = Math.min(Programmer.BLOCK, c.size - a);
                    byte[] d = prog.read(a, n);
                    System.arraycopy(d, 0, img, a, n);
                    final int done = a + n;
                    progress("Reading", done, c.size, chipView, done, 0);
                }
            } catch (CancelledException e) {
                ui(() -> {
                    chipView.setData(null);
                    chipSource = "";
                    updateTitles();
                });
                throw e;
            }
            final int crc = Programmer.crc16(img, 0, img.length, 0xFFFF);
            final String t = elapsed();
            log(String.format("Read %,d bytes from %s in %s, CRC-16 0x%04X.", c.size, c.label, t, crc));
            ui(() -> {
                chipView.refreshRange(0, img.length);
                chipView.scrollToTop();
                setResult(String.format("Read OK  ·  %,d bytes  ·  %s  ·  CRC 0x%04X", c.size, t, crc), true);
                if (bufferView.hasData()) compare(false);
            });
        });
    }

    private void writeChip() {
        if (!connected || busy) return;
        final byte[] buf = bufferView.getData();
        if (buf == null) {
            setResult("Nothing to write - open a file or create a buffer first", false);
            return;
        }
        final ChipType c = chip();
        int len = buf.length;
        String msg;
        if (len > c.size) {
            int r = JOptionPane.showConfirmDialog(this,
                    String.format("The buffer is %,d bytes but a %s holds only %,d bytes.%n%n"
                            + "Write the first %,d bytes only?", len, c.label, c.size, c.size),
                    "Buffer larger than chip", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            len = c.size;
            msg = null;
        } else {
            msg = String.format("Write %,d bytes to the %s?%n", len, c.label)
                    + (len < c.size ? String.format("(The buffer is %,d byte%s shorter than the chip; the rest of the chip is left as it is.)%n",
                            c.size - len, c.size - len == 1 ? "" : "s") : "")
                    + "%nThe current chip content will be overwritten.";
        }
        if (msg != null) {
            int r = JOptionPane.showConfirmDialog(this, String.format(msg), "Write chip",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
        }
        final int total = len;
        tabs.setSelectedComponent(bufferView);
        bufferView.setDiff(null);
        runJob("Write", () -> {
            try {
                for (int a = 0; a < total; a += Programmer.BLOCK) {
                    checkCancel();
                    int n = Math.min(Programmer.BLOCK - (a % Programmer.BLOCK), total - a);
                    progress("Writing", a, total, bufferView, a, n);
                    prog.write(a, buf, a, n);
                }
            } catch (Programmer.VerifyException e) {
                final int at = e.address;
                ui(() -> {
                    BitSet b = new BitSet();
                    b.set(at);
                    bufferView.setDiff(b);
                    bufferView.goTo(at);
                    updateDiffLabel();
                });
                throw new IOException(e.getMessage()
                        + " - is the WP pin tied to GND, and is the right chip type selected?");
            }
            final String t = elapsed();
            log(String.format("Wrote and verified %,d bytes to %s in %s.", total, c.label, t));
            ui(() -> {
                bufferView.scrollToTop();
                setResult(String.format("Write OK  ·  %,d bytes written and verified  ·  %s", total, t), true);
            });
        });
    }

    private void verifyChip() {
        if (!connected || busy) return;
        final byte[] buf = bufferView.getData();
        if (buf == null) {
            setResult("Nothing to verify against - open a file first", false);
            return;
        }
        final ChipType c = chip();
        final int total = Math.min(buf.length, c.size);
        final byte[] img = new byte[total];
        Arrays.fill(img, (byte) 0xFF);
        chipView.setData(img);
        chipSource = c.label + " read (first " + String.format("%,d", total) + " B)";
        updateTitles();
        tabs.setSelectedComponent(chipView);
        runJob("Verify", () -> {
            final BitSet diff = new BitSet();
            for (int a = 0; a < total; a += Programmer.BLOCK) {
                checkCancel();
                int n = Math.min(Programmer.BLOCK, total - a);
                byte[] d = prog.read(a, n);
                System.arraycopy(d, 0, img, a, n);
                for (int i = 0; i < n; i++) if (d[i] != buf[a + i]) diff.set(a + i);
                progress("Verifying", a + n, total, chipView, a + n, 0);
            }
            final String t = elapsed();
            final int bad = diff.cardinality();
            log(bad == 0 ? String.format("Verify OK: %,d bytes match (%s).", total, t)
                    : String.format("Verify FAILED: %,d of %,d bytes differ, first at 0x%05X.", bad, total, diff.nextSetBit(0)));
            ui(() -> {
                chipView.refreshRange(0, total);
                bufferView.setDiff(diff);
                chipView.setDiff(diff);
                updateDiffLabel();
                if (bad == 0) {
                    setResult(String.format("Verify OK  ·  %,d bytes match  ·  %s", total, t), true);
                } else {
                    setResult(String.format("Verify FAILED  ·  %,d bytes differ (red)", bad), false);
                    chipView.goTo(diff.nextSetBit(0));
                }
            });
        });
    }

    private void blankCheck() {
        if (!connected || busy) return;
        final ChipType c = chip();
        runJob("Blank check", () -> {
            for (int a = 0; a < c.size; a += Programmer.BULK) {
                checkCancel();
                int n = Math.min(Programmer.BULK, c.size - a);
                progress("Checking", a, c.size, null, 0, 0);
                final int at = prog.blankCheck(a, n, 0xFF);
                if (at >= 0) {
                    log(String.format("Not blank: byte at 0x%05X is not 0xFF.", at));
                    ui(() -> setResult(String.format("Not blank  ·  first used byte at 0x%05X", at), false));
                    return;
                }
            }
            log("Blank check OK: all " + String.format("%,d", c.size) + " bytes are 0xFF.");
            ui(() -> setResult("Chip is blank (all 0xFF)", true));
        });
    }

    private void eraseChip() {
        if (!connected || busy) return;
        final ChipType c = chip();
        int r = JOptionPane.showConfirmDialog(this,
                String.format("Erase the whole %s (%,d bytes)?%n%nEvery byte becomes 0xFF. This cannot be undone.",
                        c.label, c.size),
                "Erase chip", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        runJob("Erase", () -> {
            for (int a = 0; a < c.size; a += Programmer.BULK) {
                checkCancel();
                int n = Math.min(Programmer.BULK, c.size - a);
                progress("Erasing", a, c.size, null, 0, 0);
                prog.fill(a, n, 0xFF);
            }
            final String t = elapsed();
            log(String.format("Erased %s (%,d bytes) in %s.", c.label, c.size, t));
            ui(() -> {
                byte[] img = new byte[c.size];
                Arrays.fill(img, (byte) 0xFF);
                chipView.setData(img);
                chipSource = c.label + " (erased)";
                updateTitles();
                setResult("Erase OK  ·  chip is blank  ·  " + t, true);
            });
        });
    }

    private void checksum() {
        if (!connected || busy) return;
        final ChipType c = chip();
        final byte[] buf = bufferView.getData();
        final int len = buf == null ? c.size : Math.min(buf.length, c.size);
        runJob("Checksum", () -> {
            int crc = 0xFFFF;
            for (int a = 0; a < len; a += Programmer.BULK) {
                checkCancel();
                int n = Math.min(Programmer.BULK, len - a);
                progress("Checksum", a, len, null, 0, 0);
                crc = prog.crc(a, n, crc);
            }
            final int chipCrc = crc;
            final String range = String.format("0x00000–0x%05X", len - 1);
            if (buf == null) {
                log(String.format("Chip CRC-16 (%s) = 0x%04X", range, chipCrc));
                ui(() -> setResult(String.format("Chip CRC-16 = 0x%04X", chipCrc), true));
            } else {
                final int bufCrc = Programmer.crc16(buf, 0, len, 0xFFFF);
                final boolean same = bufCrc == chipCrc;
                log(String.format("CRC-16 over %s: chip 0x%04X, buffer 0x%04X → %s", range, chipCrc, bufCrc,
                        same ? "match" : "DIFFERENT"));
                ui(() -> setResult(String.format("CRC chip 0x%04X  ·  buffer 0x%04X  ·  %s", chipCrc, bufCrc,
                        same ? "match" : "DIFFERENT"), same));
            }
        });
    }

    // ------------------------------------------------------------ buffer operations

    private boolean confirmDiscard() {
        if (!bufferDirty) return true;
        int r = JOptionPane.showConfirmDialog(this, "The buffer has unsaved changes. Discard them?",
                "Unsaved changes", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return r == JOptionPane.OK_OPTION;
    }

    private void openFileDialog() {
        if (busy || !confirmDiscard()) return;
        JFileChooser fc = new JFileChooser(lastDir());
        fc.setDialogTitle("Open file into buffer");
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Binary (*.bin, *.eep, *.rom, *.dat)", "bin", "eep", "rom", "dat"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Intel HEX (*.hex, *.ihx)", "hex", "ihx"));
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileFilter(fc.getAcceptAllFileFilter());
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        bufferDirty = false;
        openFile(fc.getSelectedFile());
    }

    private void openFile(File f) {
        if (bufferDirty && !confirmDiscard()) return;
        try {
            byte[] d = HexFiles.load(f);
            if (d.length == 0) {
                setResult("File is empty: " + f.getName(), false);
                return;
            }
            prefs.put("dir", f.getParent() == null ? "" : f.getParent());
            bufferFile = f;
            bufferDirty = false;
            bufferView.setData(d);
            chipView.setDiff(null);
            tabs.setSelectedComponent(bufferView);
            updateTitles();
            updateDiffLabel();
            log(String.format("Opened %s (%,d bytes).", f.getName(), d.length));
            if (d.length > chip().size) {
                setResult(String.format("Opened %s  ·  %,d bytes is larger than the %s (%,d bytes)",
                        f.getName(), d.length, chip().label, chip().size), false);
            } else {
                setResult(String.format("Opened %s  ·  %,d bytes", f.getName(), d.length), true);
            }
            updateUi();
        } catch (IOException e) {
            setResult("Could not open " + f.getName() + ": " + e.getMessage(), false);
            log("ERROR opening " + f + ": " + e.getMessage());
        }
    }

    private void saveDialog(boolean buffer) {
        if (busy) return;
        byte[] d = buffer ? bufferView.getData() : chipView.getData();
        if (d == null) return;
        JFileChooser fc = new JFileChooser(lastDir());
        fc.setDialogTitle(buffer ? "Save buffer" : "Save chip read");
        FileNameExtensionFilter bin = new FileNameExtensionFilter("Binary (*.bin)", "bin");
        FileNameExtensionFilter hex = new FileNameExtensionFilter("Intel HEX (*.hex)", "hex");
        fc.addChoosableFileFilter(bin);
        fc.addChoosableFileFilter(hex);
        fc.setFileFilter(bin);
        String name = buffer && bufferFile != null ? bufferFile.getName()
                : chip().label.toLowerCase() + (buffer ? "_buffer.bin" : "_read.bin");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), name));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        String n = f.getName().toLowerCase();
        if (!n.contains(".")) f = new File(f.getPath() + (fc.getFileFilter() == hex ? ".hex" : ".bin"));
        if (f.exists()) {
            int r = JOptionPane.showConfirmDialog(this, f.getName() + " already exists. Replace it?",
                    "Save", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;
        }
        try {
            HexFiles.save(f, d);
            prefs.put("dir", f.getParent() == null ? "" : f.getParent());
            if (buffer) {
                bufferFile = f;
                bufferDirty = false;
                updateTitles();
            }
            log(String.format("Saved %,d bytes to %s.", d.length, f));
            setResult("Saved " + f.getName(), true);
        } catch (IOException e) {
            setResult("Save failed: " + e.getMessage(), false);
            log("ERROR saving " + f + ": " + e.getMessage());
        }
    }

    private void newBlank() {
        if (busy || !confirmDiscard()) return;
        byte[] d = new byte[chip().size];
        Arrays.fill(d, (byte) 0xFF);
        bufferFile = null;
        bufferDirty = false;
        bufferView.setData(d);
        chipView.setDiff(null);
        tabs.setSelectedComponent(bufferView);
        updateTitles();
        updateDiffLabel();
        setResult(String.format("New blank buffer  ·  %,d bytes (0xFF)", d.length), true);
        updateUi();
    }

    private void copyChipToBuffer() {
        if (busy || !chipView.hasData() || !confirmDiscard()) return;
        byte[] d = chipView.getData().clone();
        bufferFile = null;
        bufferDirty = true;
        bufferView.setData(d);
        chipView.setDiff(null);
        tabs.setSelectedComponent(bufferView);
        updateTitles();
        updateDiffLabel();
        setResult(String.format("Copied %,d bytes from the chip read into the buffer", d.length), true);
        updateUi();
    }

    private void fillDialog() {
        if (busy) return;
        if (!bufferView.hasData()) newBlank();
        if (!bufferView.hasData()) return;
        byte[] d = bufferView.getData();
        int[] sel = tabs.getSelectedComponent() == bufferView ? bufferView.selectedRange() : null;
        JTextField from = new JTextField(sel == null ? "0" : Integer.toHexString(sel[0]).toUpperCase(), 8);
        JTextField to = new JTextField(Integer.toHexString((sel == null ? d.length : sel[1]) - 1).toUpperCase(), 8);
        JTextField val = new JTextField("FF", 4);
        JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
        p.add(new JLabel("From address (hex)"));
        p.add(from);
        p.add(new JLabel("To address (hex, inclusive)"));
        p.add(to);
        p.add(new JLabel("Value (hex)"));
        p.add(val);
        if (JOptionPane.showConfirmDialog(this, p, "Fill buffer", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            int a = Integer.parseInt(from.getText().trim(), 16);
            int b = Integer.parseInt(to.getText().trim(), 16);
            int v = Integer.parseInt(val.getText().trim(), 16);
            if (a < 0 || b < a || b >= d.length || v < 0 || v > 255) throw new NumberFormatException();
            Arrays.fill(d, a, b + 1, (byte) v);
            bufferView.refreshRange(a, b + 1);
            bufferDirty = true;
            updateTitles();
            log(String.format("Filled 0x%05X–0x%05X with 0x%02X.", a, b, v));
            setResult(String.format("Filled %,d bytes with 0x%02X", b - a + 1, v), true);
        } catch (NumberFormatException e) {
            setResult(String.format("Fill: enter hex values, addresses 0–%X, value 00–FF", d.length - 1), false);
        }
    }

    private void compare(boolean verbose) {
        if (!bufferView.hasData() || !chipView.hasData()) return;
        byte[] a = bufferView.getData(), b = chipView.getData();
        int n = Math.min(a.length, b.length);
        BitSet diff = new BitSet();
        for (int i = 0; i < n; i++) if (a[i] != b[i]) diff.set(i);
        bufferView.setDiff(diff);
        chipView.setDiff(diff);
        updateDiffLabel();
        int bad = diff.cardinality();
        String extra = a.length != b.length ? String.format("  (compared first %,d bytes; sizes differ)", n) : "";
        if (verbose || bad > 0) {
            log(bad == 0 ? "Compare: buffer and chip read are identical" + extra + "."
                    : String.format("Compare: %,d bytes differ, first at 0x%05X%s.", bad, diff.nextSetBit(0), extra));
        }
        if (verbose) {
            setResult(bad == 0 ? "Buffer and chip read are identical" + extra
                    : String.format("%,d bytes differ (red)%s", bad, extra), bad == 0);
        }
    }

    // ------------------------------------------------------------ view helpers

    private HexView activeView() {
        return tabs.getSelectedComponent() == chipView ? chipView : bufferView;
    }

    private void setBinary(boolean b) {
        bufferView.setBinary(b);
        chipView.setBinary(b);
        prefs.putBoolean("binary", b);
    }

    private void goTo() {
        String s = gotoField.getText().trim();
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            int a = Integer.parseInt(s, 16);
            HexView v = activeView();
            if (!v.hasData() || a >= v.getData().length) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            v.goTo(a);
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void nextDiff() {
        HexView v = activeView();
        BitSet d = v.getDiff();
        if (d.isEmpty()) return;
        int[] sel = v.selectedRange();
        int from = sel == null ? 0 : sel[0] + 1;
        int next = d.nextSetBit(from);
        if (next < 0) next = d.nextSetBit(0);
        v.goTo(next);
    }

    private void updateDiffLabel() {
        int n = activeView().getDiff().cardinality();
        diffLabel.setText(n == 0 ? " " : String.format("%,d difference%s", n, n == 1 ? "" : "s"));
        diffLabel.setForeground(ERR_RED);
        nextDiffBtn.setEnabled(n > 0);
    }

    private void updateTitles() {
        byte[] b = bufferView.getData();
        String bt = "Buffer";
        if (b != null) {
            bt += "  ·  " + (bufferFile != null ? bufferFile.getName() : "untitled") + String.format("  ·  %,d B", b.length)
                    + (bufferDirty ? "  •" : "");
        }
        tabs.setTitleAt(0, bt);
        byte[] c = chipView.getData();
        tabs.setTitleAt(1, c == null ? "Chip" : "Chip  ·  " + chipSource + String.format("  ·  %,d B", c.length));
        updateUi();
    }

    // ------------------------------------------------------------ enable / disable

    private void updateUi() {
        boolean idle = !busy;
        boolean on = connected && idle;
        boolean hasBuf = bufferView.hasData();
        boolean hasChip = chipView.hasData();
        boolean havePorts = portBox.getItemCount() > 0 && !String.valueOf(portBox.getItemAt(0)).startsWith("(");

        portBox.setEnabled(!connected && idle);
        refreshBtn.setEnabled(!connected && idle);
        connectBtn.setEnabled(idle && (connected || havePorts));
        connectBtn.setText(connected ? "Disconnect" : "Connect");
        Theme.setIcon(connectBtn, connected ? "disconnect" : "connect");
        chipBox.setEnabled(idle);
        detectBtn.setEnabled(on);

        readBtn.setEnabled(on);
        writeBtn.setEnabled(on && hasBuf);
        verifyBtn.setEnabled(on && hasBuf);
        blankBtn.setEnabled(on);
        eraseBtn.setEnabled(on);
        crcBtn.setEnabled(on);

        openBtn.setEnabled(idle);
        saveBufBtn.setEnabled(idle && hasBuf);
        saveChipBtn.setEnabled(idle && hasChip);
        newBtn.setEnabled(idle);
        copyBtn.setEnabled(idle && hasChip);
        fillBtn.setEnabled(idle);
        compareBtn.setEnabled(idle && hasBuf && hasChip);
        cancelBtn.setEnabled(busy);
        bufferView.setEditable(idle);
    }

    // ------------------------------------------------------------ job runner

    private void runJob(final String name, final Job job) {
        if (busy) return;
        busy = true;
        cancel = false;
        jobStart = System.currentTimeMillis();
        lastUiUpdate = 0;
        progress.setValue(0);
        progress.setString(name + "…");
        setResult(name + "…", null);
        updateUi();

        Thread t = new Thread(() -> {
            String err = null;
            boolean cancelled = false;
            try {
                job.run();
            } catch (CancelledException | InterruptedException e) {
                cancelled = true;
            } catch (Exception e) {
                err = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String error = err;
            final boolean wasCancelled = cancelled;
            ui(() -> {
                busy = false;
                bufferView.clearProgress();
                chipView.clearProgress();
                if (wasCancelled) {
                    progress.setValue(0);
                    progress.setString("Cancelled");
                    setResult(name + " cancelled", null);
                    log(name + " cancelled.");
                } else if (error != null) {
                    progress.setString(name + " failed");
                    setResult(name + " failed: " + error, false);
                    log("ERROR (" + name + "): " + error);
                } else {
                    progress.setValue(1000);
                    progress.setString(name + " done");
                }
                updateUi();
            });
        }, "eeprom-job");
        t.setDaemon(true);
        t.start();
    }

    private void requestCancel() {
        if (busy) {
            cancel = true;
            progress.setString("Cancelling…");
        }
    }

    private void checkCancel() throws CancelledException {
        if (cancel) throw new CancelledException();
    }

    /** Called from the job thread. Throttled to ~15 updates per second. */
    private void progress(final String verb, final int done, final int total, final HexView view,
            final int viewDone, final int viewCur) {
        long now = System.currentTimeMillis();
        if (now - lastUiUpdate < 66 && done < total) return;
        lastUiUpdate = now;
        double secs = Math.max(0.001, (now - jobStart) / 1000.0);
        double rate = done / secs;
        String eta = rate > 0 && done > 0 ? fmtTime((long) ((total - done) / rate)) + " left" : "";
        final String text = String.format("%s  %,d / %,d B  ·  %.1f KB/s  %s", verb, done, total, rate / 1024, eta);
        final int val = total == 0 ? 0 : (int) (1000L * done / total);
        ui(() -> {
            progress.setValue(val);
            progress.setString(text);
            if (view != null) view.setProgress(viewDone, viewCur);
        });
    }

    private String elapsed() {
        return fmtTime((System.currentTimeMillis() - jobStart) / 1000);
    }

    private static String fmtTime(long s) {
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------ misc

    private static void ui(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private void setResult(String text, Boolean ok) {
        resultLabel.setText(text);
        resultLabel.setForeground(ok == null ? Theme.TEXT_DIM : ok ? OK_GREEN : ERR_RED);
    }

    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss");

    private void log(final String s) {
        ui(() -> {
            logArea.append(clock.format(new Date()) + "  " + s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void quit() {
        if (busy) {
            int r = JOptionPane.showConfirmDialog(this, "An operation is still running. Stop it and quit?",
                    "Quit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            cancel = true;
        } else if (!confirmDiscard()) {
            return;
        }
        link.close();
        dispose();
        System.exit(0);
    }
}
