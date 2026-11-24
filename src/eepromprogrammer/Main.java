package eepromprogrammer;

import javax.swing.SwingUtilities;

/** Entry point. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new MainWindow().setVisible(true);
        });
    }
}
