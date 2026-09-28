package cn.zhiya.pact;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Abstract artwork only. Business content remains in native text views. */
final class PactArt extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hill=new Path(),trail=new Path();
    private final RectF leftRing=new RectF(),rightRing=new RectF();
    private Shader landscape,ringColor;
    private float w,h,radius,cx,cy;
    PactArt(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        super.onSizeChanged(width,height,oldWidth,oldHeight);w=width;h=height;if(w<=0||h<=0)return;
        hill.reset();hill.moveTo(0,h*.85f);hill.cubicTo(w*.25f,h*.3f,w*.35f,h*1.12f,w*.65f,h*.75f);hill.cubicTo(w*.84f,h*.51f,w*.95f,h*.58f,w,h*.66f);hill.lineTo(w,h);hill.lineTo(0,h);hill.close();
        landscape=new LinearGradient(0,h*.4f,w,h,new int[]{Color.argb(100,213,186,207),Color.argb(180,201,172,199)},null,Shader.TileMode.CLAMP);
        trail.reset();trail.moveTo(w*.5f,h);trail.cubicTo(w*.28f,h*.7f,w*.7f,h*.77f,w*.65f,h*.54f);
        radius=Math.min(h*.65f,w*.25f);cx=w*.23f;cy=h*.42f;
        ringColor=new LinearGradient(cx-radius,cy-radius,cx+radius,cy+radius,Color.rgb(183,127,154),Color.rgb(223,170,177),Shader.TileMode.CLAMP);
        leftRing.set(cx-radius*.36f,cy-radius*.59f,cx+radius*.36f,cy+radius*.59f);
        float rightX=cx+radius*.46f,rightY=cy+radius*.08f;rightRing.set(rightX-radius*.36f,rightY-radius*.59f,rightX+radius*.36f,rightY+radius*.59f);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(w<=0||h<=0)return;
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(120,212,177,194));canvas.drawCircle(w*.82f,h*.3f,h*.21f,paint);
        paint.setShader(landscape);canvas.drawPath(hill,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.argb(185,255,250,251));paint.setStrokeWidth(Math.max(1,getResources().getDisplayMetrics().density));canvas.drawPath(trail,paint);
        paint.setStrokeWidth(Math.max(2,getResources().getDisplayMetrics().density*1.8f));paint.setShader(ringColor);
        canvas.save();canvas.rotate(-29,leftRing.centerX(),leftRing.centerY());canvas.drawOval(leftRing,paint);canvas.restore();
        canvas.save();canvas.rotate(29,rightRing.centerX(),rightRing.centerY());canvas.drawOval(rightRing,paint);canvas.restore();paint.setShader(null);
    }
}
