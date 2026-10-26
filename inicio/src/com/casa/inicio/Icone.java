package com.casa.inicio;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Ícones desenhados à mão, para não depender de emoji nem de recursos. */
class Icone extends Drawable {
    static final int PLAY = 0;
    static final int TV = 1;
    static final int ENTRADA = 2;
    static final int ENGRENAGEM = 3;
    static final int CONTROLE = 4;
    static final int NOTEBOOK = 5;

    private final int tipo;
    private final float traco;
    private final Paint linha = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cheio = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icone(int tipo, int cor, float dp) {
        this.tipo = tipo;
        this.traco = 3.5f * dp;
        linha.setStyle(Paint.Style.STROKE);
        linha.setStrokeWidth(traco);
        linha.setStrokeCap(Paint.Cap.ROUND);
        linha.setStrokeJoin(Paint.Join.ROUND);
        cheio.setStyle(Paint.Style.FILL);
        setCor(cor);
    }

    void setCor(int cor) {
        linha.setColor(cor);
        cheio.setColor(cor);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f;
        float y = b.top + (b.height() - s) / 2f;
        switch (tipo) {
            case PLAY: play(c, x, y, s); break;
            case TV: tv(c, x, y, s); break;
            case ENTRADA: entrada(c, x, y, s); break;
            case CONTROLE: controle(c, x, y, s); break;
            case NOTEBOOK: notebook(c, x, y, s); break;
            default: engrenagem(c, x, y, s); break;
        }
    }

    private void play(Canvas c, float x, float y, float s) {
        c.drawRoundRect(new RectF(x + s * .06f, y + s * .2f, x + s * .94f, y + s * .8f), s * .16f, s * .16f, linha);
        Path p = new Path();
        p.moveTo(x + s * .42f, y + s * .36f);
        p.lineTo(x + s * .64f, y + s * .5f);
        p.lineTo(x + s * .42f, y + s * .64f);
        p.close();
        c.drawPath(p, cheio);
    }

    private void tv(Canvas c, float x, float y, float s) {
        c.drawRoundRect(new RectF(x + s * .06f, y + s * .18f, x + s * .94f, y + s * .74f), s * .08f, s * .08f, linha);
        c.drawLine(x + s * .32f, y + s * .88f, x + s * .68f, y + s * .88f, linha);
        c.drawLine(x + s * .5f, y + s * .74f, x + s * .5f, y + s * .88f, linha);
    }

    private void entrada(Canvas c, float x, float y, float s) {
        Path caixa = new Path();
        caixa.moveTo(x + s * .44f, y + s * .14f);
        caixa.lineTo(x + s * .84f, y + s * .14f);
        caixa.quadTo(x + s * .92f, y + s * .14f, x + s * .92f, y + s * .22f);
        caixa.lineTo(x + s * .92f, y + s * .78f);
        caixa.quadTo(x + s * .92f, y + s * .86f, x + s * .84f, y + s * .86f);
        caixa.lineTo(x + s * .44f, y + s * .86f);
        c.drawPath(caixa, linha);
        c.drawLine(x + s * .08f, y + s * .5f, x + s * .66f, y + s * .5f, linha);
        Path seta = new Path();
        seta.moveTo(x + s * .48f, y + s * .32f);
        seta.lineTo(x + s * .66f, y + s * .5f);
        seta.lineTo(x + s * .48f, y + s * .68f);
        c.drawPath(seta, linha);
    }

    private void controle(Canvas c, float x, float y, float s) {
        Path corpo = new Path();
        corpo.moveTo(x + s * .3f, y + s * .3f);
        corpo.lineTo(x + s * .7f, y + s * .3f);
        corpo.cubicTo(x + s * .9f, y + s * .3f, x + s * .98f, y + s * .62f, x + s * .94f, y + s * .74f);
        corpo.cubicTo(x + s * .9f, y + s * .86f, x + s * .76f, y + s * .82f, x + s * .66f, y + s * .66f);
        corpo.lineTo(x + s * .34f, y + s * .66f);
        corpo.cubicTo(x + s * .24f, y + s * .82f, x + s * .1f, y + s * .86f, x + s * .06f, y + s * .74f);
        corpo.cubicTo(x + s * .02f, y + s * .62f, x + s * .1f, y + s * .3f, x + s * .3f, y + s * .3f);
        corpo.close();
        c.drawPath(corpo, linha);
        // direcional
        c.drawLine(x + s * .22f, y + s * .48f, x + s * .36f, y + s * .48f, linha);
        c.drawLine(x + s * .29f, y + s * .41f, x + s * .29f, y + s * .55f, linha);
        // botões
        c.drawCircle(x + s * .7f, y + s * .43f, s * .035f, cheio);
        c.drawCircle(x + s * .78f, y + s * .51f, s * .035f, cheio);
        c.drawCircle(x + s * .62f, y + s * .51f, s * .035f, cheio);
        c.drawCircle(x + s * .7f, y + s * .59f, s * .035f, cheio);
    }

    private void notebook(Canvas c, float x, float y, float s) {
        c.drawRoundRect(new RectF(x + s * .16f, y + s * .2f, x + s * .84f, y + s * .66f), s * .05f, s * .05f, linha);
        Path base = new Path();
        base.moveTo(x + s * .04f, y + s * .78f);
        base.lineTo(x + s * .96f, y + s * .78f);
        c.drawPath(base, linha);
        c.drawLine(x + s * .1f, y + s * .66f, x + s * .04f, y + s * .78f, linha);
        c.drawLine(x + s * .9f, y + s * .66f, x + s * .96f, y + s * .78f, linha);
    }

    private void engrenagem(Canvas c, float x, float y, float s) {
        float cx = x + s / 2f, cy = y + s / 2f;
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            c.drawRoundRect(new RectF(cx - s * .075f, cy - s * .43f, cx + s * .075f, cy - s * .25f), s * .03f, s * .03f, cheio);
            c.restore();
        }
        Path corpo = new Path();
        corpo.setFillType(Path.FillType.EVEN_ODD);
        corpo.addCircle(cx, cy, s * .29f, Path.Direction.CW);
        corpo.addCircle(cx, cy, s * .12f, Path.Direction.CW);
        c.drawPath(corpo, cheio);
    }

    @Override public void setAlpha(int alpha) { linha.setAlpha(alpha); cheio.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { linha.setColorFilter(cf); cheio.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
