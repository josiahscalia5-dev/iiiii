import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import sys; sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2
from PIL import Image
from inpaint import inpaint
A=ROOT+'/app/assets/rooms/'
img=np.array(Image.open(ROOT+'/art/wip/room1_full_res.png').convert('RGB'))
H,W=img.shape[:2]
mask=np.zeros((H,W),np.uint8)
g=np.array(Image.open(A+'room1_occ_guard.png'))[...,3]
mask[866:866+333,683:683+161]=(g>20)
mask=cv2.dilate(mask,np.ones((9,9),np.uint8))
beam=np.array([(712,1008),(640,1012),(520,1030),(400,1055),(318,1085),(300,1140),(318,1195),(430,1185),(560,1120),(712,1058)],np.int32)
cv2.fillPoly(mask,[beam],1)
m=mask.astype(bool)
out=inpaint(img,m,(240,760,1056,1400))
Image.fromarray(out).save(ROOT+'/art/wip/room1_clean.png')
Image.fromarray(out).crop((250,800,1056,1500)).save(ROOT+'/art/wip/room1_clean_zone.png')
# pass 2: residual beam haze, higher-res crop
img=out
mask=np.zeros((H,W),np.uint8)
haze=np.array([(705,1015),(600,1040),(470,1070),(430,1120),(450,1215),(560,1225),(680,1150),(720,1060)],np.int32)
cv2.fillPoly(mask,[haze],1)
out=inpaint(img,mask.astype(bool),(300,940,860,1330))
Image.fromarray(out).save(ROOT+'/art/wip/room1_clean.png')
Image.fromarray(out).crop((250,800,1056,1500)).save(ROOT+'/art/wip/room1_clean_zone.png')
