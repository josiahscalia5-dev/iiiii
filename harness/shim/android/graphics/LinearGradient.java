package android.graphics;
import java.awt.MultipleGradientPaint;
public class LinearGradient extends Shader { float x0,y0,x1,y1; int[] cs; float[] pos;
    public LinearGradient(float x0,float y0,float x1,float y1,int c0,int c1,TileMode m){this(x0,y0,x1,y1,new int[]{c0,c1},null,m);}
    public LinearGradient(float x0,float y0,float x1,float y1,int[] c,float[] p,TileMode m){this.x0=x0;this.y0=y0;this.x1=x1;this.y1=y1;cs=c;pos=p;}
    public java.awt.Paint awt(int alpha){
        float[] p=pos; if(p==null){p=new float[cs.length]; for(int i=0;i<cs.length;i++)p[i]=cs.length==1?0:i/(float)(cs.length-1);}
        java.awt.Color[] cc=new java.awt.Color[cs.length]; for(int i=0;i<cs.length;i++)cc[i]=c(cs[i],alpha);
        float[] pp=p.clone(); for(int i=1;i<pp.length;i++) if(pp[i]<=pp[i-1]) pp[i]=Math.min(1f,pp[i-1]+1e-4f);
        if(Math.abs(x1-x0)<1e-4&&Math.abs(y1-y0)<1e-4) return cc[cc.length-1];
        java.awt.geom.AffineTransform at= local==null? new java.awt.geom.AffineTransform(): local.t;
        return new java.awt.LinearGradientPaint(new java.awt.geom.Point2D.Float(x0,y0),new java.awt.geom.Point2D.Float(x1,y1),pp,cc,MultipleGradientPaint.CycleMethod.NO_CYCLE,MultipleGradientPaint.ColorSpaceType.SRGB,at);
    }
}
