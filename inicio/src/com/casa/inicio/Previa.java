package com.casa.inicio;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Janelinha com a entrada ao vivo (sem som) do cartão em foco.
 *
 * O vídeo da TV é uma camada de hardware que não aceita cantos arredondados; por isso a moldura é
 * desenhada por cima dele, com o recorte interno arredondado, escondendo os cantos retos do vídeo.
 * A TV só decodifica um vídeo por vez, então a janelinha é liberada antes de abrir algo em tela cheia.
 */
class Previa extends LinearLayout {
    private static final long ESPERA_MS = 400; // só sintoniza quando o seletor para no cartão
    private static final int LARGURA = 240, ALTURA = 135; // dp, 16:9
    private static final int BRILHO = 10, BORDA = 5, RAIO = 15; // dp

    private enum Estado { SINTONIZANDO, AO_VIVO, SEM_SINAL }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private final TvView tv;
    private final Espera espera;
    private final Moldura moldura;
    private final TextView nomeView;
    private final TextView selo;
    private final GradientDrawable fundoSelo = new GradientDrawable();

    private int cor = Color.WHITE;
    private ValueAnimator trocaCor;
    private String nome = "";
    private String sintonizado;   // inputId em uso no TvView, ou null
    private Runnable pendente;

    Previa(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setAlpha(0f);
        setVisibility(INVISIBLE);

        int brilho = px(BRILHO), borda = px(BORDA);
        FrameLayout quadro = new FrameLayout(c);
        quadro.setClipChildren(false);

        tv = new TvView(c);
        tv.setStreamVolume(0f);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                if (inputId.equals(sintonizado)) {
                    mudarEstado(Estado.AO_VIVO);
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
                mudarEstado(carregando ? Estado.SINTONIZANDO : Estado.SEM_SINAL);
            }

            @Override
            public void onConnectionFailed(String inputId) {
                mudarEstado(Estado.SEM_SINAL);
            }
        });
        FrameLayout.LayoutParams lpVideo = new FrameLayout.LayoutParams(px(LARGURA), px(ALTURA));
        lpVideo.leftMargin = lpVideo.topMargin = brilho + borda;
        quadro.addView(tv, lpVideo);

        espera = new Espera(c);
        quadro.addView(espera, new FrameLayout.LayoutParams(lpVideo));

        moldura = new Moldura(c);
        quadro.addView(moldura, new FrameLayout.LayoutParams(
                px(LARGURA) + 2 * (brilho + borda), px(ALTURA) + 2 * (brilho + borda)));
        addView(quadro);

        // Legenda: nome à esquerda, selo de estado à direita, alinhados à moldura
        LinearLayout legenda = new LinearLayout(c);
        legenda.setOrientation(HORIZONTAL);
        legenda.setGravity(Gravity.CENTER_VERTICAL);
        nomeView = new TextView(c);
        nomeView.setTextColor(Color.WHITE);
        nomeView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        nomeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nomeView.setLetterSpacing(.18f);
        legenda.addView(nomeView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        selo = new TextView(c);
        selo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        selo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        selo.setLetterSpacing(.14f);
        selo.setPadding(px(9), px(3), px(9), px(3));
        fundoSelo.setCornerRadius(px(20));
        selo.setBackground(fundoSelo);
        legenda.addView(selo);
        LayoutParams lpLegenda = new LayoutParams(px(LARGURA), LayoutParams.WRAP_CONTENT);
        lpLegenda.leftMargin = brilho + borda;
        lpLegenda.topMargin = -px(2);
        addView(legenda, lpLegenda);
    }

    /** Mostra a moldura já em espera e sintoniza a entrada depois de uma pausa curta no cartão. */
    void mostrar(final String inputId, final Uri canal, int novaCor, String novoNome, int icone) {
        cancelarPendente();
        nome = novoNome;
        nomeView.setText(novoNome.toUpperCase());
        espera.setIcone(icone);
        animarCor(novaCor);
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setTranslationY(px(8));
            animate().alpha(1f).translationY(0).setDuration(220).start();
        }
        if (inputId.equals(sintonizado)) {
            return;
        }
        liberarTv();
        mudarEstado(Estado.SINTONIZANDO);
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
        espera.setAtiva(false);
        if (getVisibility() == VISIBLE) {
            animate().alpha(0f).translationY(px(8)).setDuration(160).withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(INVISIBLE);
                }
            }).start();
        }
    }

    private void mudarEstado(Estado e) {
        if (e == Estado.AO_VIVO) {
            espera.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    espera.setAtiva(false);
                }
            }).start();
        } else {
            espera.animate().cancel();
            espera.setAlpha(1f);
            espera.setSemSinal(e == Estado.SEM_SINAL, nome);
            espera.setAtiva(true);
        }
        switch (e) {
            case AO_VIVO:
                selo.setText("●  AO VIVO");
                selo.setTextColor(Color.WHITE);
                fundoSelo.setColor(Color.argb(200, 230, 40, 70));
                break;
            case SINTONIZANDO:
                selo.setText("SINTONIZANDO");
                selo.setTextColor(Color.argb(220, 255, 255, 255));
                fundoSelo.setColor(Color.argb(60, 255, 255, 255));
                break;
            default:
                selo.setText("SEM SINAL");
                selo.setTextColor(Color.argb(170, 255, 255, 255));
                fundoSelo.setColor(Color.argb(40, 255, 255, 255));
        }
    }

    private void animarCor(int alvo) {
        if (trocaCor != null) {
            trocaCor.cancel();
        }
        if (getVisibility() != VISIBLE) {
            aplicarCor(alvo);
            return;
        }
        trocaCor = ValueAnimator.ofObject(new ArgbEvaluator(), cor, alvo);
        trocaCor.setDuration(250);
        trocaCor.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                aplicarCor((Integer) a.getAnimatedValue());
            }
        });
        trocaCor.start();
    }

    private void aplicarCor(int c) {
        cor = c;
        moldura.invalidate();
        espera.invalidate();
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

    private int px(int v) {
        return Math.round(v * dp);
    }

    private static int comAlfa(int cor, int alfa) {
        return Color.argb(alfa, Color.red(cor), Color.green(cor), Color.blue(cor));
    }

    /**
     * Moldura por cima do vídeo: vidro escuro com recorte arredondado (cobre os cantos retos do vídeo),
     * fio neon na borda interna e um brilho suave em volta.
     */
    private class Moldura extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path vidro = new Path();
        private final RectF fora = new RectF(), dentro = new RectF();

        Moldura(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float b = px(BRILHO), e = px(BORDA);
            fora.set(b, b, w - b, h - b);
            dentro.set(b + e, b + e, w - b - e, h - b - e);
            vidro.reset();
            vidro.setFillType(Path.FillType.EVEN_ODD);
            vidro.addRoundRect(fora, px(RAIO), px(RAIO), Path.Direction.CW);
            vidro.addRoundRect(dentro, px(RAIO - BORDA + 2), px(RAIO - BORDA + 2), Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas c) {
            float raio = px(RAIO);
            // Brilho: alguns contornos largos e bem transparentes, sem desfoque caro
            p.setStyle(Paint.Style.STROKE);
            p.setShader(null);
            for (int i = 4; i >= 1; i--) {
                p.setStrokeWidth(px(BRILHO) * i / 2.5f);
                p.setColor(comAlfa(cor, 10 + (4 - i) * 6));
                c.drawRoundRect(fora, raio, raio, p);
            }
            // Vidro escuro da moldura, com um reflexo no alto
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, fora.top, 0, fora.bottom,
                    new int[] {Color.rgb(40, 26, 54), Color.rgb(16, 8, 26), Color.rgb(10, 4, 18)},
                    new float[] {0f, .35f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(vidro, p);
            p.setShader(null);
            // Fio neon na borda interna e contorno fino por fora
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(px(1) * 1.5f);
            p.setColor(comAlfa(cor, 230));
            float ri = px(RAIO - BORDA + 2);
            c.drawRoundRect(dentro, ri, ri, p);
            p.setStrokeWidth(px(1));
            p.setColor(comAlfa(cor, 90));
            c.drawRoundRect(fora, raio, raio, p);
        }
    }

    /**
     * Tela calma enquanto o vídeo não chega: degradê escuro na cor do cartão, o ícone "respirando",
     * um texto discreto e linhas de varredura bem leves.
     */
    private class Espera extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linhas = new Paint();
        private final long inicio = SystemClock.uptimeMillis();
        private Icone icone;
        private boolean ativa, semSinal;
        private String nomeAparelho = "";
        // Degradês prontos: só são refeitos quando a cor ou o tamanho mudam
        private Shader fundo, halo, faixa;
        private int corDosShaders;
        private float wDosShaders, hDosShaders;

        Espera(Context c) {
            super(c);
            texto.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            texto.setTextAlign(Paint.Align.CENTER);
            texto.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
            texto.setLetterSpacing(.25f);
            linhas.setColor(Color.argb(14, 255, 255, 255));
        }

        void setIcone(int tipo) {
            icone = new Icone(tipo, Color.WHITE, dp);
            invalidate();
        }

        void setSemSinal(boolean sim, String nome) {
            semSinal = sim;
            nomeAparelho = nome;
            invalidate();
        }

        void setAtiva(boolean sim) {
            ativa = sim;
            if (sim) {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float t = (SystemClock.uptimeMillis() - inicio) / 1000f;
            float respiro = semSinal ? .5f : .5f + .5f * (float) Math.sin(t * 2.2f);

            float cx = w / 2, cy = h * .42f;
            if (fundo == null || corDosShaders != cor || wDosShaders != w || hDosShaders != h) {
                corDosShaders = cor;
                wDosShaders = w;
                hDosShaders = h;
                fundo = new LinearGradient(0, 0, 0, h,
                        Color.rgb(Color.red(cor) / 7 + 8, Color.green(cor) / 9 + 4, Color.blue(cor) / 6 + 14),
                        Color.rgb(8, 3, 14), Shader.TileMode.CLAMP);
                halo = new RadialGradient(cx, cy, h * .55f, comAlfa(cor, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP);
                faixa = new LinearGradient(0, 0, 0, h * .3f,
                        Color.TRANSPARENT, Color.argb(18, 255, 255, 255), Shader.TileMode.MIRROR);
            }

            // Fundo: degradê escuro tingido na cor do cartão
            p.setStyle(Paint.Style.FILL);
            p.setShader(fundo);
            c.drawRect(0, 0, w, h, p);
            // Halo atrás do ícone, pulsando pela opacidade
            p.setShader(halo);
            p.setAlpha(semSinal ? 28 : (int) (40 + 50 * respiro));
            c.drawRect(0, 0, w, h, p);
            p.setAlpha(255);
            p.setShader(null);

            // Ícone do aparelho
            if (icone != null) {
                float lado = h * .3f * (semSinal ? 1f : .96f + .04f * respiro);
                icone.setCor(comAlfa(cor, semSinal ? 110 : 200));
                icone.setBounds((int) (cx - lado / 2), (int) (cy - lado / 2), (int) (cx + lado / 2), (int) (cy + lado / 2));
                icone.draw(c);
            }

            // Texto
            String msg;
            if (semSinal) {
                msg = nomeAparelho.equalsIgnoreCase("TV aberta") ? "SEM SINAL" : nomeAparelho.toUpperCase() + " DESLIGADO";
                texto.setColor(Color.argb(150, 255, 255, 255));
            } else {
                int pontos = (int) (t * 2.5f) % 4;
                msg = "SINTONIZANDO" + "...".substring(0, pontos) + "   ".substring(0, 3 - pontos);
                texto.setColor(Color.argb(200, 255, 255, 255));
            }
            c.drawText(msg, cx, h * .8f, texto);

            // Linhas de varredura leves e uma faixa clara rolando devagar
            for (float y = 0; y < h; y += 3 * dp) {
                c.drawRect(0, y, w, y + dp * .6f, linhas);
            }
            if (!semSinal) {
                float fy = (t % 3.5f) / 3.5f * (h * 1.3f) - h * .3f;
                c.save();
                c.translate(0, fy);
                p.setShader(faixa);
                c.drawRect(0, 0, w, h * .3f, p);
                p.setShader(null);
                c.restore();
            }

            if (ativa && getAlpha() > 0f) {
                postInvalidateDelayed(50);
            }
        }
    }
}
