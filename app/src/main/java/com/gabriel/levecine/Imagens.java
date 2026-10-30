package com.gabriel.levecine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carregador de imagens minúsculo: cache em memória pequeno e imagens reduzidas. */
public final class Imagens {
    private static final int LADO = 220; // px máximos — suficiente para os cartões
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 16, 12 * 1024 * 1024)) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
            };

    private Imagens() {}

    public static void carregar(final String url, final ImageView v) {
        v.setTag(url);
        v.setImageDrawable(null);
        if (url == null || url.isEmpty()) return;
        Bitmap b = cache.get(url);
        if (b != null) { v.setImageBitmap(b); return; }
        pool.execute(() -> {
            if (!url.equals(v.getTag())) return; // célula já foi reaproveitada
            Bitmap bmp = baixar(url);
            if (bmp == null) return;
            cache.put(url, bmp);
            ui.post(() -> { if (url.equals(v.getTag())) v.setImageBitmap(bmp); });
        });
    }

    private static Bitmap baixar(String url) {
        try {
            byte[] dados = Net.bytes(url);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= LADO || o.outHeight / (s * 2) >= LADO) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(dados, 0, dados.length, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void limpar() { cache.evictAll(); }
}
