package cn.zhiya.pact;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Decorative line icons; the owning native control supplies an accessible label. */
final class PactIcon extends Drawable {
    private final String key;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    PactIcon(String key,int color) {this.key=key;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    private void line(Canvas c,float... xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,paint);}
    @Override public void draw(Canvas c){
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
        switch(key){
            case "home":line(c,3,10,12,3,21,10,21,21,3,21,3,10);line(c,9,21,9,14,15,14,15,21);break;
            case "book":line(c,3,4,9,4,12,6,15,4,21,4,21,20,15,20,12,21,9,20,3,20,3,4);line(c,12,6,12,21);line(c,6,9,9,9);line(c,15,9,18,9);break;
            case "gift":c.drawRect(4,10,20,21,paint);c.drawRect(3,6,21,10,paint);line(c,12,6,12,21);Path bow=new Path();bow.moveTo(12,6);bow.cubicTo(3,7,5,-1,9,2);bow.cubicTo(11,3,12,6,12,6);bow.cubicTo(21,7,19,-1,15,2);bow.cubicTo(13,3,12,6,12,6);c.drawPath(bow,paint);break;
            case "people":c.drawCircle(9,7,3,paint);c.drawArc(new RectF(3,12,15,24),180,180,false,paint);line(c,3,18,3,21);line(c,15,18,15,21);c.drawArc(new RectF(13,4,19,10),-90,180,false,paint);c.drawArc(new RectF(13,13,23,23),-90,90,false,paint);line(c,23,18,23,21);break;
            case "plus":line(c,12,5,12,19);line(c,5,12,19,12);break;
            case "back":line(c,15,5,8,12,15,19);break;
            case "next":line(c,9,5,16,12,9,19);break;
            case "more":c.drawCircle(5,12,1,paint);c.drawCircle(12,12,1,paint);c.drawCircle(19,12,1,paint);break;
            case "refresh":c.drawArc(new RectF(4,4,20,20),35,290,false,paint);line(c,20,3,20,9,14,9);break;
            case "settings":c.drawCircle(12,12,3.5f,paint);c.drawCircle(12,12,8,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,1,12,4);c.restore();}break;
            case "check":line(c,5,12,10,17,20,6);break;
            case "heart":Path heart=new Path();heart.moveTo(12,21);heart.cubicTo(10,18,2,13,2,7);heart.cubicTo(2,1,9,1,12,6);heart.cubicTo(15,1,22,1,22,7);heart.cubicTo(22,13,14,18,12,21);c.drawPath(heart,paint);break;
            case "rings":c.save();c.rotate(-28,9,11);c.drawOval(new RectF(4,3,14,20),paint);c.restore();c.save();c.rotate(28,16,13);c.drawOval(new RectF(11,4,21,21),paint);c.restore();break;
            default:c.drawCircle(12,12,8,paint);break;
        }
        c.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
