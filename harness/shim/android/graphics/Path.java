package android.graphics;
import java.awt.geom.*;
public class Path { public enum Direction{CW,CCW} public enum FillType{WINDING,EVEN_ODD}
    public Path2D.Float p=new Path2D.Float();
    public void reset(){p.reset();} public void rewind(){p.reset();}
    public void moveTo(float x,float y){p.moveTo(x,y);} public void lineTo(float x,float y){p.lineTo(x,y);}
    public void quadTo(float a,float b,float x,float y){p.quadTo(a,b,x,y);}
    public void cubicTo(float a,float b,float c,float d,float x,float y){p.curveTo(a,b,c,d,x,y);}
    public void close(){p.closePath();}
    public void addCircle(float x,float y,float r,Direction d){p.append(new Ellipse2D.Float(x-r,y-r,2*r,2*r),false);}
    public void addOval(RectF o,Direction d){p.append(new Ellipse2D.Float(o.left,o.top,o.width(),o.height()),false);}
    public void addRect(float l,float t,float r,float b,Direction d){p.append(new Rectangle2D.Float(l,t,r-l,b-t),false);}
    public void addRoundRect(RectF o,float rx,float ry,Direction d){p.append(new RoundRectangle2D.Float(o.left,o.top,o.width(),o.height(),2*rx,2*ry),false);}
    public void setFillType(FillType f){p.setWindingRule(f==FillType.EVEN_ODD?Path2D.WIND_EVEN_ODD:Path2D.WIND_NON_ZERO);}
    public void transform(Matrix m){p.transform(m.t);}
}
