package org.gsisms.guard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Uses ordinary su or the bundled, unchanged PHH client.
 * The existing daemon verifies peer UID and retains its approval decision.
 */
final class RootClient {
    static final int LIMIT = 4 * 1024 * 1024;
    private String suPath;
    private final String bundledPhh;
    RootClient(String nativeDir) { bundledPhh=nativeDir+"/libphhsu.so"; }
    static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) { if (out.size() + n > LIMIT) throw new IOException("Output too large"); out.write(b,0,n); }
        return out.toByteArray();
    }
    synchronized void connect() throws Exception {
        if (suPath != null) return;
        for (String candidate : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", bundledPhh, "/system/bin/phh-su"}) {
            if (!new File(candidate).canExecute()) continue;
            try { String r = execute(candidate, "id"); if (r.contains("uid=0(root)")) { suPath=candidate; return; } }
            catch (Exception ignored) {}
        }
        throw new IOException("Root 未授权或不可用。请在当前系统的 root 管理器中允许本应用；PHH GSI 需启用 Dynamic Superuser。");
    }
    synchronized String run(String command) throws Exception {
        connect(); return execute(suPath, command);
    }
    private String execute(String binary, String command) throws Exception {
        java.lang.Process p = new ProcessBuilder(binary,"-c",command).start();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<byte[]> out = pool.submit(() -> read(p.getInputStream()));
        Future<byte[]> err = pool.submit(() -> read(p.getErrorStream()));
        try {
            p.getOutputStream().close();
            if (!p.waitFor(100,TimeUnit.SECONDS)) throw new IOException("Root command timed out");
            String stdout = new String(out.get(5,TimeUnit.SECONDS), StandardCharsets.UTF_8);
            String stderr = new String(err.get(5,TimeUnit.SECONDS), StandardCharsets.UTF_8);
            if (p.exitValue()!=0) throw new IOException("Root 操作失败，exit="+p.exitValue()+" "+safeError(stderr));
            return stdout.trim();
        } finally { p.destroy(); pool.shutdownNow(); }
    }
    static String safeError(String s) {
        // Keep application diagnostics from accidentally exposing card IDs or payloads.
        s=s.replaceAll("[0-9A-Fa-f]{16,}","[已隐藏]").replaceAll("[0-9]{7,}","[已隐藏]");
        return s.length()>300?s.substring(0,300):s;
    }
}
