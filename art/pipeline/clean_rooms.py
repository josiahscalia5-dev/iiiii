"""v0.5 room cleanup: paint the guards (and their flashlight beams) out of the Room 1/2 backgrounds,
make the empty-diamond-case art, and re-cut every occluder from the cleaned backgrounds.
Inputs are the original v0.4 assets, read straight from reference/TreasureRun-v0_4-rooms.apk (app/assets holds the cleaned ones).
Outputs go to art/wip/clean/ ; tools/lama.onnx + tools/u2net.onnx needed (tools/setup_tools.sh --art)."""
import os, sys, re, json; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2, zipfile, io
from PIL import Image
from inpaint import inpaint_native, inpaint
from matte import matte
A=ROOT+'/app/assets/rooms/'; W=ROOT+'/art/wip/'; OUT=W+'clean/'
os.makedirs(OUT,exist_ok=True)
_apk=zipfile.ZipFile(ROOT+'/reference/TreasureRun-v0_4-rooms.apk')
def orig(n): return Image.open(io.BytesIO(_apk.read('assets/rooms/'+n)))
def orig_full(room):
    tiles=[np.array(orig('room%d_bg_%d.jpg'%(room,i)).convert('RGB')) for i in (0,1)]; return np.concatenate(tiles,0)
def fp(shape,pts): m=np.zeros(shape[:2],np.uint8); cv2.fillPoly(m,[np.array(pts,np.int32)],1); return m.astype(bool)
def dil(m,k): return cv2.dilate(m.astype(np.uint8),cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(k,k))).astype(bool)
def ell(shape,cx,cy,rx,ry): m=np.zeros(shape[:2],np.uint8); cv2.ellipse(m,(cx,cy),(rx,ry),0,0,360,1,-1); return m.astype(bool)
def occs(room):
    src=open(ROOT+'/app/java/com/treasurerun/game/RoomData.java').read()
    return [(m.group(1),int(m.group(2)),int(m.group(3)),int(m.group(4)),int(m.group(5)),float(m.group(6)))
            for m in re.finditer(r'new Occ\("rooms/(room%d_occ_\w+\.png)", (\d+), (\d+), (\d+), (\d+), ([\d.]+)f\)'%room,src)]
def front_of(room,base,shape):
    """pixels covered by occluders standing in front of depth `base` - those are not part of a guard"""
    m=np.zeros(shape[:2],bool)
    for n,x,y,w,h,b in occs(room):
        if b>base and 'guard' not in n:
            a=np.array(orig(n).convert('RGBA'))[...,3]>128; m[y:y+h,x:x+w]|=a
    return m
def guard_mask(img,x,y,w,h,pad=60,thr=0.3):
    crop=img[y-pad:y+h+pad,x-pad:x+w+pad]; mt=matte(crop,'u2net')
    m=np.zeros(img.shape[:2],bool); m[y-pad:y-pad+mt.shape[0],x-pad:x-pad+mt.shape[1]]=mt>thr; return m
def recut(room,bg,src):
    """re-cut the colour of every occluder the cleanup touched from the cleaned background, keeping its alpha"""
    for n,x,y,w,h,b in occs(room):
        if 'guard' in n: continue
        o=np.array(orig(n).convert('RGBA'))
        sub=bg[y:y+h,x:x+w]
        if not (sub!=src[y:y+h,x:x+w]).any(axis=2)[o[...,3]>0].any(): continue
        o[...,:3]=np.where(o[...,3:4]>0,sub,o[...,:3])
        Image.fromarray(o).save(OUT+n)
def split_bg(room,bg,ys):
    for i,y0 in enumerate(ys):
        y1=ys[i+1] if i+1<len(ys) else bg.shape[0]
        Image.fromarray(bg[y0:y1]).save(OUT+'room%d_bg_%d.jpg'%(room,i),quality=90,subsampling=2)
    Image.fromarray(bg).save(W+'room%d_clean_full.png'%room)

