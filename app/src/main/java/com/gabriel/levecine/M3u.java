package com.gabriel.levecine;

import java.util.ArrayList;
import java.util.List;

/** Lê listas .m3u (formato do iptv-org). */
public final class M3u {
    private M3u() {}

    public static List<Item> ler(String texto) {
        List<Item> lista = new ArrayList<>();
        Item atual = null;
        for (String linha : texto.split("\n")) {
            linha = linha.trim();
            if (linha.isEmpty()) continue;
            if (linha.startsWith("#EXTINF")) {
                atual = new Item();
                atual.tipo = Item.CANAL;
                atual.imagem = attr(linha, "tvg-logo");
                String g = attr(linha, "group-title");
                atual.grupo = g == null ? "" : g;
                int virgula = fimDosAtributos(linha);
                atual.titulo = virgula >= 0 ? linha.substring(virgula + 1).trim() : "Canal";
                String ua = attr(linha, "http-user-agent");
                if (ua != null) atual.userAgent = ua;
                String ref = attr(linha, "http-referrer");
                if (ref != null) atual.referer = ref;
            } else if (linha.startsWith("#EXTVLCOPT:")) {
                if (atual == null) continue;
                String op = linha.substring(11);
                if (op.startsWith("http-user-agent=")) atual.userAgent = op.substring(16);
                else if (op.startsWith("http-referrer=")) atual.referer = op.substring(14);
            } else if (!linha.startsWith("#")) {
                if (atual != null) {
                    atual.url = linha;
                    lista.add(atual);
                }
                atual = null;
            }
        }
        return lista;
    }

    /** Vírgula que separa atributos do nome (ignora vírgulas dentro de aspas). */
    private static int fimDosAtributos(String l) {
        boolean aspas = false;
        for (int i = 0; i < l.length(); i++) {
            char ch = l.charAt(i);
            if (ch == '"') aspas = !aspas;
            else if (ch == ',' && !aspas) return i;
        }
        return -1;
    }

    private static String attr(String linha, String nome) {
        String chave = nome + "=\"";
        int i = linha.indexOf(chave);
        if (i < 0) return null;
        int ini = i + chave.length();
        int fim = linha.indexOf('"', ini);
        if (fim < 0) return null;
        String v = linha.substring(ini, fim).trim();
        return v.isEmpty() ? null : v;
    }
}
