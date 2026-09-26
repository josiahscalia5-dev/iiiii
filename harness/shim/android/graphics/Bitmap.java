package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
    public enum Config { ARGB_8888, RGB_565 }
    public final BufferedImage img;
    public Bitmap(BufferedImage i){ if(i.getType()!=BufferedImage.TYPE_INT_ARGB){BufferedImage c=new BufferedImage(i.getWidth(),i.getHeight(),BufferedImage.TYPE_INT_ARGB); c.getGraphics().drawImage(i,0,0,null); i=c;} img=i; }
    public static Bitmap createBitmap(int w,int h,Config c){ return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB)); }
    public int getWidth(){return img.getWidth();}
    public int getHeight(){return img.getHeight();}
    public void getPixels(int[] px,int off,int stride,int x,int y,int w,int h){ img.getRGB(x,y,w,h,px,off,stride); }
    public int getPixel(int x,int y){ return img.getRGB(x,y); }
    public void recycle(){}
}
