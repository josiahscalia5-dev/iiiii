import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
# Builds the v0.5 gameplay assets into app/assets from art/wip (run: python3 art/pipeline/v05_assets.py [--rooms]).
#   rig/     boy + guard body sprites with legs removed (legs are drawn procedurally in Rig.java), boy arm pieces
#   rooms/   backgrounds with the painted guards/beams removed and the Room 1 diamond out of its case (--rooms, needs LaMa)
# Originals stay recoverable from reference/TreasureRun-v0_4-rooms.apk.
import sys; sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2, json
from PIL import Image
A=ROOT+'/app/assets/'; W=ROOT+'/art/wip/'; P=W+'parts/'
os.makedirs(A+'rig',exist_ok=True)
def fp(shape,pts): m=np.zeros(shape[:2],np.uint8); cv2.fillPoly(m,[np.array(pts,np.int32)],1); return m.astype(bool)
def load(p): return np.array(Image.open(p).convert('RGBA'))
def save(a,p): Image.fromarray(a).save(p,optimize=True)
def trim(a,pad=2):
    ys,xs=np.where(a[...,3]>4); x0,y0=max(0,xs.min()-pad),max(0,ys.min()-pad); return a[y0:ys.max()+1+pad,x0:xs.max()+1+pad],(int(x0),int(y0))
def cut_below(a,poly_top,feather=2.0):
    """erase everything below the polyline (list of (x,y), left->right), with a soft edge"""
    H,Wd=a.shape[:2]; m=np.zeros((H,Wd),np.float32)
    cv2.fillPoly(m,[np.array(list(poly_top)+[(Wd,H),(0,H)],np.int32)],1.0)
    m=cv2.GaussianBlur(m,(0,0),feather) if feather>0 else m
    a=a.copy(); a[...,3]=(a[...,3]*(1-np.clip(m*1.6,0,1))).astype(np.uint8); return a
meta={}
# ---------- boy ----------
bm=json.load(open(P+'boy_meta.json'))
for v in ['back','tq','front']:
    a=load(P+f'boy_{v}_body.png')
    if v=='tq': a[495:580,386:460,3]=0          # sleeve spike left by the arm cut
    if v=='back': a[398:468,268:320,3]=0
    save(a,A+f'rig/boy_{v}_body.png')
    if v!='front': save(load(P+f'boy_{v}_arm.png'),A+f'rig/boy_{v}_arm.png')
# ---------- guards ----------
G={ # name: (source, cut polyline under the belt, hips L/R, ground y, flashlight lens)
 'front':('guard_front_full.png',[(0,203),(118,206),(121,217),(161,217)],[(57,200),(104,200)],330,(9,158)),
 'tqf':  ('guard_tqf_full.png',  [(0,178),(62,176),(118,183),(136,184),(138,203),(169,203)],[(84,178),(126,183)],262,(24,124)),
 'tqb':  ('guard_side_full.png', [(0,214),(46,212),(62,200),(150,210),(152,216),(194,216)],[(76,202),(122,208)],305,(178,165)),
}
for k,(src,poly,hips,ground,lamp) in G.items():
    a=load(P+src)
    if k=='front': a[:120,148:,3]=0     # background sliver right of the head
    a=cut_below(a,poly)
    save(a,A+f'rig/guard_{k}.png')
    meta['guard_'+k]=dict(size=[a.shape[1],a.shape[0]],hips=hips,ground=ground,lamp=lamp)
json.dump(meta,open(P+'rig_meta.json','w'),indent=1)
print(json.dumps(meta))
if '--rooms' not in sys.argv: sys.exit(0)
# ---------- rooms ----------
from inpaint import inpaint
def split_jpg(img,cuts,prefix):
    for i,(y0,y1) in enumerate(cuts): Image.fromarray(img[y0:y1]).save(A+f'rooms/{prefix}_bg_{i}.jpg',quality=90,optimize=True)
r1=np.array(Image.open(W+'room1_final.png').convert('RGB'))
split_jpg(r1,[(0,1256),(1256,2511)],'room1')
save(load(P+'room1_occ_diamond_case.png'),A+'rooms/room1_occ_diamond_case.png')
r2=np.array(Image.open(W+'room2_final.png').convert('RGB'))
# shadows the painted guards left on the floor
for poly,box in [([(515,1268),(560,1253),(620,1246),(665,1237),(708,1233),(710,1256),(660,1270),(600,1284),(560,1294),(515,1296)],(420,1140,820,1370)),
                 ([(1463,1836),(1500,1834),(1560,1838),(1614,1850),(1604,1874),(1560,1881),(1500,1881),(1476,1860)],(1380,1760,1700,1950))]:
    m=fp(r2.shape,poly); m=cv2.dilate(m.astype(np.uint8),np.ones((9,9),np.uint8)).astype(bool)
    r2=inpaint(r2,m,box,feather=5)
Image.fromarray(r2).save(W+'room2_final2.png')
split_jpg(r2,[(0,1765),(1765,3530)],'room2')
for f in ['room1_occ_guard.png','room2_occ_guard1.png','room2_occ_guard2.png']:
    if os.path.exists(A+'rooms/'+f): os.remove(A+'rooms/'+f)
# the v0.4 walk masks treated the painted guards' feet as obstacles; the guards are live now, so open those holes
for n,rects in [('room1',[(700,1160,820,1208),(700,1208,832,1212)]),('room2',[(580,1240,708,1284)]),('room2',[(1492,1824,1656,1868)])]:
    wm=np.array(Image.open(A+f'rooms/{n}_walk.png').convert('L'))
    for (x0,y0,x1,y1) in rects: wm[y0//4:y1//4,x0//4:x1//4]=255
    Image.fromarray(wm).save(A+f'rooms/{n}_walk.png')
print('rooms written')
