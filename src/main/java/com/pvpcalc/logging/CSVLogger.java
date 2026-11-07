package com.pvpcalc.logging;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.config.PvPCalcConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Thread-safe, Excel-friendly CSV writer (UTF-8 BOM, CRLF, no timestamps). */
public final class CSVLogger {
    public static final String HEADER = "Event,Kit Context,Details";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String EOL = "\r\n";

    private CSVLogger() {}

    public static Path file() {
        return PvPCalcConfig.directory().resolve("session_data.csv");
    }

    /** Creates the file with the header if it is missing or empty. */
    public static synchronized void ensureFile() {
        try {
            Files.createDirectories(PvPCalcConfig.directory());
            Path f = file();
            if (!Files.exists(f) || Files.size(f) == 0) {
                writeHeader(f);
            }
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not prepare CSV file", e);
        }
    }

    public static synchronized void append(String event, String kitContext, String details) {
        try {
            ensureFile();
            String line = escape(event) + "," + escape(kitContext) + "," + escape(details) + EOL;
            try (FileChannel ch = FileChannel.open(file(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ch.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                ch.force(true); // flush to disk so events survive a crash
            }
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not append to CSV", e);
        }
    }

    /** Clears only the CSV file and recreates it with the header. */
    public static synchronized boolean reset() {
        try {
            Files.createDirectories(PvPCalcConfig.directory());
            writeHeader(file());
            return true;
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not reset CSV", e);
            return false;
        }
    }

    private static void writeHeader(Path f) throws IOException {
        byte[] head = (HEADER + EOL).getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[BOM.length + head.length];
        System.arraycopy(BOM, 0, all, 0, BOM.length);
        System.arraycopy(head, 0, all, BOM.length, head.length);
        Files.write(f, all, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    static String escape(String s) {
        if (s == null) return "";
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String out = s.replace("\"", "\"\"");
        return quote ? "\"" + out + "\"" : out;
    }
}
