package com.gabriel.levecine;

/** Um canal, filme ou série. */
public class Item {
    public static final int CANAL = 0, FILME = 1, SERIE = 2;

    public int tipo;
    public String titulo = "";
    public String url;        // link direto (canal ou extra)
    public String archiveId;  // filme/série do archive.org (link resolvido ao abrir)
    public String imagem;
    public String grupo = "";
    public String userAgent;
    public String referer;

    /** Linha única para salvar nos favoritos. */
    public String salvar() {
        return tipo + "\t" + n(titulo) + "\t" + n(url) + "\t" + n(archiveId) + "\t" + n(imagem)
                + "\t" + n(userAgent) + "\t" + n(referer);
    }

    public static Item ler(String s) {
        String[] p = s.split("\t", -1);
        if (p.length < 7) return null;
        Item i = new Item();
        try { i.tipo = Integer.parseInt(p[0]); } catch (Exception e) { return null; }
        i.titulo = p[1];
        i.url = v(p[2]);
        i.archiveId = v(p[3]);
        i.imagem = v(p[4]);
        i.userAgent = v(p[5]);
        i.referer = v(p[6]);
        return i;
    }

    public String chave() { return archiveId != null ? "ia:" + archiveId : "u:" + url; }

    private static String n(String s) { return s == null ? "" : s.replace('\t', ' ').replace('\n', ' '); }
    private static String v(String s) { return s == null || s.isEmpty() ? null : s; }
}
