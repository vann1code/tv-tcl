package com.casa.inicio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Relógio no estilo da tela de um videocassete: "▶ PLAY" piscando, hora em fonte monoespaçada com
 * as bordas vermelha/ciano desalinhadas, linhas de varredura e, de vez em quando, um tremor de fita.
 * Embaixo, a data e um contador de fita com o tempo que a tela inicial está aberta.
 */
class RelogioVhs extends View {
    private static final String[] DIAS = {"DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB"};
    private static final String[] MESES = {"JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"};
    private static final int VERMELHO = Color.argb(200, 255, 40, 90);
    private static final int CIANO = Color.argb(200, 40, 230, 255);

    private final Paint grande = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pequeno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linhas = new Paint();
    private final float dp;
    private long abertoDesde = SystemClock.uptimeMillis();
    private boolean animando;

    RelogioVhs(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        grande.setTypeface(mono);
        grande.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 70, c.getResources().getDisplayMetrics()));
        pequeno.setTypeface(mono);
        pequeno.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17, c.getResources().getDisplayMetrics()));
        linhas.setColor(Color.argb(110, 0, 0, 0));
        linhas.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        // Camada própria: o DST_OUT das linhas recorta só o texto, e a tela compõe a textura pronta
        // a cada quadro do cenário sem redesenhar o relógio.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setAnimando(boolean sim) {
        animando = sim;
        if (sim) {
            abertoDesde = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float largura = Math.max(grande.measureText("00:00"), pequeno.measureText("SÁB 00.OUT.0000   SP 0:00:00"));
        float altura = pequeno.getTextSize() * 2.6f + grande.getTextSize() * 1.05f;
        setMeasuredDimension((int) (largura + 16 * dp), (int) (altura + 12 * dp));
    }

    @Override
    protected void onDraw(Canvas c) {
        long agora = SystemClock.uptimeMillis();
        Calendar cal = Calendar.getInstance();
        float x0 = 6 * dp;
        float yPlay = pequeno.getTextSize() + 4 * dp;
        float yHora = yPlay + grande.getTextSize() * 1.05f;
        float yData = yHora + pequeno.getTextSize() * 1.5f;

        // Tremor de fita: a cada ~7 s, por 250 ms, a imagem desliza uns pixels
        boolean tremendo = (agora % 7000) < 250;
        float tremor = tremendo ? (float) Math.sin(agora * .09) * 5 * dp : 0f;

        // ▶ PLAY piscando
        if ((agora % 1600) < 1100) {
            texto(c, "▶ PLAY", x0 + tremor * .4f, yPlay, pequeno, 1.2f);
        }

        String hora = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        texto(c, hora, x0 + tremor, yHora, grande, 2.6f);

        long seg = (agora - abertoDesde) / 1000;
        String linha = String.format(Locale.US, "%s %02d.%s.%d   SP %d:%02d:%02d",
                DIAS[cal.get(Calendar.DAY_OF_WEEK) - 1], cal.get(Calendar.DAY_OF_MONTH), MESES[cal.get(Calendar.MONTH)],
                cal.get(Calendar.YEAR), seg / 3600, (seg / 60) % 60, seg % 60);
        texto(c, linha, x0 + tremor * .6f, yData, pequeno, 1.2f);

        // Linhas de varredura: recortam só o texto (DST_OUT), sem escurecer o fundo em volta
        float passo = 3 * dp;
        for (float y = 0; y < getHeight(); y += passo) {
            c.drawRect(0, y, getWidth(), y + dp, linhas);
        }

        if (animando) {
            // Fora do tremor basta acompanhar o piscar do PLAY e o contador de segundos
            postInvalidateDelayed(tremendo ? 50 : 200);
        }
    }

    /** Texto com aberração cromática: cópias vermelha e ciano desalinhadas, branco por cima. */
    private void texto(Canvas c, String s, float x, float y, Paint base, float desvio) {
        float d = desvio * dp;
        base.setShadowLayer(0, 0, 0, 0);
        base.setColor(VERMELHO);
        c.drawText(s, x - d, y, base);
        base.setColor(CIANO);
        c.drawText(s, x + d, y, base);
        base.setColor(Color.argb(245, 250, 248, 255));
        base.setShadowLayer(6 * dp, 0, 0, Color.argb(120, 255, 255, 255));
        c.drawText(s, x, y, base);
        base.setShadowLayer(0, 0, 0, 0);
    }
}
