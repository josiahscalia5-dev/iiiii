"""v0.4 walk masks had the painted guards carved out as obstacles; v0.5 guards are live, so reopen that floor.
Reads the original masks from the reference APK, writes app/assets/rooms/room*_walk.png."""
import os, io, zipfile; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import numpy as np
from PIL import Image
apk=zipfile.ZipFile(ROOT+'/reference/TreasureRun-v0_4-rooms.apk')
OPEN={1:[(704,1164,824,1216)], 2:[(584,1240,716,1296),(1492,1828,1664,1872)]}   # world px x0,y0,x1,y1
for room,rects in OPEN.items():
    im=Image.open(io.BytesIO(apk.read('assets/rooms/room%d_walk.png'%room))); mode=im.mode
    a=np.array(im.convert('RGB'))
    for x0,y0,x1,y1 in rects: a[y0//4:y1//4,x0//4:x1//4]=255
    Image.fromarray(a).convert(mode).save(ROOT+'/app/assets/rooms/room%d_walk.png'%room)
    print(room,mode,a.shape)
