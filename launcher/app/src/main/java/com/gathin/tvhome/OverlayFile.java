package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * The pushed config.json (Config.overlay), read from external storage only when its length or modification time
 * changed since this process last read it. Home asks for it several times on every start (Config, the account, Phone
 * notes, First run): before, each ask read the file, parsed it and wrote the copy again. A push from the tools is
 * followed by a force stop, which starts a new process anyway.
 *
 * <p>The copy in the app's own files is what is read before external storage is mounted (seen at every boot on a
 * Xiaomi MiTV-MTEQ0); it is written when the file changed, and deleted when the file went or can't be read.
 */
final class OverlayFile {
    static final int MAX_BYTES = 64 * 1024;

    private final Consumer<String> warn;
    /** The stamp ("absent" or length@mtime) of the file this process last read while storage was mounted. */
    private String stamp;
    /** That file's text, or null when it was absent, too large or not JSON. */
    private String text;
    /** How many times the file was read and the copy written or deleted; for the tests. */
    int reads, copies;

    OverlayFile(Consumer<String> warn) {
        this.warn = warn;
    }

    /** The overlay's text, or null for none. file may be null (no external storage). */
    synchronized String read(File file, File copy, boolean mounted) {
        if (!mounted) return readCopy(copy);
        String now = stamp(file);
        if (now.equals(stamp)) return text;
        reads++;
        ioError = false;
        String t = readText(file);
        copies++;
        if (t == null) {
            if (copy.exists() && !copy.delete()) {
                ioError = true;
                warn.accept("could not delete the copy of " + file);
            }
        } else {
            try (FileOutputStream out = new FileOutputStream(copy)) {
                out.write(t.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                ioError = true;
                warn.accept("could not keep a copy of " + file + ": " + e.getMessage());
            }
        }
        // A file that couldn't be read, copied or have its copy deleted is tried again next time, as before; bad
        // JSON or size waits for the file to change.
        stamp = ioError ? null : now;
        text = t;
        return t;
    }

    /** Before storage is mounted: the copy, read again only when it changed (several asks at every boot). */
    private String readCopy(File copy) {
        String now = stamp(copy);
        if (now.equals(copyStamp)) return copyText;
        copyReads++;
        ioError = false;
        String t = readText(copy);
        copyStamp = ioError ? null : now;
        copyText = t;
        return t;
    }

    /** The copy's stamp and text as last read while storage was unmounted. */
    private String copyStamp, copyText;
    /** How many times the copy was read; for the tests. */
    int copyReads;

    /** Set when the last read or copy failed on I/O rather than on the file's content. */
    private boolean ioError;

    static String stamp(File file) {
        return file != null && file.isFile() ? file.length() + "@" + file.lastModified() : "absent";
    }

    /** The file's text if it is JSON of at most 64 KB, without a leading BOM; else null, with a warning. */
    private String readText(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > MAX_BYTES) {
            warn.accept("ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String t = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (t.startsWith("\uFEFF")) t = t.substring(1);   // Windows editors add a BOM
            new JSONObject(t);
            return t;
        } catch (IOException e) {
            ioError = true;
            warn.accept("ignoring " + file + ": " + e.getMessage());
            return null;
        } catch (JSONException | RuntimeException e) {
            warn.accept("ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }
}
