package me.champdev.weblock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pantalla de inicio simplificada: solo las tarjetas que aparecen en la lista "hoy.json".
 *
 * La lista se descarga de LIST_URL cada vez que se abre la app. Si no hay internet se usa la última
 * que se descargó bien, y si nunca se descargó ninguna, la que va dentro de la app (assets/hoy.json).
 *
 * Formato de hoy.json:
 *   { "titulo": "Hoy", "items": [
 *       { "titulo": "Película", "subtitulo": "20:00", "url": "https://..." },
 *       { "titulo": "Partido",  "embed": "<iframe src=\"https://...\"></iframe>", "imagen": "https://.../foto.jpg" }
 *   ] }
 * De cada elemento hace falta "url" o "embed" (el código iframe completo; se extrae el src solo).
 */
public class SimpleHome extends Activity {
    /** CAMBIA ESTO por el enlace "raw" de tu hoy.json en GitHub (ver la guía). */
    static final String LIST_URL = "https://raw.githubusercontent.com/chapu21/android/main/hoy.json";

    static final int BG = 0xFF120E1A, CARD = 0xFF1E1828, ACCENT = 0xFFFF3B5C, TEXT = 0xFFF2EDF7, MUTED = 0xFF9A90A8;
    static final String PREF_CACHE = "simpleList";
    static final Pattern SRC = Pattern.compile("src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    static class Item {
        String title = "", sub = "", url = "", image = "";
    }

    ScrollView page;
    LinearLayout col;
    String heading = "Hoy";
    List<Item> items = new ArrayList<>();
    String status = "";
    String avisoTitle = "Aviso", avisoText = "";
    AlertDialog avisoDialog;
    boolean firstFocusDone;
    int loadId;

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Valor distinto en TV (mando, se ve de lejos) y en móvil. */
    int u(float tv, float phone) {
        return dp(App.tv ? tv : phone);
    }

    float sp(float tv, float phone) {
        return App.tv ? tv : phone;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Si el arranque anterior falló, enseñamos el error (una sola vez) para poder arreglarlo
        String crash = getSharedPreferences("crash", MODE_PRIVATE).getString("trace", null);
        if (crash != null) {
            getSharedPreferences("crash", MODE_PRIVATE).edit().remove("trace").commit();
            showError("La app se cerró por este error:\n\n" + crash);
            return;
        }
        try {
            start();
        } catch (Throwable e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            showError("Error al arrancar:\n\n" + sw);
        }
    }

    void showError(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(12);
        t.setTextIsSelectable(true);
        t.setPadding(dp(16), dp(32), dp(16), dp(16));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(t);
        setContentView(sv);
    }

