package com.android.settings.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class BubbleShapeView extends View {
    private int shape = 0;
    private int color = BubblePrefs.DEFAULT_COLOR;
    private Bitmap face;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final PathEffect corner;
    private final float density;

    public BubbleShapeView(Context c) { this(c, null); }

    public BubbleShapeView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        corner = new CornerPathEffect(4 * density);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * density);
        ring.setStrokeJoin(Paint.Join.ROUND);
        face = BitmapFactory.decodeResource(getResources(), R.drawable.bubble_face);
    }

    public void setStyle(int shape, int color) {
        this.shape = shape;
        this.color = color;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (face != null && w > 0 && h > 0) {
            BitmapShader s = new BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(w / (float) face.getWidth(), h / (float) face.getHeight());
            s.setLocalMatrix(matrix);
            fill.setShader(s);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float inset = ring.getStrokeWidth() / 2f + (shape == 3 ? 2 * density : 0f);
        float l = inset, t = inset, r = w - inset, b = h - inset;
        path.reset();
        switch (shape) {
            case 1: path.addRect(l, t, r, b, Path.Direction.CW); break;
            case 2: {
                float rad = Math.min(r - l, b - t) * 0.28f;
                path.addRoundRect(l, t, r, b, rad, rad, Path.Direction.CW);
                break;
            }
            case 3:
                path.moveTo((l + r) / 2f, t);
                path.lineTo(r, b);
                path.lineTo(l, b);
                path.close();
                break;
            default: path.addOval(l, t, r, b, Path.Direction.CW);
        }
        PathEffect pe = shape == 3 ? corner : null;
        fill.setPathEffect(pe);
        tint.setPathEffect(pe);
        ring.setPathEffect(pe);
        canvas.drawPath(path, fill);
        tint.setColor((color & 0x00FFFFFF) | 0x40000000);
        canvas.drawPath(path, tint);
        ring.setColor(color);
        canvas.drawPath(path, ring);
    }
}
