package com.casa.inicio;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Janelinha com a entrada ao vivo (sem som) do cartão em foco.
 *
 * O compositor da Realtek só aceita janelas com uma região visível retangular: um TvView dentro da
 * janela da tela inicial abre um "furo" nela e o compositor descarta quase toda a tela. Por isso o
 * vídeo fica numa janela própria do tamanho exato dele, e a moldura (que cobre os cantos retos do
 * vídeo) e a legenda ficam numa terceira janela por cima. Nenhuma janela tem furo.
 *
 * A TV só decodifica um vídeo por vez, então a prévia é liberada antes de abrir algo em tela cheia.
 *
 * As sessões de vídeo da TCL ignoram o tamanho do TvView e mandam o vídeo sempre em tela cheia, atrás
 * de tudo; a janelinha viraria só um recorte dele. Então a escala é pedida direto ao chip
 * ({@code TTvPictureManager.scaleVideoWindow}, em coordenadas do painel) e desfeita ao soltar a prévia.
 * O vídeo fica sempre retangular e por cima das janelas, por isso a borda interna da moldura é reta.
 */
class Previa {
    private static final int PAINEL_L = 1920, PAINEL_A = 1080; // resolução do painel (VSCControlImpl GetPanelInfo)
    private static final long ESPERA_MS = 400; // só sintoniza quando o seletor para no cartão
    private static final int LARGURA = 240, ALTURA = 135; // dp, 16:9
    // RAIO 0: moldura reta por fora também, combinando com o vídeo retangular
    private static final int BRILHO = 10, BORDA = 5, RAIO = 0, LEGENDA = 30; // dp
    private static final int TOPO = 26, DIREITA = 36; // dp, posição da moldura na tela

    // Paleta do pôr do sol de Leonida
    private static final int ROSA = Color.rgb(255, 46, 136);
    private static final int LARANJA = Color.rgb(255, 138, 61);
    private static final int AMARELO = Color.rgb(255, 205, 96);

    private enum Estado { SINTONIZANDO, AO_VIVO, SEM_SINAL }

    private final Activity activity;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;

    private final TvView tv;
    private final FrameLayout camadaVideo;   // janela só com o vídeo
    private final FrameLayout camadaMoldura; // janela com moldura, tela de espera e legenda
    private final Moldura moldura;
    private final Espera espera;
    private final Legenda legenda;
    private boolean anexada;

    private int cor = ROSA;
    private ValueAnimator trocaCor;
    private String nome = "";
    private Estado estado = Estado.SINTONIZANDO;
    private String sintonizado;   // inputId em uso no TvView, ou null
    private Runnable pendente;
    private boolean visivel;

