package android.graphics;
public class Paint { public static final int ANTI_ALIAS_FLAG=1, FILTER_BITMAP_FLAG=2, DITHER_FLAG=4;
    public enum Style{FILL,STROKE,FILL_AND_STROKE} public enum Align{LEFT,CENTER,RIGHT} public enum Join{MITER,ROUND,BEVEL} public enum Cap{BUTT,ROUND,SQUARE}
    public int color=0xFF000000; public Style style=Style.FILL; public float strokeWidth=0; public Shader shader; public ColorFilter filter;
    public float textSize=12; public Align align=Align.LEFT; public Typeface typeface=Typeface.DEFAULT; public Join join=Join.MITER; public Cap cap=Cap.BUTT; public boolean aa;
    public Paint(){} public Paint(int flags){aa=(flags&1)!=0;}
    public void setColor(int c){color=c;} public int getColor(){return color;}
    public void setAlpha(int a){color=(color&0x00FFFFFF)|((a&255)<<24);} public int getAlpha(){return Color.alpha(color);}
    public void setStyle(Style s){style=s;} public void setStrokeWidth(float w){strokeWidth=w;}
    public Shader setShader(Shader s){shader=s;return s;} public ColorFilter setColorFilter(ColorFilter f){filter=f;return f;}
    public void setTextSize(float s){textSize=s;} public void setTextAlign(Align a){align=a;}
    public Typeface setTypeface(Typeface t){typeface=t;return t;} public void setStrokeJoin(Join j){join=j;} public void setStrokeCap(Cap c){cap=c;}
    public void setAntiAlias(boolean b){aa=b;} public void setFilterBitmap(boolean b){} public void setDither(boolean b){}
    public float measureText(String s){ java.awt.Font f=typeface.font.deriveFont(textSize); java.awt.font.FontRenderContext frc=new java.awt.font.FontRenderContext(null,true,true); return (float)f.getStringBounds(s,frc).getWidth(); }
}
