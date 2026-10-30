package com.gabriel.levecine;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView view;
    private TextView titulo;
    private String[] nomes;
    private boolean aoVivo;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable esconderTitulo = () -> titulo.setVisibility(View.GONE);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_player);
        view = findViewById(R.id.player);
        titulo = findViewById(R.id.titulo);

        String[] urls = getIntent().getStringArrayExtra("urls");
        nomes = getIntent().getStringArrayExtra("nomes");
        int inicio = getIntent().getIntExtra("inicio", 0);
        aoVivo = getIntent().getBooleanExtra("aoVivo", false);
        String ua = getIntent().getStringExtra("ua");
        String ref = getIntent().getStringExtra("ref");
        if (urls == null || urls.length == 0) { finish(); return; }

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua != null ? ua : Net.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        if (ref != null) {
            Map<String, String> h = new HashMap<>();
            h.put("Referer", ref);
            http.setDefaultRequestProperties(h);
        }

        // Buffer menor = menos RAM na TV box
        DefaultLoadControl buffer = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(8000, 30000, 1500, 3000)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setLoadControl(buffer)
                .build();
        view.setPlayer(player);
        view.setKeepScreenOn(true);
        if (aoVivo) {
            view.setShowFastForwardButton(false);
            view.setShowRewindButton(false);
        }

        List<MediaItem> lista = new ArrayList<>();
        for (int i = 0; i < urls.length; i++) {
            MediaItem.Builder mb = new MediaItem.Builder().setUri(urls[i])
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(nome(i)).build());
            String u = urls[i].toLowerCase();
            if (aoVivo && !u.contains(".mp4") && !u.contains(".mkv") && !u.contains(".ts")
                    && !u.contains(".mpd") && !u.contains(".mp3") && !u.contains(".aac")) {
                mb.setMimeType(MimeTypes.APPLICATION_M3U8); // maioria dos canais é HLS
            }
            lista.add(mb.build());
        }
        player.setMediaItems(lista, Math.min(inicio, lista.size() - 1), 0);

        player.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int motivo) {
                mostrarTitulo();
            }
            @Override public void onPlayerError(PlaybackException e) {
                String msg = aoVivo ? "Canal fora do ar ou bloqueado na sua região." : "Não foi possível tocar esse vídeo.";
                Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); }
                else finish();
            }
        });

        player.prepare();
        player.play();
        mostrarTitulo();
        telaCheia();
    }

    private String nome(int i) {
        return nomes != null && i < nomes.length ? nomes[i] : "";
    }

    private void mostrarTitulo() {
        if (player == null) return;
        String t = nome(player.getCurrentMediaItemIndex());
        if (player.getMediaItemCount() > 1) t += "  (" + (player.getCurrentMediaItemIndex() + 1) + "/" + player.getMediaItemCount() + ")";
        titulo.setText(t);
        titulo.setVisibility(t.trim().isEmpty() ? View.GONE : View.VISIBLE);
        ui.removeCallbacks(esconderTitulo);
        ui.postDelayed(esconderTitulo, 4000);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // OK/Enter no controle da TV mostra os controles
        if (e.getAction() == KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible()) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                view.showController();
                mostrarTitulo();
                return true;
            }
        }
        return view.dispatchKeyEvent(e) || super.dispatchKeyEvent(e);
    }

    private void telaCheia() {
        View d = getWindow().getDecorView();
        int f = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 19) f |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        d.setSystemUiVisibility(f);
    }

    @Override
    public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        if (foco) telaCheia();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null && aoVivo) { player.seekToDefaultPosition(); player.play(); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }
}