# ---------------- Room 1 ----------------
r1=orig_full(1)
g=np.array(orig('room1_occ_guard.png'))[...,3]>40
gm=np.zeros(r1.shape[:2],bool); gm[866:866+333,683:683+161]=g
gm=dil(gm,9)|ell(r1.shape,750,1196,70,16)
gm&=~front_of(1,1198,r1.shape)
beam=fp(r1.shape,[(712,1004),(640,1030),(560,1045),(470,1060),(432,1098),(440,1150),(520,1162),(612,1140),(655,1096),(708,1046)])
# Room 1's art is soft (upscaled), and at 1:1 LaMa leaves a ghost of the tall guard: give it 2x context instead
def win(cx,cy,S,shape): x0=int(np.clip(cx-S//2,0,shape[1]-S)); y0=int(np.clip(cy-S//2,0,shape[0]-S)); return (x0,y0,x0+S,y0+S)
c1=inpaint(r1,gm,win(763,1030,1024,r1.shape),feather=4)
c1=inpaint(c1,dil(beam,13),win(570,1090,768,r1.shape),feather=4)
# diamond out of its case (the stand stays)
dm=dil(fp(r1.shape,[(208,946),(286,944),(299,966),(254,1039),(236,1039),(189,967)]),9)
c1e=inpaint_native(c1,dm)
# without the gem the case's blue glow reads as a ghost diamond: calm it down (less saturation and brightness)
gw=np.zeros(r1.shape[:2],np.float32); cv2.ellipse(gw,(243,992),(62,58),0,0,360,1.0,-1); gw=cv2.GaussianBlur(gw,(0,0),14)[...,None]*0.55
hsv=cv2.cvtColor(c1e,cv2.COLOR_RGB2HSV).astype(np.float32); hsv[...,1]*=0.55; hsv[...,2]*=0.8
calm=cv2.cvtColor(np.clip(hsv,0,255).astype(np.uint8),cv2.COLOR_HSV2RGB).astype(np.float32)
c1e=(c1e*(1-gw)+calm*gw).astype(np.uint8)
dm=dm|(gw[...,0]>0.02)
split_bg(1,c1,[0,1256])
Image.fromarray(c1e).save(W+'room1_clean_empty_full.png')
recut(1,c1,r1)
# empty-case variants: occluder + a background patch drawn once the diamond is stolen
n,x,y,w,h,b=[o for o in occs(1) if 'diamond_case' in o[0]][0]
o=np.array(orig(n).convert('RGBA')); o[...,:3]=np.where(o[...,3:4]>0,c1e[y:y+h,x:x+w],o[...,:3])
Image.fromarray(o).save(OUT+'room1_occ_diamond_case_empty.png')
ys,xs=np.where(dil(dm,9)); px0,py0,px1,py1=xs.min(),ys.min(),xs.max()+1,ys.max()+1
patch=np.dstack([c1e[py0:py1,px0:px1],(cv2.GaussianBlur(dil(dm,5)[py0:py1,px0:px1].astype(np.float32),(0,0),2.5)*255).clip(0,255).astype(np.uint8)])
Image.fromarray(patch).save(OUT+'room1_case_empty.png')
meta={'room1_case_empty':[int(px0),int(py0),int(px1-px0),int(py1-py0)]}

# ---------------- Room 2 ----------------
r2=orig_full(2)
m2=np.zeros(r2.shape[:2],bool)
# guard 1 (walking left, beam to the left onto the post)
g1=guard_mask(r2,556,979,171,298); g1[:1004,:]=False
g1=dil(g1,15)|fp(r2.shape,[(575,1232),(705,1232),(725,1268),(690,1292),(560,1292),(538,1262)])
g1&=~front_of(2,1280,r2.shape)
b1=fp(r2.shape,[(572,1122),(470,1136),(380,1150),(318,1160),(322,1398),(345,1402),(470,1262),(575,1150)])
m2|=g1|dil(b1,15)
# guard 2 (walking right, beam to the right onto the carpet / glass case)
g2=guard_mask(r2,1469,1552,202,332)
g2=dil(g2,15)|fp(r2.shape,[(1462,1815),(1625,1815),(1650,1868),(1600,1895),(1470,1895),(1440,1860)])
g2&=~front_of(2,1864,r2.shape)
b2=fp(r2.shape,[(1662,1700),(1800,1703),(1984,1712),(1984,1962),(1952,1978),(1800,1855),(1658,1740)])
# The beam lights the carpet (red clipped at 255, the glow spills around it). The carpet pattern survives in G/B,
# so: normalise G/B locally to unlit-carpet statistics, predict R from G/B, then LaMa only the soft beam edge.
carpet=np.zeros(r2.shape[:2],bool); carpet[1693:1946,1696:1985]=True
lit=carpet&dil(b2,25)&~dil(g2,5)
ref=carpet&~dil(b2,45)&~dil(g2,9)
f=r2.astype(np.float32)
def nblur(x,m,s):
    w=cv2.GaussianBlur(m.astype(np.float32),(0,0),s)
    return cv2.GaussianBlur(x*m[...,None],(0,0),s).reshape(x.shape)/np.maximum(w,1e-3)[...,None]
inside=carpet&b2; halo=lit&~b2          # normalise the beam and its halo separately so their stats don't mix
fixed=f.copy()
# unlit carpet colours lie close to one axis (dark red <-> bright pattern): rebuild colour along it from a G/B brightness
X=f[ref]; mu=X.mean(0); v=np.linalg.svd(X-mu,full_matrices=False)[2][0]; v*=np.sign(v.sum())
sref=((X-mu)@v).std()
t=cv2.GaussianBlur(f[...,1]*v[1]+f[...,2]*v[2],(0,0),1.1)[...,None]
for reg in [inside,halo]:
    lm=nblur(t,reg,16); lsd=np.sqrt(np.maximum(nblur((t-lm)**2,reg,16),1e-3))
    sc=np.clip((t-lm)/np.maximum(lsd,sref/7.0),-2.2,2.2)*sref
    n=mu[None,None]+sc*v[None,None]
    fixed[reg]=np.clip(n[reg],0,255)
c2=r2.copy(); c2[lit]=fixed[lit].astype(np.uint8)
edge=dil(b2,25)&~cv2.erode(b2.astype(np.uint8),np.ones((17,17),np.uint8)).astype(bool)
apex=ell(r2.shape,1672,1722,34,34)
stone=np.zeros(r2.shape[:2],bool); stone[:1692,1760:]=True          # keep the stone edge above the carpet visible to LaMa
m2|=g2|((edge|apex|(dil(b2,13)&~carpet))&~stone)
c2=inpaint_native(c2,m2)
split_bg(2,c2,[0,1765])
recut(2,c2,r2)
json.dump(meta,open(OUT+'meta.json','w'))
# review sheets
def sheet(o,c,box,name):
    x0,y0,x1,y1=box; Image.fromarray(np.concatenate([o[y0:y1,x0:x1],c[y0:y1,x0:x1]],1)).save(W+'review_'+name+'.png')
sheet(r1,c1,(380,820,900,1300),'r1_guard'); sheet(r1,c1e,(110,860,380,1110),'r1_case')
sheet(r2,c2,(150,950,780,1460),'r2_g1'); sheet(r2,c2,(1400,1520,2040,2060),'r2_g2')
print('done',meta)
