"""Guard sprites for v0.5, cut from the original v0.4 backgrounds (reference APK) with a U2Net matte:
guard_side.png  = Room 2 guard 2 (walking right, calm)      guard_front.png = Room 1 guard (shocked "!" face)
Writes app/assets/rooms/guard_*.png and prints anchor/flashlight points (sprite px) for RoomData/Guard.java."""
import os, sys, io, zipfile; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2
from PIL import Image
from matte import matte
apk=zipfile.ZipFile(ROOT+'/reference/TreasureRun-v0_4-rooms.apk')
def orig(n): return Image.open(io.BytesIO(apk.read('assets/rooms/'+n)))
def full(room): return np.concatenate([np.array(orig('room%d_bg_%d.jpg'%(room,i)).convert('RGB')) for i in (0,1)],0)
def cut(img,x,y,w,h,pad,lo,hi,keep=None):
    crop=img[y-pad:y+h+pad,x-pad:x+w+pad]; m=matte(crop,'u2net')
    a=np.clip((m-lo)/(hi-lo),0,1)
    if keep is not None: a*=keep(crop.shape)
    # largest blob only
    n,lab,st,_=cv2.connectedComponentsWithStats((a>0.3).astype(np.uint8))
    k=1+np.argmax(st[1:,cv2.CC_STAT_AREA]); a*=cv2.dilate((lab==k).astype(np.uint8),np.ones((5,5),np.uint8))
    rgba=np.dstack([crop,(a*255).astype(np.uint8)])
    ys,xs=np.where(a>0.05); return rgba[ys.min():ys.max()+1,xs.min():xs.max()+1],(x-pad+xs.min(),y-pad+ys.min())
OUT=ROOT+'/app/assets/rooms/'
side,o1=cut(full(2),1469,1552,202,332,60,0.3,0.7)
Image.fromarray(side).save(OUT+'guard_side.png')
r1=full(1)
def no_beam(shape):
    # the flashlight beam leaves the lens to the lower left: keep the matte off it
    k=np.ones(shape[:2],np.float32); cv2.fillPoly(k,[np.array([(0,205),(68,212),(68,shape[0]),(0,shape[0])],np.int32)],0); return k
front,o2=cut(r1,683,866,161,333,70,0.3,0.7,no_beam)
Image.fromarray(front).save(OUT+'guard_front.png')
print('side',side.shape,o1,'front',front.shape,o2)
for n,im in [('side',side),('front',front)]:
    bg=Image.new('RGBA',(im.shape[1],im.shape[0]),(120,170,120,255)); bg.alpha_composite(Image.fromarray(im))
    bg.resize((im.shape[1]*2,im.shape[0]*2)).save(ROOT+'/art/wip/guard_%s_check.png'%n)