    Previa(Activity a) {
        activity = a;
        wm = a.getWindowManager();
        dp = a.getResources().getDisplayMetrics().density;

        tv = new TvView(a);
        tv.setStreamVolume(0f);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                if (inputId.equals(sintonizado)) {
                    escalarVideo(true);
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
        camadaVideo = new FrameLayout(a);
        camadaVideo.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        camadaVideo.setVisibility(View.INVISIBLE);

        int margem = px(BRILHO + BORDA);
        camadaMoldura = new FrameLayout(a);
        camadaMoldura.setClipChildren(false);
        camadaMoldura.setAlpha(0f);
        camadaMoldura.setVisibility(View.INVISIBLE);
        espera = new Espera(a);
        FrameLayout.LayoutParams lpEspera = new FrameLayout.LayoutParams(px(LARGURA), px(ALTURA));
        lpEspera.leftMargin = lpEspera.topMargin = margem;
        camadaMoldura.addView(espera, lpEspera);
        moldura = new Moldura(a);
        camadaMoldura.addView(moldura, new FrameLayout.LayoutParams(-1, px(ALTURA) + 2 * margem));
        legenda = new Legenda(a);
        FrameLayout.LayoutParams lpLegenda = new FrameLayout.LayoutParams(px(LARGURA), px(LEGENDA));
        lpLegenda.leftMargin = margem;
        lpLegenda.topMargin = px(ALTURA) + 2 * margem - px(4);
        camadaMoldura.addView(legenda, lpLegenda);
    }

    /** Cria as duas janelas por cima da tela inicial; precisa da janela da activity já anexada. */
    void anexar() {
        IBinder token = activity.getWindow().getDecorView().getWindowToken();
        if (anexada || token == null) {
            return;
        }
        int margem = px(BRILHO + BORDA);
        wm.addView(camadaVideo, janela(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, token,
                px(LARGURA), px(ALTURA), px(DIREITA) + margem - px(BRILHO), px(TOPO) + margem - px(BRILHO)));
        wm.addView(camadaMoldura, janela(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL, token,
                px(LARGURA) + 2 * margem, px(ALTURA) + 2 * margem + px(LEGENDA), px(DIREITA) - px(BRILHO), px(TOPO) - px(BRILHO)));
        anexada = true;
    }

    void remover() {
        esconder();
        if (anexada) {
            wm.removeViewImmediate(camadaMoldura);
            wm.removeViewImmediate(camadaVideo);
            anexada = false;
        }
    }

    private WindowManager.LayoutParams janela(int tipo, IBinder token, int w, int h, int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, tipo,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.token = token;
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = x;
        lp.y = y;
        lp.windowAnimations = 0;
        return lp;
    }

    /** Mostra a moldura já em espera e sintoniza a entrada depois de uma pausa curta no cartão. */
    void mostrar(final String inputId, final Uri canal, int novaCor, String novoNome, int icone) {
        if (!anexada) {
            return;
        }
        cancelarPendente();
        nome = novoNome;
        legenda.invalidate();
        espera.setIcone(icone);
        animarCor(novaCor);
        if (!visivel) {
            visivel = true;
            camadaMoldura.setVisibility(View.VISIBLE);
            camadaMoldura.setTranslationY(px(8));
            camadaMoldura.animate().alpha(1f).translationY(0).setDuration(220).start();
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
                camadaVideo.setVisibility(View.VISIBLE);
                tv.tune(inputId, canal);
                escalarVideo(true);
            }
        };
        handler.postDelayed(pendente, ESPERA_MS);
    }

