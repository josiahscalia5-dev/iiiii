package android.graphics;
import java.awt.MultipleGradientPaint;
public class RadialGradient extends Shader { float cx,cy,r; int[] cs; float[] pos;
    public RadialGradient(float cx,float cy,float r,int c0,int c1,TileMode m){this(cx,cy,r,new int[]{c0,c1},null,m);}
    public RadialGradient(float cx,float cy,float r,int[] c,float[] p,TileMode m){this.cx=cx;this.cy=cy;this.r=Math.max(r,0.01f);cs=c;pos=p;}
    public java.awt.Paint awt(int alpha){
        float[] p=pos; if(p==null){p=new float[cs.length]; for(int i=0;i<cs.length;i++)p[i]=i/(float)(cs.length-1);}
        java.awt.Color[] cc=new java.awt.Color[cs.length]; for(int i=0;i<cs.length;i++)cc[i]=c(cs[i],alpha);
        float[] pp=p.clone(); for(int i=1;i<pp.length;i++) if(pp[i]<=pp[i-1]) pp[i]=Math.min(1f,pp[i-1]+1e-4f);
        java.awt.geom.AffineTransform at= local==null? new java.awt.geom.AffineTransform(): local.t;
        return new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(cx,cy),r,new java.awt.geom.Point2D.Float(cx,cy),pp,cc,MultipleGradientPaint.CycleMethod.NO_CYCLE,MultipleGradientPaint.ColorSpaceType.SRGB,at);
    }
}
