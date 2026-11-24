package eepromprogrammer;

import java.io.IOException;

/** A line-based, request/response text channel (the serial port). */
public interface LineChannel {

    void writeLine(String line) throws IOException;

    /** @return the next line, or null on timeout */
    String readLine(long timeoutMs) throws IOException, InterruptedException;

    /** Drop anything received but not read yet. */
    void clearInput();
}
