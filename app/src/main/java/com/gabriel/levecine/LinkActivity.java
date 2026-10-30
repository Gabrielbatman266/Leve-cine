package com.gabriel.levecine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Leve Link: cole o link de uma página e veja as mídias que estão nela. */
public class LinkActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService fundo = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private EditText campo;
    private TextView status;
    private ListView lista;
    private final List<Item> midias = new ArrayList<>();
    private final List<String> nomes = new ArrayList<>();
    private ArrayAdapter<String> adaptador;
    private int geracao = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("leve", MODE_PRIVATE);
        setContentView(montarTela());

        campo.setText(prefs.getString("ultimoLink", ""));
        campo.setOnEditorActionListener((v, acao, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (acao == EditorInfo.IME_ACTION_GO || acao == EditorInfo.IME_ACTION_DONE || enter) {
                abrirLink();
                return true;
            }
            return false;
        });
        lista.setOnItemClickListener((p, v, pos, id) -> tocar(pos));

        receberCompartilhado(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        receberCompartilhado(i);
    }

    /** Aceita "Compartilhar → Leve Link" vindo do navegador. */
    private void receberCompartilhado(Intent i) {
        if (i == null) return;
        String txt = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) txt = i.getStringExtra(Intent.EXTRA_TEXT);
        else if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) txt = i.getData().toString();
        if (txt == null) return;
        Matcher m = Pattern.compile("https?://\\S+").matcher(txt);
        if (m.find()) {
            campo.setText(m.group());
            abrirLink();
        }
    }

    private void abrirLink() {
        String url = campo.getText().toString().trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http")) url = "https://" + url;
        campo.setText(url);
        prefs.edit().putString("ultimoLink", url).apply();
        esconderTeclado();

        midias.clear();
        nomes.clear();
        adaptador.notifyDataSetChanged();

        // O próprio link já é um vídeo/áudio: toca direto
        if (Extrator.ehMidia(url) && !url.toLowerCase().contains(".m3u")) {
            Item it = new Item();
            it.url = url;
            it.titulo = Extrator.nome(url);
            midias.add(it);
            nomes.add("▶  " + it.titulo);
            adaptador.notifyDataSetChanged();
            status.setText("Link direto de mídia.");
            tocar(0);
            return;
        }

        final String alvo = url;
        final int g = ++geracao;
        status.setText("Procurando mídias na página…");
        fundo.execute(() -> {
            List<Item> achados;
            String erro = null;
            try {
                String html = Net.pagina(alvo);
                if (html.trim().startsWith("#EXTM3U") && html.contains("#EXTINF")) {
                    achados = M3u.ler(html); // lista de canais .m3u
                } else {
                    achados = Extrator.extrair(html, alvo);
                }
            } catch (Exception e) {
                achados = new ArrayList<>();
                erro = e.getMessage();
            }
            final List<Item> r = achados;
            final String err = erro;
            ui.post(() -> {
                if (g != geracao) return;
                if (err != null) { status.setText("Não consegui abrir a página (" + err + ")."); return; }
                midias.addAll(r);
                for (Item it : r) nomes.add((Extrator.ehAudio(it.url) ? "♪  " : "▶  ") + it.titulo);
                adaptador.notifyDataSetChanged();
                status.setText(r.isEmpty()
                        ? "Nenhuma mídia direta nessa página. Sites que escondem o vídeo em players de terceiros ou com proteção não funcionam."
                        : r.size() + " mídia(s) encontrada(s). Toque para assistir; as seguintes tocam em sequência.");
                if (!r.isEmpty()) lista.requestFocus();
            });
        });
    }

    private void tocar(int pos) {
        if (pos < 0 || pos >= midias.size()) return;
        String[] urls = new String[midias.size()], titulos = new String[midias.size()];
        for (int i = 0; i < urls.length; i++) { urls[i] = midias.get(i).url; titulos[i] = midias.get(i).titulo; }
        Item it = midias.get(pos);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("urls", urls);
        i.putExtra("nomes", titulos);
        i.putExtra("inicio", pos);
        i.putExtra("aoVivo", it.tipo == Item.CANAL);
        i.putExtra("ua", it.userAgent);
        i.putExtra("ref", it.referer);
        startActivity(i);
    }

    // ---------- Tela (feita em código para ficar leve) ----------

    private View montarTela() {
        int pad = dp(12);
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(pad, pad, pad, pad);

        TextView titulo = new TextView(this);
        titulo.setText("Leve Link");
        titulo.setTextColor(Color.parseColor("#E8B04B"));
        titulo.setTextSize(20);
        titulo.setPadding(0, 0, 0, dp(8));
        raiz.addView(titulo);

        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);

        campo = new EditText(this);
        campo.setHint("Cole o link da página…");
        campo.setSingleLine(true);
        campo.setEllipsize(TextUtils.TruncateAt.END);
        campo.setImeOptions(EditorInfo.IME_ACTION_GO);
        campo.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        campo.setTextColor(Color.parseColor("#F2F2F2"));
        campo.setHintTextColor(Color.parseColor("#9AA0AA"));
        campo.setTextSize(14);
        campo.setBackgroundResource(R.drawable.botao);
        campo.setPadding(dp(14), 0, dp(14), 0);
        linha.addView(campo, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button ir = botao("Abrir");
        ir.setOnClickListener(v -> abrirLink());
        linha.addView(ir);

        Button colar = botao("Colar");
        colar.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null) { campo.setText(t.toString().trim()); abrirLink(); }
            }
        });
        linha.addView(colar);
        raiz.addView(linha);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#9AA0AA"));
        status.setTextSize(13);
        status.setPadding(0, dp(8), 0, dp(8));
        status.setText("Funciona com páginas que têm o vídeo/áudio direto no código, links de .mp4/.m3u8/.mp3 e listas .m3u. Dica: no navegador, use Compartilhar → Leve Link.");
        raiz.addView(status);

        lista = new ListView(this);
        lista.setDividerHeight(dp(6));
        lista.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lista.setSelector(android.R.color.transparent);
        adaptador = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, nomes) {
            @Override
            public View getView(int pos, View v, ViewGroup pai) {
                TextView t = (TextView) super.getView(pos, v, pai);
                t.setBackgroundResource(R.drawable.cartao);
                t.setTextColor(Color.parseColor("#F2F2F2"));
                t.setTextSize(14);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                t.setPadding(dp(14), dp(12), dp(14), dp(12));
                return t;
            }
        };
        lista.setAdapter(adaptador);
        raiz.addView(lista, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return raiz;
    }

    private Button botao(String texto) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#F2F2F2"));
        b.setBackgroundResource(R.drawable.botao);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.setMarginStart(dp(6));
        b.setLayoutParams(lp);
        b.setPadding(dp(16), 0, dp(16), 0);
        return b;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void esconderTeclado() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(campo.getWindowToken(), 0);
    }
}
