package android.graphics;
public abstract class Shader { public enum TileMode{CLAMP,REPEAT,MIRROR}
    public Matrix local=null;
    public void setLocalMatrix(Matrix m){ local=new Matrix(); local.set(m); }
    public abstract java.awt.Paint awt(int alpha);
    static java.awt.Color c(int argb,int alpha){ int a=(Color.alpha(argb)*alpha)/255; return new java.awt.Color(Color.red(argb),Color.green(argb),Color.blue(argb),a); }
}
