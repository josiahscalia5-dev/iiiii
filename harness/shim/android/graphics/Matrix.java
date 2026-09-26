package android.graphics;
import java.awt.geom.AffineTransform;
public class Matrix { public AffineTransform t=new AffineTransform();
    public void reset(){t.setToIdentity();}
    public void set(Matrix m){t.setTransform(m.t);}
    public void setTranslate(float x,float y){t.setToTranslation(x,y);}
    public void setScale(float x,float y){t.setToScale(x,y);}
    public void setRotate(float d){t.setToRotation(Math.toRadians(d));}
    public void setRotate(float d,float px,float py){t.setToRotation(Math.toRadians(d),px,py);}
    public boolean postTranslate(float x,float y){AffineTransform a=AffineTransform.getTranslateInstance(x,y); a.concatenate(t); t=a; return true;}
    public boolean postScale(float x,float y){AffineTransform a=AffineTransform.getScaleInstance(x,y); a.concatenate(t); t=a; return true;}
    public boolean postScale(float x,float y,float px,float py){postTranslate(-px,-py);postScale(x,y);postTranslate(px,py);return true;}
    public boolean postRotate(float d){AffineTransform a=AffineTransform.getRotateInstance(Math.toRadians(d)); a.concatenate(t); t=a; return true;}
    public boolean postRotate(float d,float px,float py){AffineTransform a=AffineTransform.getRotateInstance(Math.toRadians(d),px,py); a.concatenate(t); t=a; return true;}
    public boolean preTranslate(float x,float y){t.translate(x,y);return true;}
    public boolean preScale(float x,float y){t.scale(x,y);return true;}
    public boolean preRotate(float d){t.rotate(Math.toRadians(d));return true;}
    public boolean postConcat(Matrix m){AffineTransform a=new AffineTransform(m.t); a.concatenate(t); t=a; return true;}
    public boolean preConcat(Matrix m){t.concatenate(m.t);return true;}
    public void setValues(float[] v){t.setTransform(v[0],v[3],v[1],v[4],v[2],v[5]);}
    public void mapPoints(float[] p){ double[] d=new double[p.length]; for(int i=0;i<p.length;i++)d[i]=p[i]; t.transform(d,0,d,0,p.length/2); for(int i=0;i<p.length;i++)p[i]=(float)d[i]; }
    public boolean invert(Matrix out){ try{ out.t=t.createInverse(); return true;}catch(Exception e){return false;} }
}
