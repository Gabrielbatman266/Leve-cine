package com.gabriel.levecine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Filmes e séries de domínio público do Internet Archive. */
public final class Archive {
    private Archive() {}

    public static final int POR_PAGINA = 60;

    public static List<Item> buscar(String consulta, String termo, int pagina, int tipo) throws Exception {
        String q = consulta;
        if (termo != null && !termo.trim().isEmpty()) {
            q = "(" + consulta + ") AND title:(" + termo.trim().replace("\"", "") + ")";
        }
        String url = "https://archive.org/advancedsearch.php?q=" + enc(q)
                + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year"
                + "&sort%5B%5D=downloads+desc&rows=" + POR_PAGINA + "&page=" + pagina + "&output=json";
        JSONObject json = new JSONObject(Net.texto(url));
        JSONArray docs = json.getJSONObject("response").getJSONArray("docs");
        List<Item> lista = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            Item it = new Item();
            it.tipo = tipo;
            it.archiveId = d.optString("identifier");
            it.titulo = texto(d.opt("title"), it.archiveId);
            String ano = texto(d.opt("year"), "");
            if (!ano.isEmpty()) it.titulo += " (" + ano + ")";
            it.imagem = "https://archive.org/services/img/" + it.archiveId;
            lista.add(it);
        }
        return lista;
    }

    /** Episódio/arquivo de vídeo de um item. */
    public static class Video {
        public String titulo, url;
    }

    /** Lista os vídeos de um item, preferindo a versão leve (512kb) para TV box fraca. */
    public static List<Video> videos(String id) throws Exception {
        JSONObject json = new JSONObject(Net.texto("https://archive.org/metadata/" + enc(id)));
        JSONArray files = json.optJSONArray("files");
        List<String> leves = new ArrayList<>(), mp4 = new ArrayList<>(), iaMp4 = new ArrayList<>(), outros = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                String nome = files.getJSONObject(i).optString("name");
                String n = nome.toLowerCase();
                if (n.endsWith("512kb.mp4")) leves.add(nome);
                else if (n.endsWith(".ia.mp4")) iaMp4.add(nome);
                else if (n.endsWith(".mp4") || n.endsWith(".m4v")) mp4.add(nome);
                else if (n.endsWith(".ogv") || n.endsWith(".webm") || n.endsWith(".mkv")) outros.add(nome);
            }
        }
        List<String> escolha = !leves.isEmpty() ? leves : !mp4.isEmpty() ? mp4 : !iaMp4.isEmpty() ? iaMp4 : outros;
        Collections.sort(escolha, String.CASE_INSENSITIVE_ORDER);
        List<Video> out = new ArrayList<>();
        for (String nome : escolha) {
            Video v = new Video();
            v.url = "https://archive.org/download/" + enc(id) + "/" + caminho(nome);
            String t = nome.substring(nome.lastIndexOf('/') + 1);
            t = t.replaceAll("(?i)(_512kb)?\\.(ia\\.)?(mp4|m4v|ogv|webm|mkv)$", "").replace('_', ' ').trim();
            v.titulo = t.isEmpty() ? id : t;
            out.add(v);
        }
        return out;
    }

    private static String caminho(String nome) throws Exception {
        String[] partes = nome.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(enc(partes[i]));
        }
        return sb.toString();
    }

    private static String texto(Object o, String padrao) {
        if (o == null) return padrao;
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            return a.length() > 0 ? a.optString(0, padrao) : padrao;
        }
        String s = String.valueOf(o);
        return s.isEmpty() || "null".equals(s) ? padrao : s;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }
}
