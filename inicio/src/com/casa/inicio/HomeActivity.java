package com.casa.inicio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends Activity {
    // Paleta neon de pôr do sol
    private static final int ROSA = Color.rgb(255, 46, 136);
    private static final int LARANJA = Color.rgb(255, 138, 61);
    private static final int CIANO = Color.rgb(34, 211, 238);
    private static final int VIOLETA = Color.rgb(168, 85, 247);
    private static final int AMARELO = Color.rgb(255, 194, 75);

    private static final int VIDRO = Color.argb(170, 16, 6, 28);
    private static final int TEXTO_SUAVE = Color.argb(170, 255, 225, 240);

    private View primeiro;
    private Cenario cenario;
    private RelogioVhs relogio;
    private Previa previa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout tela = new FrameLayout(this);
        tela.setBackgroundColor(Color.rgb(8, 2, 16));
        tela.setClipChildren(false);
        cenario = new Cenario(this);
        tela.addView(cenario, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout topo = new LinearLayout(this);
        topo.setOrientation(LinearLayout.VERTICAL);
        topo.setClipChildren(false);
        topo.setClipToPadding(false);
        relogio = new RelogioVhs(this);
        topo.addView(relogio);
        TextView lema = texto("VICE CITY  ·  LEONIDA", 11, ROSA, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        lema.setLetterSpacing(.4f);
        lema.setShadowLayer(dp(8), 0, 0, ROSA);
        lema.setPadding(dp(8), dp(6), dp(12), dp(6));
        topo.addView(lema);
        FrameLayout.LayoutParams lpTopo = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lpTopo.leftMargin = dp(40);
        lpTopo.topMargin = dp(24);
        tela.addView(topo, lpTopo);

        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER);
        linha.setClipChildren(false);
        linha.setClipToPadding(false);
        FrameLayout.LayoutParams lpLinha = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lpLinha.bottomMargin = dp(44);
        tela.addView(linha, lpLinha);

        previa = new Previa(this);
        FrameLayout.LayoutParams lpPrevia = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lpPrevia.topMargin = dp(28);
        lpPrevia.rightMargin = dp(40);
        tela.addView(previa, lpPrevia);

        primeiro = cartao(linha, "TV aberta", Icone.TV, LARANJA, Botoes.tvAberta(),
                Botoes.TV_ABERTA, TvContract.buildChannelUri(Botoes.CANAL_PREVIA));
        cartao(linha, "PS4", Icone.CONTROLE, VIOLETA, Botoes.entrada(Botoes.HDMI1),
                Botoes.HDMI1, TvContract.buildChannelUriForPassthroughInput(Botoes.HDMI1));
        cartao(linha, "Notebook", Icone.NOTEBOOK, CIANO, Botoes.entrada(Botoes.HDMI2),
                Botoes.HDMI2, TvContract.buildChannelUriForPassthroughInput(Botoes.HDMI2));
        cartao(linha, "YouTube", Icone.PLAY, ROSA,
                getPackageManager().getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv"), null, null);
        cartao(linha, "Ajustes", Icone.ENGRENAGEM, AMARELO,
                new Intent(Settings.ACTION_SETTINGS).setPackage("com.android.tv.settings"), null, null);

        setContentView(tela);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ativador.garantir(this);
        primeiro.requestFocus();
        // requestFocus não chama o listener se o cartão já tinha o foco; a prévia precisa voltar mesmo assim
        primeiro.getOnFocusChangeListener().onFocusChange(primeiro, true);
        cenario.setAnimando(true);
        relogio.setAnimando(true);
    }

    @Override
    protected void onPause() {
        previa.esconder();
        cenario.setAnimando(false);
        relogio.setAnimando(false);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Na tela inicial o "voltar" não faz nada.
    }

    private TextView texto(String s, int tamanho, int cor, Typeface fonte) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(cor);
        t.setTypeface(fonte);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho);
        return t;
    }

    private TextClock relogio(String formato, int tamanho, int cor, Typeface fonte) {
        TextClock t = new TextClock(this);
        t.setFormat24Hour(formato);
        t.setFormat12Hour(formato);
        t.setTextColor(cor);
        t.setTypeface(fonte);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho);
        return t;
    }

    /** Cartão da fileira; com {@code inputPrevia}, o foco nele mostra a entrada ao vivo na janelinha. */
    private View cartao(LinearLayout linha, final String nome, final int tipoIcone, final int cor, final Intent intent,
                        final String inputPrevia, final Uri canalPrevia) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setFocusable(true);
        c.setClickable(true);

        final Icone icone = new Icone(tipoIcone, cor, dp(1));
        ImageView img = new ImageView(this);
        img.setImageDrawable(icone);
        c.addView(img, new LinearLayout.LayoutParams(dp(56), dp(56)));

        final TextView rotulo = texto(nome.toUpperCase(), 14, TEXTO_SUAVE, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        rotulo.setLetterSpacing(.12f);
        rotulo.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpRotulo = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRotulo.topMargin = dp(16);
        c.addView(rotulo, lpRotulo);

        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(VIDRO);
        normal.setCornerRadius(dp(18));
        normal.setStroke(dp(1), Color.argb(110, Color.red(cor), Color.green(cor), Color.blue(cor)));

        final GradientDrawable foco = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {cor, escurecer(cor)});
        foco.setCornerRadius(dp(18));
        foco.setStroke(dp(2), Color.WHITE);
        c.setBackground(normal);

        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean temFoco) {
                v.setBackground(temFoco ? foco : normal);
                icone.setCor(temFoco ? Color.WHITE : cor);
                rotulo.setTextColor(temFoco ? Color.WHITE : TEXTO_SUAVE);
                if (temFoco) {
                    rotulo.setShadowLayer(dp(6), 0, 0, Color.argb(160, 0, 0, 0));
                } else {
                    rotulo.setShadowLayer(0, 0, 0, 0);
                }
                float escala = temFoco ? 1.14f : 1f;
                v.animate().scaleX(escala).scaleY(escala).setDuration(120).start();
                if (temFoco) {
                    if (inputPrevia != null) {
                        previa.mostrar(inputPrevia, canalPrevia, cor, nome, tipoIcone);
                    } else {
                        previa.esconder();
                    }
                }
            }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // A TV só tem um caminho de vídeo: solta a prévia antes de abrir em tela cheia
                previa.esconder();
                abrir(intent);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(150));
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        linha.addView(c, lp);
        return c;
    }

    private static int escurecer(int cor) {
        return Color.rgb(Color.red(cor) * 45 / 100, Color.green(cor) * 30 / 100, Color.blue(cor) * 55 / 100);
    }

    private void abrir(Intent intent) {
        if (intent == null) {
            Toast.makeText(this, "App não instalado", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Não consegui abrir", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
