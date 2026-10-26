package com.casa.inicio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * Janelinha com a entrada ao vivo (sem som) do cartão em foco. A TV só decodifica um vídeo por vez,
 * então ela é liberada antes de abrir qualquer coisa em tela cheia. Enquanto o vídeo não chega, ou
 * se o aparelho estiver desligado, mostra chuvisco de fita VHS.
 */
class Previa extends LinearLayout {
    private static final long ESPERA_MS = 400; // só sintoniza quando o seletor para no cartão

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GradientDrawable moldura = new GradientDrawable();
    private final TvView tv;
    private final Chuvisco chuvisco;
    private final TextView estado;
    private final TextView legenda;
    private final float dp;

    private String sintonizado;   // inputId em uso no TvView, ou null
    private Runnable pendente;

    Previa(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setAlpha(0f);
        setVisibility(INVISIBLE);

        FrameLayout quadro = new FrameLayout(c);
        moldura.setColor(Color.BLACK);
        moldura.setCornerRadius(10 * dp);
        quadro.setBackground(moldura);
        int borda = Math.round(3 * dp);
        quadro.setPadding(borda, borda, borda, borda);

        tv = new TvView(c);
        tv.setStreamVolume(0f);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                if (inputId.equals(sintonizado)) {
                    chuvisco.animate().alpha(0f).setDuration(250).start();
                    estado.setVisibility(GONE);
                }
            }

            @Override
            public void onVideoUnavailable(String inputId, int motivo) {
                if (!inputId.equals(sintonizado)) {
                    return;
                }
                boolean carregando = motivo == TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN
                        || motivo == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                        || motivo == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING;
                mostrarChuvisco(carregando ? "SINTONIZANDO…" : "SEM SINAL");
            }

            @Override
            public void onConnectionFailed(String inputId) {
                mostrarChuvisco("SEM SINAL");
            }
        });
        quadro.addView(tv, new FrameLayout.LayoutParams(-1, -1));

        chuvisco = new Chuvisco(c);
        quadro.addView(chuvisco, new FrameLayout.LayoutParams(-1, -1));

        estado = new TextView(c);
        estado.setTextColor(Color.WHITE);
        estado.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        estado.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        estado.setShadowLayer(4 * dp, 0, 0, Color.BLACK);
        quadro.addView(estado, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        addView(quadro, new LayoutParams(Math.round(288 * dp), Math.round(162 * dp)));

        legenda = new TextView(c);
        legenda.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        legenda.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        legenda.setLetterSpacing(.08f);
        legenda.setPadding(Math.round(4 * dp), Math.round(6 * dp), 0, 0);
        addView(legenda);
    }

    /** Mostra a moldura já com chuvisco e sintoniza a entrada depois de uma pausa curta no cartão. */
    void mostrar(final String inputId, final Uri canal, int cor, String nome) {
        cancelarPendente();
        moldura.setStroke(Math.round(2 * dp), cor);
        legenda.setTextColor(cor);
        legenda.setShadowLayer(6 * dp, 0, 0, cor);
        legenda.setText("● AO VIVO  ·  " + nome.toUpperCase());
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(200).start();
        }
        if (inputId.equals(sintonizado)) {
            return;
        }
        liberarTv();
        mostrarChuvisco("SINTONIZANDO…");
        pendente = new Runnable() {
            @Override
            public void run() {
                pendente = null;
                sintonizado = inputId;
                tv.tune(inputId, canal);
            }
        };
        handler.postDelayed(pendente, ESPERA_MS);
    }

    /** Some com a janelinha e devolve a entrada para o resto da TV. */
    void esconder() {
        cancelarPendente();
        liberarTv();
        if (getVisibility() == VISIBLE) {
            animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(INVISIBLE);
                }
            }).start();
        }
        chuvisco.setAtivo(false);
    }

    private void liberarTv() {
        if (sintonizado != null) {
            tv.reset();
            sintonizado = null;
        }
    }

    private void cancelarPendente() {
        if (pendente != null) {
            handler.removeCallbacks(pendente);
            pendente = null;
        }
    }

    private void mostrarChuvisco(String texto) {
        chuvisco.animate().cancel();
        chuvisco.setAlpha(1f);
        chuvisco.setAtivo(true);
        estado.setText(texto);
        estado.setVisibility(VISIBLE);
    }

    /** Chuvisco de fita: ruído em baixa resolução, ampliado sem suavizar, com uma faixa de rolagem. */
    private static class Chuvisco extends View {
        private static final int L = 72, A = 40;
        private final Bitmap ruido = Bitmap.createBitmap(L, A, Bitmap.Config.ARGB_8888);
        private final int[] px = new int[L * A];
        private final Random r = new Random();
        private final Paint p = new Paint();
        private final Paint faixa = new Paint();
        private final Rect destino = new Rect();
        private boolean ativo;

        Chuvisco(Context c) {
            super(c);
            faixa.setColor(Color.argb(40, 255, 255, 255));
        }

        void setAtivo(boolean sim) {
            ativo = sim;
            if (sim) {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            for (int i = 0; i < px.length; i++) {
                int v = 30 + r.nextInt(150);
                px[i] = Color.rgb(v, v, v + 10);
            }
            ruido.setPixels(px, 0, L, 0, 0, L, A);
            destino.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(ruido, null, destino, p);
            float y = (System.currentTimeMillis() % 2200) / 2200f * getHeight();
            c.drawRect(0, y, getWidth(), y + getHeight() * .12f, faixa);
            if (ativo && getAlpha() > 0f) {
                postInvalidateDelayed(66);
            }
        }
    }
}