    void start() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        page = new ScrollView(this);
        page.setBackgroundColor(BG);
        page.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        page.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);
        // Primero lo que ya tenemos (caché o lista incluida) para que no haya pantalla vacía
        parse(readCache());
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (col != null) refresh();
    }


    // ---------- aviso al abrir ----------

    /** Muestra el aviso de hoy.json ("aviso" y, opcional, "avisoTitulo") hasta que se pulsa Entendido. Si el texto cambia, vuelve a salir. */
    void maybeShowAviso() {
        if (avisoText == null || avisoText.trim().isEmpty() || isFinishing() || isDestroyed()) return;
        if (avisoDialog != null && avisoDialog.isShowing()) return;
        final String key = Integer.toHexString((avisoTitle + "|" + avisoText).hashCode());
        if (key.equals(App.prefs.getString("avisoVisto", ""))) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(CARD);
        boxBg.setCornerRadius(dp(22));
        boxBg.setStroke(dp(2), ACCENT);
        box.setBackground(boxBg);
        box.setPadding(u(40, 24), u(32, 24), u(40, 24), u(32, 24));

        TextView t = new TextView(this);
        t.setText(avisoTitle);
        t.setTextColor(ACCENT);
        t.setTextSize(sp(34, 24));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, u(16, 12));
        box.addView(t);

        TextView m = new TextView(this);
        m.setText(avisoText);
        m.setTextColor(TEXT);
        m.setTextSize(sp(30, 21));
        m.setLineSpacing(0, 1.15f);
        ScrollView ms = new ScrollView(this);
        ms.addView(m);
        box.addView(ms, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ok = new TextView(this);
        ok.setText("Entendido");
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(sp(30, 22));
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(u(24, 16), u(18, 14), u(24, 16), u(18, 14));
        makeFocusable(ok);
        GradientDrawable okBg = new GradientDrawable();
        okBg.setColor(ACCENT);
        okBg.setCornerRadius(dp(18));
        ok.setBackground(okBg);
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okp.topMargin = u(24, 18);
        box.addView(ok, okp);

        avisoDialog = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        ok.setOnClickListener(v -> {
            App.prefs.edit().putString("avisoVisto", key).apply();
            avisoDialog.dismiss();
        });
        avisoDialog.show();
        if (avisoDialog.getWindow() != null) {
            avisoDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(App.tv ? 900 : 480));
            avisoDialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ok.post(ok::requestFocus);
    }

    @Override protected void onDestroy() {
        if (avisoDialog != null && avisoDialog.isShowing()) avisoDialog.dismiss();
        super.onDestroy();
    }

    // ---------- datos ----------

    void refresh() {
        final int id = ++loadId;
        if (LIST_URL.contains("TU_USUARIO")) { maybeShowAviso(); return; }   // aún sin configurar: se queda con la lista incluida
        App.io.execute(() -> {
            String text = null;
            try {
                text = download(LIST_URL);
                new JSONObject(text.trim().startsWith("[") ? "{\"items\":" + text + "}" : text);   // comprueba que es JSON válido
            } catch (Exception e) {
                text = null;
            }
            final String ok = text;
            App.main.post(() -> {
                if (isDestroyed() || id != loadId) return;
                if (ok != null) {
                    App.prefs.edit().putString(PREF_CACHE, ok).apply();
                    status = "";
                    parse(ok);
                } else {
                    status = items.isEmpty() ? "No se puede cargar la lista. Comprueba internet." : "";
                }
                build();
                maybeShowAviso();
            });
        });
    }

    String readCache() {
        String cached = App.prefs.getString(PREF_CACHE, null);
        if (cached != null) return cached;
        try (InputStream in = getAssets().open("hoy.json")) {
            return new String(readAll(in), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    void parse(String text) {
        List<Item> list = new ArrayList<>();
        String head = "Hoy";
        String aTitle = "Aviso", aText = "";
        try {
            if (text != null) {
                text = text.trim();
                JSONObject root = text.startsWith("[") ? new JSONObject().put("items", new JSONArray(text)) : new JSONObject(text);
                head = root.optString("titulo", "Hoy");
                aText = root.optString("aviso", "");
                aTitle = root.optString("avisoTitulo", "Aviso");
                JSONArray arr = root.optJSONArray("items");
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String url = cleanUrl(o.optString("url", ""));
                    if (url.isEmpty()) url = cleanUrl(o.optString("embed", ""));
                    if (url.isEmpty()) continue;
                    Item it = new Item();
                    it.title = o.optString("titulo", url);
                    it.sub = o.optString("subtitulo", "");
                    it.image = o.optString("imagen", "");
                    it.url = url;
                    list.add(it);
                }
            }
        } catch (Exception ignored) {
        }
        heading = head;
        items = list;
        avisoTitle = aTitle;
        avisoText = aText;
    }

    /** Acepta un enlace normal o un código iframe completo y devuelve siempre un https:// válido (o vacío). */
    static String cleanUrl(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (s.contains("<")) {   // código embed
            Matcher m = SRC.matcher(s);
            if (!m.find()) return "";
            s = m.group(1).trim();
        }
        s = s.replace("&amp;", "&");
        if (s.startsWith("//")) s = "https:" + s;
        if (!s.startsWith("http://") && !s.startsWith("https://")) return "";
        return s;
    }

    static String download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Cache-Control", "no-cache");
        try (InputStream in = c.getInputStream()) {
            return new String(readAll(in), "UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ---------- pantalla ----------

    void build() {
        col.removeAllViews();
        int side = u(64, 18);
        col.setPadding(side, u(40, 28), side, u(40, 28));

        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextColor(TEXT);
        title.setTextSize(sp(40, 30));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, u(24, 18));
        col.addView(title);

        View first = null;
        for (Item it : items) {
            View card = card(it);
            if (first == null) first = card;
            col.addView(card);
        }

        if (items.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(status.isEmpty() ? "Hoy no hay nada preparado." : status);
            t.setTextColor(MUTED);
            t.setTextSize(sp(28, 20));
            t.setPadding(0, u(16, 16), 0, u(24, 16));
            col.addView(t);
            TextView retry = bigText("Actualizar", "");
            makeFocusable(retry);
            retry.setOnClickListener(v -> refresh());
            col.addView(retry, cardParams());
            first = retry;
        }

        if (App.tv && first != null && !firstFocusDone) {
            firstFocusDone = true;
            final View f = first;
            f.post(f::requestFocus);
        }
    }

    LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = u(18, 12);
        return lp;
    }

    TextView bigText(String main, String sub) {
        TextView t = new TextView(this);
        t.setText(sub.isEmpty() ? main : main + "\n" + sub);
        t.setTextColor(TEXT);
        t.setTextSize(sp(32, 22));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(u(110, 84));
        t.setPadding(u(32, 20), u(16, 12), u(32, 20), u(16, 12));
        return t;
    }

    View card(Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(u(110, 84));
        row.setPadding(u(24, 16), u(16, 12), u(24, 16), u(16, 12));

        if (!it.image.isEmpty()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(u(200, 110), u(112, 62));
            ip.rightMargin = u(24, 14);
            row.addView(img, ip);
            loadImage(it.image, img);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(it.title);
        t.setTextColor(TEXT);
        t.setTextSize(sp(32, 22));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        if (!it.sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(it.sub);
            s.setTextColor(MUTED);
            s.setTextSize(sp(24, 16));
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView play = new TextView(this);
        play.setText("\u25B6");
        play.setTextColor(ACCENT);
        play.setTextSize(sp(40, 28));
        row.addView(play);

        makeFocusable(row);
        row.setLayoutParams(cardParams());
        final String url = it.url, name = it.title;
        row.setOnClickListener(v -> startActivity(WebActivity.open(this, name, url, url)));
        return row;
    }

    void makeFocusable(View v) {
        int radius = dp(18);
        v.setFocusable(true);
        v.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(radius);
        v.setBackground(bg);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        ring.setStroke(dp(4), ACCENT);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, ring);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        v.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), states, mask));
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(120).start();
            if (has) view.post(() -> {
                int top = view.getTop() - dp(80);
                page.smoothScrollTo(0, Math.max(0, top));
            });
        });
    }

    void loadImage(final String url, final ImageView target) {
        Bitmap cached = Home.images.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        App.io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                byte[] data;
                try (InputStream in = c.getInputStream()) {
                    data = readAll(in);
                } finally {
                    c.disconnect();
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                final Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (b == null) return;
                Home.images.put(url, b);
                App.main.post(() -> { if (!isDestroyed()) target.setImageBitmap(b); });
            } catch (Exception ignored) {
            }
        });
    }
}
