package com.casa.inicio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Region;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Fundo da tela inicial: pôr do sol neon numa praia de Vice City, com skyline art déco, palmeiras,
 * estrelas e gaivotas. O que não mexe (incluindo o escurecimento) é desenhado uma vez num bitmap;
 * por cima, a cada quadro, só entram os detalhes animados, bem sutis, a 20 fps e parados quando a
 * tela não está visível. As cores animadas já vêm escurecidas para casar com o fundo.
 */
class Cenario extends View {
    private static final long QUADRO_MS = 50;
    /** Quanto o véu escuro do fundo deixa passar; aplicado às camadas animadas que ficam por cima dele. */
    private static final float VEU = .63f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBitmap = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Random sorteio = new Random(6);
    private final long inicio = SystemClock.uptimeMillis();
    private boolean animando;

    private Bitmap fundo;
    private Bitmap predios;        // skyline da frente, desenhada acima do halo do sol
    private int prediosTopo;       // y onde a faixa dos prédios começa
    private Path silhueta;         // contorno de todos os prédios da frente
    private float w, h, horizonte, sx, sy, raio;

    private float[][] estrelas;    // x, y, raio, fase, velocidade
    private float[][] janelas;     // x, y, largura, altura, cor, fase
    private float[][] gaivotas;    // y, velocidade, escala, fase
    private Shader brilhoSol;
    private Shader[] reflexos;
    private float[][] reflexoGeo;  // y, meia largura, espessura
    private Path[][] palmeiras;    // por palmeira: tronco, folhas, folíolos
    private float[][] palmeiraGeo; // baseX, baseY, altura, amplitude, velocidade, fase

    Cenario(Context c) {
        super(c);
    }

    void setAnimando(boolean sim) {
        animando = sim;
        if (sim) {
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw;
        h = nh;
        horizonte = h * .62f;
        raio = h * .2f;
        sx = w * .62f;
        sy = horizonte - raio * .5f;
        prepararDetalhes();
        gerarPredios();
        if (fundo != null) {
            fundo.recycle();
            predios.recycle();
        }
        fundo = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888);
        desenharFundo(new Canvas(fundo));
        // Só a faixa do horizonte onde há prédios, para não pintar uma tela inteira a mais por quadro
        prediosTopo = (int) (horizonte - h * .16f);
        predios = Bitmap.createBitmap(nw, (int) (horizonte + 2) - prediosTopo, Bitmap.Config.ARGB_8888);
        Canvas cp = new Canvas(predios);
        cp.translate(0, -prediosTopo);
        desenharPredios(cp);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (fundo == null) {
            return;
        }
        float t = (SystemClock.uptimeMillis() - inicio) / 1000f;
        c.drawBitmap(fundo, 0, 0, pBitmap);

        // Estrelas piscando devagar
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        for (float[] e : estrelas) {
            float brilho = .55f + .45f * (float) Math.sin(t * e[4] + e[3]);
            p.setColor(Color.argb((int) (150 * brilho), 255, 235, 250));
            c.drawCircle(e[0], e[1], e[2], p);
        }

        // Halo do sol "respirando"
        p.setShader(brilhoSol);
        p.setAlpha((int) ((165 + 60 * Math.sin(t * .6f)) * VEU));
        c.save();
        c.clipRect(0, 0, w, horizonte);
        c.drawCircle(sx, sy, raio * 2.3f, p);
        c.restore();
        p.setAlpha(255);

        c.drawBitmap(predios, 0, prediosTopo, pBitmap);

        // Janelas acesas do skyline, algumas tremulando
        p.setShader(null);
        for (float[] j : janelas) {
            float a = VEU * (j[5] < 0 ? 1f : .6f + .4f * (float) Math.sin(t * 1.3f + j[5]));
            int cor = (int) j[4];
            p.setColor(Color.argb((int) (Color.alpha(cor) * a), Color.red(cor), Color.green(cor), Color.blue(cor)));
            c.drawRect(j[0], j[1], j[0] + j[2], j[1] + j[3], p);
        }

        // Reflexo do sol ondulando
        for (int i = 0; i < reflexos.length; i++) {
            float[] g = reflexoGeo[i];
            float desvio = raio * .07f * (float) Math.sin(t * .9f + i * 1.7f);
            float estica = 1f + .08f * (float) Math.sin(t * 1.3f + i * 2.3f);
            c.save();
            c.translate(sx + desvio, 0);
            c.scale(estica, 1f);
            p.setShader(reflexos[i]);
            c.drawRect(-g[1], g[0], g[1], g[0] + g[2], p);
            c.restore();
        }
        p.setShader(null);

        // Gaivotas cruzando o céu
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.argb(170, 20, 6, 30));
        for (float[] g : gaivotas) {
            float x = ((t * g[1] + g[3] * w) % (w * 1.4f)) - w * .2f;
            float y = g[0] + 6 * (float) Math.sin(t * .5f + g[3] * 5);
            gaivota(c, x, y, g[2], (float) Math.sin(t * 4.5f + g[3] * 9));
        }

