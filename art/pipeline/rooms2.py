import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import sys; sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2
from PIL import Image
from inpaint import inpaint
from matte import matte
A=ROOT+'/app/assets/rooms/'; W=ROOT+'/art/wip/'; P=W+'parts/'
def fp(shape,pts): m=np.zeros(shape[:2],np.uint8); cv2.fillPoly(m,[np.array(pts,np.int32)],1); return m.astype(bool)
# ---- Room 1: diamond out of case ----
r1=np.array(Image.open(W+'room1_clean.png').convert('RGB'))
dm=fp(r1.shape,[(196,941),(276,941),(295,968),(238,1041),(179,968)])
dm=cv2.dilate(dm.astype(np.uint8),np.ones((7,7),np.uint8)).astype(bool)
r1=inpaint(r1,dm,(120,860,360,1100),feather=4)
Image.fromarray(r1).save(W+'room1_final.png')
occ=np.array(Image.open(A+'room1_occ_diamond_case.png').convert('RGBA'))
occ[...,:3]=np.where(occ[...,3:4]>0, r1[880:880+399,123:123+238], occ[...,:3])
Image.fromarray(occ).save(P+'room1_occ_diamond_case.png')
Image.fromarray(r1[860:1100,100:400]).resize((600,480)).save(W+'case_empty.png')
# ---- Room 2 ----
r2o=np.array(Image.open(W+'room2_full_res.png').convert('RGB'))
r2=r2o.copy()
def guard_matte(x,y,w,h,pad=60):
    crop=r2o[y-pad:y+h+pad,x-pad:x+w+pad]; m=matte(crop,'u2net'); return crop,m,(x-pad,y-pad)
masks=[]
for (x,y,w,h,beam) in [(556,979,171,298,[(566,1108),(470,1082),(330,1078),(215,1092),(180,1150),(225,1265),(300,1385),(365,1425),(455,1330),(566,1165)]),
                        (1469,1552,202,332,[(1648,1693),(1760,1683),(1900,1688),(1944,1700),(1944,1995),(1880,2012),(1760,1905),(1655,1765)])]:
    crop,m,(ox,oy)=guard_matte(x,y,w,h)
    gm=np.zeros(r2.shape[:2],bool); gm[oy:oy+m.shape[0],ox:ox+m.shape[1]]=m>0.35
    if x==556: gm[:1004,:]=False
    gm=cv2.dilate(gm.astype(np.uint8),np.ones((11,11),np.uint8)).astype(bool)
    bm=fp(r2.shape,beam)
    full=gm|bm
    ys,xs=np.where(full); bx0,by0,bx1,by1=xs.min()-120,ys.min()-120,xs.max()+120,ys.max()+120
    r2=inpaint(r2,full,(max(0,bx0),max(0,by0),min(2322,bx1),min(3530,by1)),feather=6)
    # second finer pass on the beam only
    ys,xs=np.where(bm); r2=inpaint(r2,bm,(max(0,xs.min()-60),max(0,ys.min()-60),min(2322,xs.max()+60),min(3530,ys.max()+60)),feather=6)
Image.fromarray(r2).save(W+'room2_final.png')
Image.fromarray(r2[850:1450,50:850]).save(W+'r2_g1_clean.png'); Image.fromarray(r2[1400:2050,1350:2322]).save(W+'r2_g2_clean.png')
# ---- guard sprites ----
crop,m,(ox,oy)=guard_matte(1469,1552,202,332)
a=np.clip((m-0.3)/0.4,0,1)
rgba=np.dstack([crop,(a*255).astype(np.uint8)])
ys,xs=np.where(a>0.05); rgba=rgba[ys.min():ys.max()+1,xs.min():xs.max()+1]
Image.fromarray(rgba).save(P+'guard_side_full.png')
Image.open(A+'room1_occ_guard.png').save(P+'guard_front_full.png')
from PIL import ImageDraw
for n in ['guard_side_full','guard_front_full']:
    im=Image.open(P+n+'.png').convert('RGBA'); s=2
    bg=Image.new('RGBA',im.size,(120,170,120,255)); bg.alpha_composite(im); bg=bg.resize((im.width*s,im.height*s)); d=ImageDraw.Draw(bg)
    for yy in range(0,im.height,10): d.line([(0,yy*s),(bg.width,yy*s)],fill=(255,255,255,60 if yy%50 else 200))
    for xx in range(0,im.width,10): d.line([(xx*s,0),(xx*s,bg.height)],fill=(255,255,255,60 if xx%50 else 200))
    for yy in range(0,im.height,50): d.text((2,yy*s+2),str(yy),fill=(255,255,0))
    for xx in range(0,im.width,50): d.text((xx*s+2,2),str(xx),fill=(255,255,0))
    bg.save(W+n+'_grid.png'); print(n,im.size)
