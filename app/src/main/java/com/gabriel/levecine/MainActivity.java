package com.gabriel.levecine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Troque aqui ou pelo botão ⚙ dentro do app
    static final String CATALOGO_PADRAO =
            "https://raw.githubusercontent.com/Gabrielbatman266/Leve-cine/main/catalogo.json";

    static final int ABA_FAV = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService fundo = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private JSONObject catalogo;
    private int aba = Item.CANAL;
    private final int[] listaEscolhida = new int[3];

    private final List<Item> todos = new ArrayList<>();
    private final List<Item> visiveis = new ArrayList<>();
    private final Map<String, List<Item>> cacheCanais = new HashMap<>();
    private int geracao = 0;

    // paginação do archive.org
    private int pagina = 1;
    private boolean carregando = false, acabou = false;
    private String termo = "";

    private TextView status;
    private EditText busca;
    private GridView grade;
    private Adaptador adaptador;
    private Button[] abas;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("leve", MODE_PRIVATE);

        status = findViewById(R.id.status);
        busca = findViewById(R.id.busca);
        grade = findViewById(R.id.grade);
        adaptador = new Adaptador();
        grade.setAdapter(adaptador);

        abas = new Button[]{findViewById(R.id.tabCanais), findViewById(R.id.tabFilmes),
                findViewById(R.id.tabSeries), findViewById(R.id.tabFav)};
        for (int i = 0; i < abas.length; i++) {
            final int a = i;
            abas[i].setOnClickListener(v -> trocarAba(a));
        }
        findViewById(R.id.btnLista).setOnClickListener(v -> escolherLista());
        findViewById(R.id.btnConfig).setOnClickListener(v -> configurar());

        busca.setOnEditorActionListener((v, acao, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (acao == EditorInfo.IME_ACTION_SEARCH || acao == EditorInfo.IME_ACTION_DONE || enter) {
                buscar(busca.getText().toString());
                esconderTeclado();
                grade.requestFocus();
                return true;
            }
            return false;
        });

        grade.setOnItemClickListener((p, v, pos, id) -> abrir(visiveis.get(pos)));
        grade.setOnItemLongClickListener((p, v, pos, id) -> { alternarFavorito(visiveis.get(pos)); return true; });
        grade.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) {}
            @Override public void onScroll(AbsListView v, int primeiro, int vis, int total) {
                if (total > 0 && primeiro + vis >= total - 8) carregarMais();
            }
        });

        aba = prefs.getInt("aba", Item.CANAL);
        if (aba == ABA_FAV) aba = Item.CANAL;
        for (int i = 0; i < 3; i++) listaEscolhida[i] = prefs.getInt("lista" + i, 0);

        status.setText("Carregando catálogo do GitHub…");
        fundo.execute(() -> {
            JSONObject c = carregarCatalogo();
            ui.post(() -> { catalogo = c; trocarAba(aba); abas[aba].requestFocus(); });
        });
    }

    // ---------- Catálogo (GitHub → cópia salva → embutido) ----------

    private JSONObject carregarCatalogo() {
        File salvo = new File(getFilesDir(), "catalogo.json");
        String url = prefs.getString("catalogo", CATALOGO_PADRAO);
        try {
            String t = Net.texto(url);
            JSONObject j = new JSONObject(t);
            try (FileOutputStream o = new FileOutputStream(salvo)) { o.write(t.getBytes("UTF-8")); }
            return j;
        } catch (Exception ignorado) { }
        try { return new JSONObject(ler(new FileInputStream(salvo))); } catch (Exception ignorado) { }
        try { return new JSONObject(ler(getAssets().open("catalogo.json"))); } catch (Exception e) { return new JSONObject(); }
    }

    private static String ler(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        }
    }

    private JSONArray listasDa(int a) {
        String chave = a == Item.CANAL ? "canais" : a == Item.FILME ? "filmes" : "series";
        JSONArray arr = catalogo == null ? null : catalogo.optJSONArray(chave);
        return arr == null ? new JSONArray() : arr;
    }

    // ---------- Abas ----------

    private void trocarAba(int a) {
        aba = a;
        prefs.edit().putInt("aba", a).apply();
        for (int i = 0; i < abas.length; i++) abas[i].setSelected(i == a);
        termo = "";
        busca.setText("");
        geracao++;
        todos.clear();
        mostrar();
        if (a == ABA_FAV) { carregarFavoritos(); return; }
        if (a == Item.CANAL) carregarCanais();
        else recomecarArchive();
    }

    private void escolherLista() {
        if (aba == ABA_FAV || catalogo == null) return;
        final JSONArray arr = listasDa(aba);
        if (arr.length() == 0) return;
        String[] nomes = new String[arr.length()];
        for (int i = 0; i < nomes.length; i++) nomes[i] = arr.optJSONObject(i).optString("nome", "Lista " + (i + 1));
        new AlertDialog.Builder(this)
                .setTitle("Escolha a lista")
                .setSingleChoiceItems(nomes, listaEscolhida[aba], (d, i) -> {
                    listaEscolhida[aba] = i;
                    prefs.edit().putInt("lista" + aba, i).apply();
                    d.dismiss();
                    trocarAba(aba);
                }).show();
    }

    private JSONObject listaAtual() {
        JSONArray arr = listasDa(aba);
        if (arr.length() == 0) return null;
        int i = Math.min(listaEscolhida[aba], arr.length() - 1);
        return arr.optJSONObject(i);
    }

    // ---------- Canais (iptv-org no GitHub) ----------

    private void carregarCanais() {
        final JSONObject lista = listaAtual();
        if (lista == null) { status.setText("Nenhuma lista de canais no catálogo."); return; }
        final String nome = lista.optString("nome");
        final String url = lista.optString("url");
        final int g = geracao;
        List<Item> mem = cacheCanais.get(url);
        if (mem != null) { preencher(mem, nome); return; }
        status.setText("Carregando canais: " + nome + "…");
        fundo.execute(() -> {
            File f = new File(getCacheDir(), "m3u_" + Integer.toHexString(url.hashCode()));
            String txt = null;
            try {
                txt = Net.texto(url);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(txt.getBytes("UTF-8")); }
            } catch (Exception e) {
                try { txt = ler(new FileInputStream(f)); } catch (Exception ignorado) { }
            }
            final List<Item> canais = txt == null ? new ArrayList<>() : M3u.ler(txt);
            ui.post(() -> {
                if (g != geracao) return;
                if (canais.isEmpty()) { status.setText("Sem conexão com a lista " + nome + "."); return; }
                cacheCanais.put(url, canais);
                preencher(canais, nome);
            });
        });
    }

    private void preencher(List<Item> canais, String nome) {
        todos.clear();
        todos.addAll(extras(Item.CANAL));
        todos.addAll(canais);
        aplicarFiltro();
        status.setText(nome + " · " + visiveis.size() + " canais · segure para favoritar");
    }

    // ---------- Filmes / Séries (Internet Archive, domínio público) ----------

    private void recomecarArchive() {
        pagina = 1;
        acabou = false;
        carregando = false;
        todos.clear();
        if (termo.isEmpty()) todos.addAll(extras(aba));
        mostrar();
        carregarMais();
    }

    private void carregarMais() {
        if (aba != Item.FILME && aba != Item.SERIE) return;
        if (carregando || acabou) return;
        final JSONObject lista = listaAtual();
        if (lista == null) { status.setText("Nenhuma lista no catálogo."); return; }
        carregando = true;
        final int g = geracao, tipo = aba, pag = pagina;
        final String consulta = lista.optString("busca"), nome = lista.optString("nome"), t = termo;
        status.setText("Carregando " + nome + "…");
        fundo.execute(() -> {
            List<Item> novos;
            String erro = null;
            try { novos = Archive.buscar(consulta, t, pag, tipo); }
            catch (Exception e) { novos = new ArrayList<>(); erro = e.getMessage(); }
            final List<Item> r = novos;
            final String err = erro;
            ui.post(() -> {
                if (g != geracao) return;
                carregando = false;
                if (err != null) { status.setText("Erro ao carregar (" + err + ")."); return; }
                if (r.size() < Archive.POR_PAGINA) acabou = true;
                pagina++;
                todos.addAll(r);
                visiveis.clear();
                visiveis.addAll(todos);
                adaptador.notifyDataSetChanged();
                status.setText(nome + (t.isEmpty() ? "" : " · \"" + t + "\"") + " · " + todos.size() + " títulos");
            });
        });
    }

    // ---------- Busca ----------

    private void buscar(String q) {
        termo = q.trim();
        if (aba == Item.CANAL || aba == ABA_FAV) {
            aplicarFiltro();
            status.setText(visiveis.size() + " resultado(s)");
        } else {
            geracao++;
            recomecarArchive();
        }
    }

    private void aplicarFiltro() {
        visiveis.clear();
        String t = normal(termo);
        for (Item i : todos) if (t.isEmpty() || normal(i.titulo).contains(t)) visiveis.add(i);
        adaptador.notifyDataSetChanged();
        grade.setSelection(0);
    }

    private void mostrar() {
        visiveis.clear();
        visiveis.addAll(todos);
        adaptador.notifyDataSetChanged();
    }

    private static String normal(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    private List<Item> extras(int tipo) {
        List<Item> out = new ArrayList<>();
        JSONArray arr = catalogo == null ? null : catalogo.optJSONArray("extras");
        if (arr == null) return out;
        String nome = tipo == Item.CANAL ? "canal" : tipo == Item.FILME ? "filme" : "serie";
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !nome.equals(o.optString("tipo"))) continue;
            Item it = new Item();
            it.tipo = tipo;
            it.titulo = o.optString("titulo", "Extra");
            it.url = o.optString("url", null);
            it.archiveId = o.optString("archive", null);
            if (it.archiveId != null && it.archiveId.isEmpty()) it.archiveId = null;
            it.imagem = o.optString("imagem", "");
            if (it.imagem.isEmpty() && it.archiveId != null) it.imagem = "https://archive.org/services/img/" + it.archiveId;
            if (it.url != null || it.archiveId != null) out.add(it);
        }
        return out;
    }

    // ---------- Abrir / tocar ----------

    private void abrir(final Item it) {
        if (it.archiveId == null) {
            tocar(new String[]{it.url}, new String[]{it.titulo}, 0, it);
            return;
        }
        status.setText("Abrindo " + it.titulo + "…");
        fundo.execute(() -> {
            List<Archive.Video> vids;
            try { vids = Archive.videos(it.archiveId); } catch (Exception e) { vids = new ArrayList<>(); }
            final List<Archive.Video> v = vids;
            ui.post(() -> {
                status.setText("");
                if (v.isEmpty()) { Toast.makeText(this, "Nenhum vídeo encontrado nesse título.", Toast.LENGTH_SHORT).show(); return; }
                final String[] urls = new String[v.size()], nomes = new String[v.size()];
                for (int i = 0; i < urls.length; i++) { urls[i] = v.get(i).url; nomes[i] = v.get(i).titulo; }
                if (urls.length == 1) { tocar(urls, new String[]{it.titulo}, 0, it); return; }
                new AlertDialog.Builder(this)
                        .setTitle(it.titulo + " · " + urls.length + (it.tipo == Item.SERIE ? " episódios" : " partes"))
                        .setItems(nomes, (d, i) -> tocar(urls, nomes, i, it))
                        .show();
            });
        });
    }

    private void tocar(String[] urls, String[] nomes, int inicio, Item it) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("urls", urls);
        i.putExtra("nomes", nomes);
        i.putExtra("inicio", inicio);
        i.putExtra("aoVivo", it.tipo == Item.CANAL);
        i.putExtra("ua", it.userAgent);
        i.putExtra("ref", it.referer);
        startActivity(i);
    }

    // ---------- Favoritos ----------

    private Set<String> favoritos() {
        return new LinkedHashSet<>(prefs.getStringSet("fav", new LinkedHashSet<>()));
    }

    private void alternarFavorito(Item it) {
        Set<String> fav = favoritos();
        String achado = null;
        for (String s : fav) { Item f = Item.ler(s); if (f != null && f.chave().equals(it.chave())) achado = s; }
        if (achado != null) {
            fav.remove(achado);
            Toast.makeText(this, "Removido dos favoritos", Toast.LENGTH_SHORT).show();
        } else {
            fav.add(it.salvar());
            Toast.makeText(this, "★ Adicionado aos favoritos", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet("fav", fav).apply();
        if (aba == ABA_FAV) carregarFavoritos();
    }

    private void carregarFavoritos() {
        todos.clear();
        for (String s : favoritos()) { Item i = Item.ler(s); if (i != null) todos.add(i); }
        aplicarFiltro();
        status.setText(todos.isEmpty() ? "Sem favoritos. Segure OK (ou o dedo) num item para favoritar."
                : todos.size() + " favorito(s) · segure para remover");
    }

    // ---------- Configuração ----------

    private void configurar() {
        final EditText campo = new EditText(this);
        campo.setSingleLine(true);
        campo.setText(prefs.getString("catalogo", CATALOGO_PADRAO));
        new AlertDialog.Builder(this)
                .setTitle("Link do catálogo (GitHub)")
                .setView(campo)
                .setPositiveButton("Salvar e recarregar", (d, w) -> {
                    prefs.edit().putString("catalogo", campo.getText().toString().trim()).apply();
                    recarregarTudo();
                })
                .setNeutralButton("Padrão", (d, w) -> {
                    prefs.edit().remove("catalogo").apply();
                    recarregarTudo();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void recarregarTudo() {
        cacheCanais.clear();
        status.setText("Recarregando catálogo…");
        fundo.execute(() -> {
            JSONObject c = carregarCatalogo();
            ui.post(() -> { catalogo = c; trocarAba(aba == ABA_FAV ? Item.CANAL : aba); });
        });
    }

    private void esconderTeclado() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(busca.getWindowToken(), 0);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) Imagens.limpar();
    }

    // ---------- Grade ----------

    private class Adaptador extends BaseAdapter {
        @Override public int getCount() { return visiveis.size(); }
        @Override public Object getItem(int p) { return visiveis.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View v, ViewGroup pai) {
            if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item, pai, false);
            Item it = visiveis.get(pos);
            ((TextView) v.findViewById(R.id.nome)).setText(it.titulo);
            ImageView img = v.findViewById(R.id.img);
            img.setScaleType(it.tipo == Item.CANAL ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
            Imagens.carregar(it.imagem, img);
            return v;
        }
    }
}
