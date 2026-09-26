"""Boy body/arm parts for the v0.5 walk rig, from the original v0.4 poses in the reference APK.
Writes app/assets/rooms/boy_{back,tq,front}_body.png, boy_{back,tq}_arm.png and art/wip/parts/boy_meta.json.
The arm offsets / pivots printed here are what Boy.java's RIGS use."""
import os, sys, io, zipfile, json; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2
from PIL import Image
from boy_cuts import CUTS, FRONT_LEGS
from inpaint import inpaint
apk=zipfile.ZipFile(ROOT+'/reference/TreasureRun-v0_4-rooms.apk')
def orig(n): return np.array(Image.open(io.BytesIO(apk.read('assets/rooms/'+n))).convert('RGBA'))
OUT=ROOT+'/app/assets/rooms/'; WIP=ROOT+'/art/wip/parts/'
os.makedirs(WIP,exist_ok=True)
def soft_poly(shape,pts,blur=1.2):
    """anti-aliased polygon coverage 0..1 (4x supersampled)"""
    H,W=shape[:2]; m=np.zeros((H*4,W*4),np.uint8)
    cv2.fillPoly(m,[(np.array(pts,np.float32)*4).astype(np.int32)],255)
    m=cv2.resize(m,(W,H),interpolation=cv2.INTER_AREA).astype(np.float32)/255
    return cv2.GaussianBlur(m,(0,0),blur) if blur>0 else m
def biggest(alpha):
    n,lab,st,_=cv2.connectedComponentsWithStats((alpha>20).astype(np.uint8))
    if n<=2: return alpha
    k=1+np.argmax(st[1:,cv2.CC_STAT_AREA]); keep=cv2.dilate((lab==k).astype(np.uint8),np.ones((3,3),np.uint8))
    return (alpha*keep).astype(np.uint8)
meta={}
for k in ['back','tq']:
    c=CUTS[k]; im=orig(c['src']); H,W=im.shape[:2]
    a=im[...,3].astype(np.float32)/255
    armc=soft_poly(im.shape,c['arm'],0.8)            # arm coverage
    keep=soft_poly(im.shape,c['torso'],0.8)          # torso side contour
    # ---- arm sprite: original pixels, alpha limited by the arm polygon
    arm=im.copy(); arm[...,3]=(a*armc*255).astype(np.uint8)
    ys,xs=np.where(arm[...,3]>4); x0,y0,x1,y1=xs.min(),ys.min(),xs.max()+1,ys.max()+1
    Image.fromarray(arm[y0:y1,x0:x1]).save(OUT+f'boy_{k}_arm.png')
    # ---- body: inpaint the torso that the arm covered, drop the rest of the arm
    hard_arm=(armc>0.02)&(a>0.03)
    fill=hard_arm&(keep>0.02)
    rgb=im[...,:3].copy()
    ctx=(rgb*a[...,None]+np.array([40,40,60])*(1-a[...,None])).astype(np.uint8)
    fillm=cv2.dilate(fill.astype(np.uint8),np.ones((5,5),np.uint8)).astype(bool)&(a>0.03)
    bx0,by0=max(0,x0-110),max(0,y0-110); bx1,by1=min(W,x1+40),min(H,y1+60)
    fixed=inpaint(ctx,fillm,(bx0,by0,bx1,by1),feather=3)
    body=im.copy(); body[...,:3]=np.where(fillm[...,None],fixed,rgb)
    # alpha: inside the arm region only the torso keeps coverage (soft contour); the hem spike etc. go away
    alpha=a.copy()
    armzone=cv2.GaussianBlur(hard_arm.astype(np.float32),(0,0),1.0)
    alpha=alpha*(1-armzone)+np.minimum(np.maximum(a,fillm*1.0),keep)*armzone
    # darken the new torso edge a touch so it reads as a turning surface
    edge=np.clip((keep-0.5)*2,0,1); rim=np.clip(1-cv2.GaussianBlur(edge,(0,0),3)*1.0,0,1)*fill
    body[...,:3]=(body[...,:3]*(1-0.35*rim[...,None])).astype(np.uint8)
    # hip cut (legs are procedural)
    hip=soft_poly(im.shape,list(c['hipcut'])+[(W,H),(0,H)],1.2)
    alpha=alpha*(1-hip)
    body[...,3]=biggest((np.clip(alpha,0,1)*255).astype(np.uint8))
    Image.fromarray(body).save(OUT+f'boy_{k}_body.png')
    meta[k]=dict(arm_off=[int(x0),int(y0)],pivot=c['pivot'],hand=c['hand'],hips=c['hips'],ground=c['ground'],size=[W,H])
# ---- front: remove the painted legs (keep the hands)
im=orig('boy_front.png'); H,W=im.shape[:2]
legs=soft_poly(im.shape,FRONT_LEGS,1.0)
r,g,b=[im[...,i].astype(int) for i in range(3)]
skin=((r>g+35)&(r>140)&(b<r-30)).astype(np.float32)
skin=cv2.dilate(skin,np.ones((3,3),np.uint8))
legs=legs*(1-skin)
body=im.copy(); body[...,3]=biggest((im[...,3]*(1-legs)).astype(np.uint8))
Image.fromarray(body).save(OUT+'boy_front_body.png')
meta['front']=dict(hips=CUTS['front']['hips'],ground=CUTS['front']['ground'],size=[W,H])
json.dump(meta,open(WIP+'boy_meta.json','w'),indent=1)
print(json.dumps(meta))
