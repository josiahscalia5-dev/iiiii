package android.graphics;
public class Typeface { public final java.awt.Font font;
    public Typeface(java.awt.Font f){font=f;}
    public static final Typeface DEFAULT_BOLD=new Typeface(new java.awt.Font("SansSerif",java.awt.Font.BOLD,12));
    public static final Typeface DEFAULT=new Typeface(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,12));
    public static Typeface fromFile(String p){ try{ return new Typeface(java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,new java.io.File(p))); }catch(Exception e){ return DEFAULT_BOLD; } }
}