        aeronaves(c, t);
        bando(c, t);

        // Palmeiras balançando de leve (os Paths são montados uma vez; aqui só gira)
        p.setColor(Color.rgb(8, 2, 16));
        for (int i = 0; i < palmeiras.length; i++) {
            float[] g = palmeiraGeo[i];
            Path[] pp = palmeiras[i];
            c.save();
            c.rotate(g[3] * (float) Math.sin(t * g[4] + g[5]), g[0], g[1]);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(g[2] * .035f);
            c.drawPath(pp[0], p);
            p.setStrokeWidth(g[2] * .028f);
            c.drawPath(pp[1], p);
            p.setStrokeWidth(g[2] * .012f);
            c.drawPath(pp[2], p);
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);

        if (animando) {
            postInvalidateDelayed(QUADRO_MS);
        }
    }

    private void prepararDetalhes() {
        estrelas = new float[46][];
        for (int i = 0; i < estrelas.length; i++) {
            estrelas[i] = new float[] {
                    sorteio.nextFloat() * w, sorteio.nextFloat() * h * .4f, .6f + sorteio.nextFloat() * 1.4f,
                    sorteio.nextFloat() * 6.3f, .4f + sorteio.nextFloat() * 1.6f};
        }

        gaivotas = new float[][] {
                {h * .2f, w * .018f, h * .016f, .1f},
                {h * .25f, w * .014f, h * .012f, .35f},
                {h * .17f, w * .021f, h * .01f, .7f}};

        // Anel de brilho em volta do sol (transparente no meio para não lavar o disco).
        float borda = raio / (raio * 2.3f);
        brilhoSol = new RadialGradient(sx, sy, raio * 2.3f,
                new int[] {Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(140, 255, 80, 150), Color.argb(45, 255, 60, 140), Color.TRANSPARENT},
                new float[] {0f, borda * .97f, borda * 1.02f, borda * 1.6f, 1f}, Shader.TileMode.CLAMP);

        int n = 14;
        reflexos = new Shader[n];
        reflexoGeo = new float[n][];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float meia = raio * (1.05f - .75f * t);
            float ry = horizonte + 6 + (h - horizonte) * .75f * t * t;
            float vinheta = Math.max(0f, (ry - h * .45f) / (h * .55f)) * 230f / 255f;
            int alfa = (int) (210 * (1f - t * .85f) * VEU * (1f - vinheta));
            int cor = t < .5f ? Color.argb(alfa, 255, 170, 90) : Color.argb(alfa, 255, 70, 140);
            reflexos[i] = new LinearGradient(-meia, 0, meia, 0,
                    new int[] {Color.TRANSPARENT, cor, cor, Color.TRANSPARENT},
                    new float[] {0f, .25f, .75f, 1f}, Shader.TileMode.CLAMP);
            reflexoGeo[i] = new float[] {ry, meia, 1.5f + 3.5f * t};
        }

        palmeiraGeo = new float[][] {
                {w * .07f, h * 1.02f, h * .78f, 1.1f, .55f, 0f, -1f},
                {w * .17f, h * 1.02f, h * .55f, 1.4f, .62f, 1.3f, 1f},
                {w * .94f, h * 1.02f, h * .7f, 1.2f, .5f, 2.6f, 1f}};
        palmeiras = new Path[palmeiraGeo.length][];
        for (int i = 0; i < palmeiraGeo.length; i++) {
            float[] g = palmeiraGeo[i];
            palmeiras[i] = montarPalmeira(g[0], g[1], g[2], g[6]);
        }
    }

    private void desenharFundo(Canvas c) {
        // Céu
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, horizonte,
                new int[] {Color.rgb(14, 4, 30), Color.rgb(52, 10, 68), Color.rgb(140, 28, 96), Color.rgb(255, 106, 61)},
                new float[] {0f, .45f, .8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizonte, p);

        // Nuvens finas e alongadas, rosadas pela luz do sol, com as pontas sumindo
        float[][] nuvens = {{.7f, 1.35f, .2f, 5}, {.5f, 1.62f, .14f, 4}, {.82f, 1.85f, .12f, 3}, {.6f, 2.15f, .1f, 3}};
        for (float[] n : nuvens) {
            float nx = w * n[0], ny = horizonte - raio * n[1], meia = w * n[2];
            int cor = Color.argb(70, 255, 140, 175);
            p.setShader(new LinearGradient(nx - meia, 0, nx + meia, 0,
                    new int[] {Color.TRANSPARENT, cor, cor, Color.TRANSPARENT},
                    new float[] {0f, .35f, .65f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(nx - meia, ny, nx + meia, ny + n[3], n[3], n[3], p);
        }
        p.setShader(null);

        // Sol com recortes horizontais
        Path recortes = new Path();
        float y = sy - raio * .3f;
        for (int i = 0; i < 8 && y < horizonte; i++) {
            float faixa = raio * (.025f + i * .02f);
            recortes.addRect(sx - raio, y, sx + raio, y + faixa, Path.Direction.CW);
            y += faixa + raio * (.13f - i * .011f);
        }
        p.setShader(new LinearGradient(0, sy - raio, 0, sy + raio,
                new int[] {Color.rgb(255, 226, 110), Color.rgb(255, 140, 70), Color.rgb(255, 46, 136)},
                new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(0, 0, w, horizonte);
        c.clipPath(recortes, Region.Op.DIFFERENCE);
        c.drawCircle(sx, sy, raio, p);
        c.restore();

        skylineDistante(c);

        // Mar
        p.setShader(new LinearGradient(0, horizonte, 0, h,
                Color.rgb(46, 10, 52), Color.rgb(6, 2, 14), Shader.TileMode.CLAMP));
        c.drawRect(0, horizonte, w, h, p);

        // Reflexo escuro dos prédios na água, achatado e sumindo com a distância
        c.save();
        c.scale(1f, -.45f, 0, horizonte);
        p.setShader(new LinearGradient(0, horizonte, 0, horizonte - h * .12f,
                Color.argb(170, 14, 3, 24), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawPath(silhueta, p);
        c.restore();

        // Linha do horizonte em neon
        p.setShader(new LinearGradient(0, 0, w, 0,
                new int[] {Color.TRANSPARENT, Color.argb(200, 255, 120, 170), Color.TRANSPARENT},
                new float[] {0f, sx / w, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, horizonte, w, horizonte + 1.5f, p);

        // Véu escuro e vinhetas (as camadas animadas já levam isso em conta nas cores)
        p.setShader(null);
        p.setColor(Color.argb(95, 8, 2, 18));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * .45f, 0, h, Color.TRANSPARENT, Color.argb(230, 6, 2, 14),
                Shader.TileMode.CLAMP));
        c.drawRect(0, h * .45f, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * .3f, Color.argb(160, 6, 2, 14), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * .3f, p);
        p.setShader(null);
    }

    /** Fileira de prédios bem ao fundo, clareada pela névoa do pôr do sol, para dar profundidade. */
    private void skylineDistante(Canvas c) {
        Random r = new Random(11);
        Path longe = new Path();
        float x = w * .2f;
        while (x < w * .97f) {
            float larg = w * (.012f + r.nextFloat() * .022f);
            float alt = h * (.015f + r.nextFloat() * .045f);
            longe.addRect(x, horizonte - alt, x + larg, horizonte + 1, Path.Direction.CW);
            if (r.nextFloat() < .25f) {
                longe.addRect(x + larg * .45f, horizonte - alt * 1.35f, x + larg * .55f, horizonte - alt, Path.Direction.CW);
            }
            x += larg + w * r.nextFloat() * .006f;
        }
        p.setShader(new LinearGradient(0, horizonte - h * .07f, 0, horizonte,
                Color.argb(150, 120, 34, 100), Color.argb(190, 150, 46, 96), Shader.TileMode.CLAMP));
        c.drawPath(longe, p);
        p.setShader(null);
    }

    /**
     * Gera os prédios art déco da frente: o contorno vai para {@link #silhueta} (usado na camada dos
     * prédios e no reflexo da água) e as janelas para a camada animada.
     */
    private void gerarPredios() {
        silhueta = new Path();
        int[] coresJanela = {
                Color.argb(220, 255, 70, 160), Color.argb(200, 40, 220, 240), Color.argb(200, 255, 200, 110)};
        java.util.ArrayList<float[]> lista = new java.util.ArrayList<>();
        Path.Direction cw = Path.Direction.CW;

        float x = w * .27f;
        float fim = w * .9f;
        while (x < fim) {
            float larg = w * (.018f + sorteio.nextFloat() * .028f);
            float alt = h * (.025f + sorteio.nextFloat() * .075f);
            // perto do sol, prédios mais baixos para não cobri-lo
            if (Math.abs(x - sx) < raio * .9f) {
                alt *= .55f;
            }
            float topo = horizonte - alt;
            silhueta.addRect(x, topo, x + larg, horizonte + 1, cw);

            int estilo = sorteio.nextInt(4);
            if (estilo == 0) {
                // topo escalonado art déco
                silhueta.addRect(x + larg * .15f, topo - alt * .12f, x + larg * .85f, topo, cw);
                silhueta.addRect(x + larg * .32f, topo - alt * .22f, x + larg * .68f, topo, cw);
                silhueta.addRect(x + larg * .47f, topo - alt * .38f, x + larg * .53f, topo, cw);
            } else if (estilo == 1) {
                // antena
                silhueta.addRect(x + larg * .46f, topo - alt * .3f, x + larg * .54f, topo, cw);
            } else if (estilo == 2) {
                // coroa arredondada, típica de hotel de Ocean Drive
                silhueta.addOval(x + larg * .2f, topo - larg * .22f, x + larg * .8f, topo + larg * .22f, cw);
            }

            for (float jy = topo + alt * .12f; jy < horizonte - 3; jy += alt * .16f + 2) {
                for (float jx = x + larg * .18f; jx < x + larg * .82f; jx += larg * .3f) {
                    if (sorteio.nextFloat() < .28f) {
                        float fase = sorteio.nextFloat() < .3f ? sorteio.nextFloat() * 6.3f : -1f;
                        lista.add(new float[] {jx, jy, Math.max(1.5f, larg * .1f), 2f,
                                coresJanela[sorteio.nextInt(coresJanela.length)], fase});
                    }
                }
            }
            x += larg + w * (.002f + sorteio.nextFloat() * .01f);
        }
        janelas = lista.toArray(new float[0][]);
        silhueta.setFillType(Path.FillType.WINDING);
    }

    /**
     * Prédios contra o sol: luz de contorno quente nas bordas viradas para o sol (mais forte perto
     * dele), depois a silhueta por cima deslocada para longe do sol, deixando só a beirada acesa.
     */
    private void desenharPredios(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK); // opaco: o alfa da cor multiplica o do shader
        p.setShader(new RadialGradient(sx, sy, w * .42f,
                new int[] {Color.argb(235, 255, 186, 120), Color.argb(150, 255, 80, 150), Color.argb(40, 200, 60, 150)},
                new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(silhueta, p);

        // Corpo escuro com leve névoa perto do chão
        p.setShader(new LinearGradient(0, horizonte - h * .1f, 0, horizonte,
                Color.rgb(16, 4, 28), Color.rgb(30, 8, 42), Shader.TileMode.CLAMP));
        float borda = Math.max(1.2f, h * .0018f);
        c.save();
        c.clipRect(0, 0, sx, h);
        c.translate(-borda, borda);
        c.drawPath(silhueta, p);
        c.restore();
        c.save();
        c.clipRect(sx, 0, w, h);
        c.translate(borda, borda);
        c.drawPath(silhueta, p);
        c.restore();
        p.setShader(null);

        // Corta a sobra deslocada abaixo do horizonte
        p.setColor(Color.TRANSPARENT);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawRect(0, horizonte + 1, w, h, p);
        p.setXfermode(null);
    }

    private void gaivota(Canvas c, float x, float y, float tam, float asa) {
        Path g = new Path();
        float dobra = tam * (.35f + .35f * asa);
        g.moveTo(x - tam, y - dobra);
        g.quadTo(x - tam * .45f, y - tam * .15f, x, y);
        g.quadTo(x + tam * .45f, y - tam * .15f, x + tam, y - dobra);
        p.setStrokeWidth(Math.max(1.5f, tam * .14f));
        c.drawPath(g, p);
    }

    /** Silhuetas pequenas e escuras, como os pássaros: um jato alto e um helicóptero mais baixo. */
    private void aeronaves(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(175, 20, 6, 30));

        // Jato: a cada 55 s, ~35 s para cruzar, alternando o sentido
        float[] jato = travessia(t, 55f, 35f, 8f, h * .03f);
        if (jato != null) {
            jato(c, jato[0], h * .22f, h * .011f, jato[1] > 0);
        }

        // Helicóptero: a cada 80 s, ~40 s para cruzar, balançando um pouco
        float[] heli = travessia(t, 80f, 40f, 40f, h * .03f);
        if (heli != null) {
            float y = h * .37f + h * .006f * (float) Math.sin(t * 1.1f);
            helicoptero(c, heli[0], y, h * .012f, heli[1] > 0, t);
        }
    }

    /** Posição x e sentido (+1 direita, -1 esquerda) de algo que cruza a tela em ciclos; null fora da janela. */
    private float[] travessia(float t, float ciclo, float duracao, float atraso, float margem) {
        float corrido = t + ciclo - atraso;
        float fase = corrido % ciclo;
        if (fase > duracao) {
            return null;
        }
        boolean direita = ((int) (corrido / ciclo)) % 2 == 0;
        float prog = fase / duracao;
        float x = -margem + (w + margem * 2) * (direita ? prog : 1f - prog);
        return new float[] {x, direita ? 1f : -1f};
    }

    private void jato(Canvas c, float x, float y, float s, boolean direita) {
        c.save();
        c.translate(x, y);
        if (!direita) {
            c.scale(-1, 1);
        }
        c.drawRoundRect(-s * 2.2f, -s * .2f, s * 2.2f, s * .2f, s * .2f, s * .2f, p);  // fuselagem
        Path asa = new Path();
        asa.moveTo(s * .4f, 0);
        asa.lineTo(-s * .7f, s * .9f);
        asa.lineTo(-s * 1.0f, s * .9f);
        asa.lineTo(-s * .5f, 0);
        asa.close();
        c.drawPath(asa, p);
        Path cauda = new Path();
        cauda.moveTo(-s * 1.7f, 0);
        cauda.lineTo(-s * 2.2f, -s * .9f);
        cauda.lineTo(-s * 1.95f, -s * .9f);
        cauda.lineTo(-s * 1.3f, 0);
        cauda.close();
        c.drawPath(cauda, p);
        c.restore();
    }

    private void helicoptero(Canvas c, float x, float y, float s, boolean direita, float t) {
        c.save();
        c.translate(x, y);
        if (!direita) {
            c.scale(-1, 1);
        }
        c.rotate(4f);                                                       // levemente inclinado para a frente
        c.drawOval(-s * .9f, -s * .45f, s * .9f, s * .45f, p);             // cabine
        c.drawRect(-s * 2.4f, -s * .12f, -s * .6f, s * .1f, p);             // cauda
        c.drawRect(-s * 2.5f, -s * .45f, -s * 2.25f, s * .1f, p);           // deriva
        c.drawRect(-s * .08f, -s * .75f, s * .08f, -s * .4f, p);            // mastro
        // Rotor principal "girando": a largura visível oscila rápido
        float rotor = s * (1.4f + .9f * Math.abs((float) Math.sin(t * 9f)));
        c.drawRect(-rotor, -s * .82f, rotor, -s * .72f, p);
        // Rotor de cauda
        float rc = s * (.18f + .2f * Math.abs((float) Math.sin(t * 13f)));
        c.drawRect(-s * 2.42f - .5f, -s * .2f - rc, -s * 2.33f, -s * .2f + rc, p);
        // Esquis
        c.drawRect(-s * .7f, s * .62f, s * .7f, s * .7f, p);
        c.drawRect(-s * .4f, s * .4f, -s * .32f, s * .62f, p);
        c.drawRect(s * .32f, s * .4f, s * .4f, s * .62f, p);
        c.restore();
    }

    /** Bando de passarinhos em V cruzando da direita para a esquerda a cada 45 s. */
    private void bando(Canvas c, float t) {
        final float ciclo = 45f, travessia = 30f, atraso = 30f;
        float fase = (t + ciclo - atraso) % ciclo;
        if (fase > travessia) {
            return;
        }
        float lider = w * 1.1f - w * 1.3f * (fase / travessia);
        float base = h * .3f + h * .015f * (float) Math.sin(t * .3f);
        float tam = h * .008f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.argb(150, 20, 6, 30));
        for (int i = 0; i < 9; i++) {
            int fila = (i + 1) / 2;
            float lado = i % 2 == 0 ? 1f : -1f;
            float x = lider + fila * tam * 3.2f;
            float y = base + (i == 0 ? 0 : lado * fila * tam * 2.4f);
            gaivota(c, x, y, tam, (float) Math.sin(t * 6f + i * .8f));
        }
    }

    /** Monta os traços de uma palmeira: [tronco, folhas, folíolos], cada um com sua espessura. */
    private static Path[] montarPalmeira(float baseX, float baseY, float altura, float lado) {
        float topoX = baseX + lado * altura * .18f, topoY = baseY - altura;

        Path tronco = new Path();
        tronco.moveTo(baseX, baseY);
        tronco.quadTo(baseX + lado * altura * .02f, baseY - altura * .6f, topoX, topoY);

        Path folhas = new Path();
        folhas.addCircle(topoX, topoY + altura * .02f, altura * .015f, Path.Direction.CW);
        Path foliolos = new Path();
        float[][] direcoes = {{-1f, .05f}, {-.8f, .28f}, {-.45f, .4f}, {.45f, .4f}, {.8f, .28f}, {1f, .05f}, {-.15f, -.22f}, {.2f, -.2f}};
        float comp = altura * .32f;
        for (float[] f : direcoes) {
            float fx = topoX + f[0] * comp;
            float fy = topoY + f[1] * comp;
            folhas.moveTo(topoX, topoY);
            folhas.quadTo(topoX + f[0] * comp * .5f, topoY - comp * .22f, fx, fy);
            for (int i = 1; i < 6; i++) {
                float t = i / 6f;
                float x = topoX + (fx - topoX) * t, y = topoY + (fy - topoY) * t - comp * .12f * (float) Math.sin(Math.PI * t);
                foliolos.moveTo(x, y);
                foliolos.lineTo(x + f[0] * comp * .06f, y + comp * .1f);
            }
        }
        return new Path[] {tronco, folhas, foliolos};
    }
}
