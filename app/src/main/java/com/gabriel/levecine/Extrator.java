package com.gabriel.levecine;

import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Encontra arquivos de vídeo/áudio que estão diretamente no código de uma página. */
public final class Extrator {
    private Extrator() {}

    static final String EXT = "mp4|m4v|webm|mkv|mov|ogv|m3u8|mpd|mp3|m4a|aac|ogg|oga|opus|flac|wav";

    // URL absoluta (também dentro de JSON com \/) terminando numa extensão de mídia
    private static final Pattern ABS = Pattern.compile(
            "https?:(?:\\\\?/){2}[^\\s\"'<>()]*?\\.(?:" + EXT + ")(?:\\?[^\\s\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);

    // src="...", href="...", data-src="...", content="..." (relativos)
    private static final Pattern ATR = Pattern.compile(
            "(?:src|href|data-src|data-video|content|file)\\s*[=:]\\s*[\"']([^\"'<>]+?\\.(?:" + EXT + ")(?:\\?[^\"'<>]*)?)[\"']",
            Pattern.CASE_INSENSITIVE);

    // <video src> / <source src> / <audio src> sem extensão
    private static final Pattern TAG = Pattern.compile(
            "<(?:video|audio|source)\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static boolean ehMidia(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.matches(".*\\.(" + EXT + ")$");
    }

    public static boolean ehAudio(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.matches(".*\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav)$");
    }

    public static List<Item> extrair(String html, String base) {
        Set<String> achados = new LinkedHashSet<>();
        Matcher m = TAG.matcher(html);
        while (m.find()) adicionar(achados, m.group(1), base);
        m = ATR.matcher(html);
        while (m.find()) adicionar(achados, m.group(1), base);
        m = ABS.matcher(html);
        while (m.find()) adicionar(achados, m.group(), base);

        List<Item> lista = new ArrayList<>();
        for (String u : achados) {
            Item it = new Item();
            it.tipo = Item.FILME;
            it.url = u;
            it.titulo = nome(u);
            it.referer = base;
            lista.add(it);
        }
        return lista;
    }

    private static void adicionar(Set<String> s, String bruto, String base) {
        try {
            String u = bruto.trim().replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&");
            if (u.startsWith("data:") || u.startsWith("blob:") || u.startsWith("javascript:")) return;
            if (u.startsWith("//")) u = "https:" + u;
            u = new URL(new URL(base), u).toString();
            if (u.startsWith("http")) s.add(u);
        } catch (Exception ignorado) { }
    }

    public static String nome(String url) {
        try {
            String p = new URL(url).getPath();
            String n = p.substring(p.lastIndexOf('/') + 1);
            n = URLDecoder.decode(n, "UTF-8");
            return n.isEmpty() ? url : n;
        } catch (Exception e) {
            return url;
        }
    }
}
