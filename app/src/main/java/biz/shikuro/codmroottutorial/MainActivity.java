package biz.shikuro.codmroottutorial;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PACKAGE = "com.garena.game.codm";
    private static final String REMOTE_HELPER = "/data/local/tmp/codm_root_patcher";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(28), dp(22), dp(28));
        body.setBackgroundColor(Color.rgb(16, 20, 24));

        TextView title = text("CODM Root Tutorial", 25, Color.WHITE);
        body.addView(title);
        TextView sub = text("Patch eksternal root • tanpa Zygisk", 14, Color.rgb(160, 174, 184));
        body.addView(sub, margin(dp(8), dp(22)));

        body.addView(button("CEK ROOT", v -> runTask("Mengecek root…", () -> {
            ShellResult r = shell("id");
            return r.ok() && r.output.contains("uid=0") ? "Root aktif\n" + r.output : "Root gagal\n" + r.output;
        })));

        body.addView(button("BUKA CODM + TERAPKAN", v -> runTask("Menunggu libunity.so…", this::launchAndPatch)));
        body.addView(button("TERAPKAN SEKARANG", v -> runTask("Menerapkan patch…", () -> runHelper("apply"))));
        body.addView(button("PULIHKAN BYTE ASLI", v -> runTask("Memulihkan…", () -> runHelper("restore"))));

        status = text("Status: belum diperiksa", 14, Color.rgb(218, 226, 232));
        status.setTextIsSelectable(true);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackgroundColor(Color.rgb(27, 34, 40));
        body.addView(status, margin(dp(18), 0));

        TextView note = text("Khusus build libunity yang cocok. Helper menolak patch jika byte asli berbeda.", 12,
                Color.rgb(145, 156, 164));
        body.addView(note, margin(dp(14), 0));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
    }

    private String launchAndPatch() throws Exception {
        ShellResult launch = shell("monkey -p " + PACKAGE + " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1");
        if (!launch.ok()) return "Gagal membuka CODM\n" + launch.output;
        String last = "";
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            last = runHelper("apply");
            if (last.contains("PATCH_OK") || last.contains("ALREADY_PATCHED")) return last;
            if (last.contains("BYTE_MISMATCH")) return last;
        }
        return "Timeout: libunity.so belum siap.\n" + last;
    }

    private String runHelper(String action) throws Exception {
        File local = installAsset();
        String prepare = "cp '" + local.getAbsolutePath() + "' " + REMOTE_HELPER
                + " && chmod 0755 " + REMOTE_HELPER;
        ShellResult copied = shell(prepare);
        if (!copied.ok()) return "Gagal menyiapkan helper:\n" + copied.output;
        ShellResult result = shell(REMOTE_HELPER + " " + action + " " + PACKAGE);
        return result.output.isEmpty() ? "Helper selesai dengan kode " + result.code : result.output;
    }

    private File installAsset() throws Exception {
        File out = new File(getCacheDir(), "codm_root_patcher");
        try (InputStream in = getAssets().open("codm_root_patcher");
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        out.setReadable(true, false);
        return out;
    }

    private ShellResult shell(String command) throws Exception {
        Process p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = p.getInputStream().read(chunk)) != -1) buffer.write(chunk, 0, count);
        byte[] bytes = buffer.toByteArray();
        int code = p.waitFor();
        return new ShellResult(code, new String(bytes, StandardCharsets.UTF_8).trim());
    }

    private void runTask(String initial, ThrowingSupplier task) {
        status.setText(initial);
        executor.submit(() -> {
            try {
                String result = task.get();
                main.post(() -> status.setText(result));
            } catch (Exception e) {
                main.post(() -> status.setText("Error: " + e));
            }
        });
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.rgb(0, 137, 112));
        b.setOnClickListener(click);
        b.setLayoutParams(margin(dp(8), dp(2)));
        return b;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams margin(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private interface ThrowingSupplier { String get() throws Exception; }
    private static final class ShellResult {
        final int code;
        final String output;
        ShellResult(int code, String output) { this.code = code; this.output = output; }
        boolean ok() { return code == 0; }
    }
}
