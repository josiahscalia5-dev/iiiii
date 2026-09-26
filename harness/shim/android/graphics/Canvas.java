package android.graphics;
import java.awt.*; import java.awt.geom.*; import java.util.*;
public class Canvas { public final Graphics2D g; final ArrayDeque<AffineTransform> st=new ArrayDeque<>(); final ArrayDeque<Shape> clips=new ArrayDeque<>(); final Bitmap target;
    public Canvas(Bitmap b){ target=b; g=b.img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);}
    public int getWidth(){return target.getWidth();} public int getHeight(){return target.getHeight();}
    public int save(){ st.push(g.getTransform()); clips.push(g.getClip()==null?new Rectangle(-100000,-100000,200000,200000):g.getClip()); return st.size(); }
    public void restore(){ g.setTransform(st.pop()); Shape c=clips.pop(); g.setClip(c); }
    public void restoreToCount(int n){ while(st.size()>=n) restore(); }
    public void translate(float x,float y){g.translate(x,y);} public void scale(float x,float y){g.scale(x,y);}
    public void scale(float x,float y,float px,float py){g.translate(px,py);g.scale(x,y);g.translate(-px,-py);}
    public void rotate(float d){g.rotate(Math.toRadians(d));} public void rotate(float d,float px,float py){g.rotate(Math.toRadians(d),px,py);}
    public void concat(Matrix m){g.transform(m.t);}
    public boolean clipRect(float l,float t,float r,float b){g.clip(new Rectangle2D.Float(l,t,r-l,b-t));return true;}
    public boolean clipRect(RectF r){return clipRect(r.left,r.top,r.right,r.bottom);}
    public boolean clipPath(Path p){g.clip(p.p);return true;}
    void apply(Paint p){ int a=Color.alpha(p.color);
        if(p.shader!=null){ g.setPaint(p.shader.awt(a)); g.setComposite(AlphaComposite.SrcOver); }
        else { g.setPaint(new java.awt.Color(Color.red(p.color),Color.green(p.color),Color.blue(p.color),a)); g.setComposite(AlphaComposite.SrcOver);} 
        int cap=p.cap==Paint.Cap.ROUND?BasicStroke.CAP_ROUND:p.cap==Paint.Cap.SQUARE?BasicStroke.CAP_SQUARE:BasicStroke.CAP_BUTT;
        int join=p.join==Paint.Join.ROUND?BasicStroke.JOIN_ROUND:p.join==Paint.Join.BEVEL?BasicStroke.JOIN_BEVEL:BasicStroke.JOIN_MITER;
        g.setStroke(new BasicStroke(Math.max(p.strokeWidth,0.0001f),cap,join)); }
    void shape(Shape s,Paint p){ apply(p); if(p.style!=Paint.Style.STROKE) g.fill(s); if(p.style!=Paint.Style.FILL) g.draw(s); }
    public void drawColor(int c){ AffineTransform t=g.getTransform(); g.setTransform(new AffineTransform()); g.setComposite(AlphaComposite.SrcOver); g.setPaint(new java.awt.Color(c,true)); g.fillRect(0,0,getWidth(),getHeight()); g.setTransform(t);}
    public void drawRect(float l,float t,float r,float b,Paint p){shape(new Rectangle2D.Float(l,t,r-l,b-t),p);}
    public void drawRect(RectF r,Paint p){drawRect(r.left,r.top,r.right,r.bottom,p);}
    public void drawRoundRect(RectF r,float rx,float ry,Paint p){shape(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),2*rx,2*ry),p);}
    public void drawOval(RectF r,Paint p){shape(new Ellipse2D.Float(r.left,r.top,r.width(),r.height()),p);}
    public void drawCircle(float x,float y,float r,Paint p){shape(new Ellipse2D.Float(x-r,y-r,2*r,2*r),p);}
    public void drawLine(float a,float b,float c,float d,Paint p){ Paint.Style s=p.style; p.style=Paint.Style.STROKE; shape(new Line2D.Float(a,b,c,d),p); p.style=s; }
    public void drawPath(Path path,Paint p){shape(path.p,p);}
    public void drawArc(RectF o,float start,float sweep,boolean center,Paint p){ shape(new Arc2D.Float(o.left,o.top,o.width(),o.height(),-start,-sweep,center?Arc2D.PIE:Arc2D.OPEN),p); }
    public void drawText(String s,float x,float y,Paint p){ java.awt.Font f=p.typeface.font.deriveFont(p.textSize); g.setFont(f); float w=p.measureText(s);
        float xx= p.align==Paint.Align.CENTER? x-w/2 : p.align==Paint.Align.RIGHT? x-w : x;
        java.awt.font.GlyphVector gv=f.createGlyphVector(g.getFontRenderContext(),s); Shape sh=gv.getOutline(xx,y); shape(sh,p); }
    static final Map<String,java.awt.image.BufferedImage> tints=new HashMap<>();
    java.awt.image.BufferedImage src(Bitmap b,Paint p){ if(p!=null&&p.filter instanceof PorterDuffColorFilter){ PorterDuffColorFilter f=(PorterDuffColorFilter)p.filter; String k=System.identityHashCode(b)+":"+f.color;
            java.awt.image.BufferedImage t=tints.get(k); if(t==null){ t=new java.awt.image.BufferedImage(b.getWidth(),b.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
              for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){int a=(b.img.getRGB(x,y)>>>24); t.setRGB(x,y,((a*Color.alpha(f.color)/255)<<24)|(f.color&0xFFFFFF));} tints.put(k,t);} return t; }
        return b.img; }
    void comp(Paint p){ int a=p==null?255:Color.alpha(p.color); g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,a/255f)); }
    public void drawBitmap(Bitmap b,float x,float y,Paint p){ comp(p); g.drawImage(src(b,p),AffineTransform.getTranslateInstance(x,y),null); g.setComposite(AlphaComposite.SrcOver);}
    public void drawBitmap(Bitmap b,Rect s,RectF d,Paint p){ comp(p); int sx=0,sy=0,sw=b.getWidth(),sh=b.getHeight(); if(s!=null){sx=s.left;sy=s.top;sw=s.width();sh=s.height();}
        AffineTransform t=new AffineTransform(); t.translate(d.left,d.top); t.scale(d.width()/sw,d.height()/sh); t.translate(-sx,-sy);
        Shape oc=g.getClip(); AffineTransform gt=g.getTransform(); g.transform(t); g.clip(new Rectangle(sx,sy,sw,sh)); g.drawImage(src(b,p),0,0,null); g.setTransform(gt); g.setClip(oc); g.setComposite(AlphaComposite.SrcOver);}
    public void drawBitmap(Bitmap b,Rect s,Rect d,Paint p){ drawBitmap(b,s,new RectF(d.left,d.top,d.right,d.bottom),p); }
    public void drawBitmap(Bitmap b,Matrix m,Paint p){ comp(p); g.drawImage(src(b,p),m.t,null); g.setComposite(AlphaComposite.SrcOver);}
}
