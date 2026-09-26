import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import sys; sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2, json
from PIL import Image
from boy_cuts import CUTS, FRONT_LEGS
from inpaint import inpaint
A=ROOT+'/app/assets/rooms/'
OUT=ROOT+'/art/wip/parts/'
import os; os.makedirs(OUT,exist_ok=True)
meta={}
def poly_mask(shape,pts):
    m=np.zeros(shape[:2],np.uint8); cv2.fillPoly(m,[np.array(pts,np.int32)],1); return m.astype(bool)
for k in ['back','tq']:
    c=CUTS[k]
    im=np.array(Image.open(A+c['src']).convert('RGBA'))
    H,W=im.shape[:2]
    a=im[...,3]>8
    arm=poly_mask(im.shape,c['arm'])&a
    # arm piece
    armimg=im.copy(); armimg[~arm]=0
    ys,xs=np.where(arm); x0,y0,x1,y1=xs.min(),ys.min(),xs.max()+1,ys.max()+1
    Image.fromarray(armimg[y0:y1,x0:x1]).save(OUT+f'boy_{k}_arm.png')
    # body: remove arm, inpaint torso side
    xx=np.arange(W)[None,:].repeat(H,0)
    torso_fill=arm&poly_mask(im.shape,c['torso'])
    body=im.copy()
    rgb=body[...,:3].copy()
    # composite over mid grey for inpainting context
    alpha=body[...,3:4].astype(np.float32)/255
    ctx=(rgb*alpha+np.array([60,60,70])*(1-alpha)).astype(np.uint8)
    # widen fill a bit into arm-adjacent area to remove sleeve remnants
    fillm=cv2.dilate(torso_fill.astype(np.uint8),np.ones((5,5),np.uint8)).astype(bool)&a
    fixed=inpaint(ctx,fillm,(max(0,x0-120),max(0,y0-120),min(W,x1+60),min(H,y1+60)),feather=3)
    body[...,:3]=np.where(fillm[...,None],fixed,rgb)
    body[arm&~torso_fill,3]=0
    # hip cut
    cutpoly=list(c['hipcut'])+[(W,H),(0,H)]
    legs=poly_mask(im.shape,cutpoly)
    # soften cut edge
    body[legs,3]=0
    Image.fromarray(body).save(OUT+f'boy_{k}_body.png')
    meta[k]=dict(arm_off=[int(x0),int(y0)],pivot=c['pivot'],hand=c['hand'],hips=c['hips'],ground=c['ground'],size=[W,H])
# front
im=np.array(Image.open(A+'boy_front.png').convert('RGBA'))
H,W=im.shape[:2]
legs=poly_mask(im.shape,FRONT_LEGS)
r,g,b=[im[...,i].astype(int) for i in range(3)]
skin=(r>g+35)&(r>140)&(b<r-30)
legs&=~skin
body=im.copy(); body[legs,3]=0
n,lab,st,_=cv2.connectedComponentsWithStats((body[...,3]>8).astype(np.uint8))
k=1+np.argmax(st[1:,cv2.CC_STAT_AREA]); body[lab!=k,3]=0
Image.fromarray(body).save(OUT+'boy_front_body.png')
meta['front']=dict(hips=CUTS['front']['hips'],ground=CUTS['front']['ground'],size=[W,H])
json.dump(meta,open(OUT+'boy_meta.json','w'),indent=1)
# preview
tiles=[]
for n in ['boy_back_body','boy_back_arm','boy_tq_body','boy_tq_arm','boy_front_body']:
    p=Image.open(OUT+n+'.png'); bg=Image.new('RGBA',p.size,(120,170,120,255)); bg.alpha_composite(p); tiles.append(bg)
Hh=max(t.height for t in tiles); Ww=sum(t.width for t in tiles)+50
o=Image.new('RGBA',(Ww,Hh),(0,0,0,255)); x=0
for t in tiles: o.paste(t,(x,0)); x+=t.width+10
o.thumbnail((1600,900)); o.save(ROOT+'/art/wip/parts_preview.png')
print(json.dumps(meta))