    /** Some com a janelinha e devolve a entrada para o resto da TV. */
    void esconder() {
        cancelarPendente();
        liberarTv();
        espera.setAtiva(false);
        if (visivel) {
            visivel = false;
            camadaMoldura.animate().alpha(0f).translationY(px(8)).setDuration(160).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!visivel) {
                        camadaMoldura.setVisibility(View.INVISIBLE);
                    }
                }
            }).start();
        }
    }

    private void mudarEstado(Estado e) {
        estado = e;
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
            espera.setAtiva(true);
        }
        legenda.invalidate();
    }

    private void animarCor(int alvo) {
        if (trocaCor != null) {
            trocaCor.cancel();
        }
        if (!visivel) {
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
        legenda.invalidate();
    }

    private void liberarTv() {
        if (sintonizado != null) {
            tv.reset();
            sintonizado = null;
            escalarVideo(false);
        }
        camadaVideo.setVisibility(View.INVISIBLE);
    }

    /**
     * Pede ao chip para colocar o vídeo no retângulo da janelinha ({@code naJanela}) ou de volta em
     * tela cheia. A TCL pode refazer a janela ao trocar de canal/entrada, então o pedido se repete logo
     * depois.
     */
    private void escalarVideo(final boolean naJanela) {
        aplicarEscala(naJanela);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (naJanela == (sintonizado != null)) {
                    aplicarEscala(naJanela);
                }
            }
        }, 350);
    }

    private void aplicarEscala(boolean naJanela) {
        int x = 0, y = 0, w = PAINEL_L, h = PAINEL_A;
        if (naJanela) {
            int[] pos = new int[2];
            tv.getLocationOnScreen(pos);
            float fx = PAINEL_L / (float) activity.getResources().getDisplayMetrics().widthPixels;
            float fy = PAINEL_A / (float) activity.getResources().getDisplayMetrics().heightPixels;
            x = Math.round(pos[0] * fx);
            y = Math.round(pos[1] * fy);
            w = Math.round(px(LARGURA) * fx);
            h = Math.round(px(ALTURA) * fy);
        }
        try {
            Class<?> cm = Class.forName("com.tcl.tvmanager.TTvPictureManager");
            Object pm = cm.getMethod("getInstance", Context.class).invoke(null, activity);
            Class<?> cj = Class.forName("com.tcl.tvmanager.vo.EnTCLWindow");
            Class<?> cr = Class.forName("com.tcl.tvmanager.vo.VideoWindowRect");
            Object rect = cr.newInstance();
            cr.getField("x").setInt(rect, x);
            cr.getField("y").setInt(rect, y);
            cr.getField("width").setInt(rect, w);
            cr.getField("height").setInt(rect, h);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object principal = Enum.valueOf((Class) cj, "EN_TCL_MAIN");
            cm.getMethod("scaleVideoWindow", cj, cr).invoke(pm, principal, rect);
            Log.d("CasaPrevia", "scaleVideoWindow " + x + "," + y + " " + w + "x" + h);
        } catch (Throwable t) {
            Log.w("CasaPrevia", "não consegui escalar o vídeo", t);
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
     * fio com o degradê do pôr do sol e um brilho suave na cor do cartão.
     */
    private class Moldura extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path vidro = new Path();
        private final RectF fora = new RectF(), dentro = new RectF();
        private Shader fioShader, vidroShader;

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
            vidro.addRect(dentro, Path.Direction.CW); // reto: o vídeo é retangular e fica por cima
            vidroShader = new LinearGradient(0, fora.top, 0, fora.bottom,
                    new int[] {Color.rgb(46, 22, 58), Color.rgb(20, 8, 30), Color.rgb(12, 4, 20)},
                    new float[] {0f, .35f, 1f}, Shader.TileMode.CLAMP);
            fioShader = new LinearGradient(fora.left, fora.top, fora.right, fora.bottom,
                    new int[] {AMARELO, LARANJA, ROSA}, null, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            float raio = px(RAIO);
            // Brilho na cor do cartão: contornos largos e bem transparentes, sem desfoque caro
            p.setStyle(Paint.Style.STROKE);
            p.setShader(null);
            for (int i = 4; i >= 1; i--) {
                p.setStrokeWidth(px(BRILHO) * i / 2.5f);
                p.setColor(comAlfa(cor, 10 + (4 - i) * 7));
                c.drawRoundRect(fora, raio, raio, p);
            }
            // Vidro escuro da moldura
            p.setStyle(Paint.Style.FILL);
            p.setShader(vidroShader);
            c.drawPath(vidro, p);
            // Fio do pôr do sol na borda interna e contorno fino na cor do cartão por fora
            p.setStyle(Paint.Style.STROKE);
            p.setShader(fioShader);
            p.setStrokeWidth(px(2));
            c.drawRect(dentro, p);
            p.setShader(null);
            p.setStrokeWidth(px(1));
            p.setColor(comAlfa(cor, 120));
            c.drawRoundRect(fora, raio, raio, p);
        }
    }

    /** Nome do aparelho em letras grossas e inclinadas com o degradê do pôr do sol, e o selo de estado. */
    private class Legenda extends View {
        private final Paint nomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint seloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fundoSelo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pilula = new RectF();
        private final long inicio = SystemClock.uptimeMillis();

        Legenda(Context c) {
            super(c);
            nomePaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            nomePaint.setTextSize(sp(17));
            nomePaint.setLetterSpacing(.04f);
            seloPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            seloPaint.setTextSize(sp(10));
            seloPaint.setLetterSpacing(.14f);
            seloPaint.setTextAlign(Paint.Align.CENTER);
        }

        private float sp(int v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), base = h * .72f;
            String titulo = nome.toUpperCase();
            nomePaint.setShader(new LinearGradient(0, base - nomePaint.getTextSize(), 0, base,
                    new int[] {AMARELO, LARANJA, ROSA}, null, Shader.TileMode.CLAMP));
            nomePaint.setShadowLayer(px(6), 0, 0, comAlfa(ROSA, 150));
            c.drawText(titulo, px(2), base, nomePaint);

            String texto;
            int fundo, frente;
            boolean pisca = false;
            switch (estado) {
                case AO_VIVO:
                    texto = "●  AO VIVO";
                    fundo = ROSA;
                    frente = Color.WHITE;
                    pisca = true;
                    break;
                case SINTONIZANDO:
                    texto = "SINTONIZANDO";
                    fundo = Color.argb(70, 255, 255, 255);
                    frente = Color.argb(230, 255, 255, 255);
                    break;
                default:
                    texto = "SEM SINAL";
                    fundo = Color.argb(45, 255, 255, 255);
                    frente = Color.argb(170, 255, 255, 255);
            }
            float lp = seloPaint.measureText(texto) + px(18), ap = px(18);
            pilula.set(getWidth() - lp, base - ap * .78f, getWidth(), base - ap * .78f + ap);
            fundoSelo.setColor(fundo);
            c.drawRoundRect(pilula, ap / 2, ap / 2, fundoSelo);
            seloPaint.setColor(frente);
            if (pisca) {
                // o "●" pulsa de leve, como gravação
                float t = (SystemClock.uptimeMillis() - inicio) / 1000f;
                seloPaint.setAlpha((int) (200 + 55 * Math.sin(t * 4)));
                postInvalidateDelayed(80);
            }
            c.drawText(texto, pilula.centerX(), pilula.centerY() + seloPaint.getTextSize() * .35f, seloPaint);
        }
    }

    /**
     * Tela calma enquanto o vídeo não chega: céu de pôr do sol escurecido, o ícone "respirando" com
     * halo na cor do cartão, texto discreto e linhas de varredura bem leves.
     */
    private class Espera extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linhas = new Paint();
        private final long inicio = SystemClock.uptimeMillis();
        private Icone icone;
        private boolean ativa;
        // Degradês prontos: só são refeitos quando a cor ou o tamanho mudam
        private Shader ceu, halo, faixa;
        private int corDosShaders;
        private float hDosShaders;

        Espera(Context c) {
            super(c);
            texto.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            texto.setTextAlign(Paint.Align.CENTER);
            texto.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
            texto.setLetterSpacing(.25f);
            linhas.setColor(Color.argb(14, 255, 255, 255));
        }

        void setIcone(int tipo) {
            icone = new Icone(tipo, Color.WHITE, dp);
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
            boolean semSinal = estado == Estado.SEM_SINAL;
            float respiro = semSinal ? .5f : .5f + .5f * (float) Math.sin(t * 2.2f);
            float cx = w / 2, cy = h * .42f;

            if (ceu == null || corDosShaders != cor || hDosShaders != h) {
                corDosShaders = cor;
                hDosShaders = h;
                ceu = new LinearGradient(0, 0, 0, h,
                        new int[] {Color.rgb(16, 6, 30), Color.rgb(46, 12, 52), Color.rgb(84, 26, 60)},
                        new float[] {0f, .6f, 1f}, Shader.TileMode.CLAMP);
                halo = new RadialGradient(cx, cy, h * .55f, comAlfa(cor, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP);
                faixa = new LinearGradient(0, 0, 0, h * .3f,
                        Color.TRANSPARENT, Color.argb(16, 255, 255, 255), Shader.TileMode.MIRROR);
            }

            // Céu escurecido de pôr do sol
            p.setStyle(Paint.Style.FILL);
            p.setShader(ceu);
            c.drawRect(0, 0, w, h, p);
            // Halo atrás do ícone, pulsando pela opacidade
            p.setShader(halo);
            p.setAlpha(semSinal ? 30 : (int) (45 + 55 * respiro));
            c.drawRect(0, 0, w, h, p);
            p.setAlpha(255);
            p.setShader(null);

            if (icone != null) {
                float lado = h * .3f * (semSinal ? 1f : .96f + .04f * respiro);
                icone.setCor(semSinal ? Color.argb(110, 255, 255, 255) : comAlfa(cor, 230));
                icone.setBounds((int) (cx - lado / 2), (int) (cy - lado / 2), (int) (cx + lado / 2), (int) (cy + lado / 2));
                icone.draw(c);
            }

            String msg;
            if (semSinal) {
                msg = nome.equalsIgnoreCase("TV aberta") ? "SEM SINAL" : nome.toUpperCase() + " DESLIGADO";
                texto.setColor(Color.argb(150, 255, 255, 255));
            } else {
                int pontos = (int) (t * 2.5f) % 4;
                msg = "SINTONIZANDO" + "...".substring(0, pontos) + "   ".substring(0, 3 - pontos);
                texto.setColor(Color.argb(210, 255, 255, 255));
            }
            c.drawText(msg, cx, h * .8f, texto);

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
