import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import numpy as np, cv2
from PIL import Image
A=ROOT+'/app/assets/rooms/'
def seg(name, x, y, bgimg, pad=20):
    occ=np.array(Image.open(A+name).convert('RGBA'))
    h,w=occ.shape[:2]
    X0,Y0=max(0,x-pad),max(0,y-pad)
    crop=bgimg[Y0:y+h+pad, X0:x+w+pad].copy()
    ch,cw=crop.shape[:2]
    m=np.full((ch,cw),cv2.GC_BGD,np.uint8)
    a=np.zeros((ch,cw),np.uint8); a[y-Y0:y-Y0+h, x-X0:x-X0+w]=occ[...,3]
    m[a>20]=cv2.GC_PR_FGD
    hsv=cv2.cvtColor(crop,cv2.COLOR_RGB2HSV).astype(int)
    r,g,b=[crop[...,i].astype(int) for i in range(3)]
    blue=(b>r+25)&(a>20)
    dark=(np.maximum(np.maximum(r,g),b)<60)&(a>20)
    m[blue|dark]=cv2.GC_FGD
    # red/tan background is probable bg
    redtan=(r>b+60)&(a>20)&~blue
    m[redtan]=cv2.GC_PR_BGD
    bgm=np.zeros((1,65),np.float64); fgm=np.zeros((1,65),np.float64)
    cv2.grabCut(cv2.cvtColor(crop,cv2.COLOR_RGB2BGR),m,None,bgm,fgm,6,cv2.GC_INIT_WITH_MASK)
    fg=((m==cv2.GC_FGD)|(m==cv2.GC_PR_FGD)).astype(np.uint8)
    # keep largest component
    n,lab,st,_=cv2.connectedComponentsWithStats(fg)
    if n>1:
        k=1+np.argmax(st[1:,cv2.CC_STAT_AREA]); fg=(lab==k).astype(np.uint8)
    fg=cv2.morphologyEx(fg,cv2.MORPH_CLOSE,np.ones((5,5),np.uint8))
    alpha=cv2.GaussianBlur(fg.astype(np.float32),(0,0),0.8)
    rgba=np.dstack([crop,(np.clip(alpha,0,1)*255).astype(np.uint8)])
    return rgba,(X0,Y0)
if __name__=='__main__':
    bg=np.array(Image.open(ROOT+'/art/wip/room2_full_res.png').convert('RGB'))
    for n,x,y in [('room2_occ_guard1.png',556,979),('room2_occ_guard2.png',1469,1552)]:
        rgba,o=seg(n,x,y,bg)
        Image.fromarray(rgba).save(ROOT+'/art/wip/seg_'+n)
        v=Image.new('RGBA',(rgba.shape[1],rgba.shape[0]),(0,200,0,255)); v.alpha_composite(Image.fromarray(rgba))
        v.resize((v.width*2,v.height*2)).save(ROOT+'/art/wip/segv_'+n)
        print(n,o,rgba.shape)
