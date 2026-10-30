package com.gabriel.levecine;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** HTTP mínimo, sem bibliotecas. */
public final class Net {
    public static final String UA = "Mozilla/5.0 (Linux; Android 10) LeveCine/1.0";

    private Net() {}

    public static byte[] bytes(String endereco) throws Exception {
        String atual = endereco;
        for (int saltos = 0; saltos < 6; saltos++) {
            HttpURLConnection c = (HttpURLConnection) new URL(atual).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("Redirecionamento sem destino");
                atual = new URL(new URL(atual), loc).toString();
                continue;
            }
            if (code >= 400) {
                c.disconnect();
                throw new Exception("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("Muitos redirecionamentos");
    }

    public static String texto(String endereco) throws Exception {
        return new String(bytes(endereco), "UTF-8");
    }
}
