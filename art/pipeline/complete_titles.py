import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
# The "LEVEL 1 COMPLETE!" title art has the number painted in. This erases the "1" (LaMa) and renders
# layers/complete_title_N.png for N = 2..6 with the game font. usage: python3 art/pipeline/complete_titles.py
import sys; sys.path.insert(0,ROOT+'/art/pipeline')
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from inpaint import inpaint
A=ROOT+'/app/assets/layers/'
im=Image.open(A+'complete_title.png').convert('RGBA'); a=np.array(im)
rgb=a[...,:3].copy(); alpha=a[...,3]
m=np.zeros(alpha.shape,np.uint8); m[326:424,586:652]=1
m=cv2.dilate(m,np.ones((5,5),np.uint8)).astype(bool)
fixed=inpaint(rgb,m,(470,250,780,500),feather=3)
base=np.dstack([np.where(m[...,None],fixed,rgb),alpha])
font=ImageFont.truetype(A+'../fonts/LilitaOne-Regular.ttf',98)
for n in range(2,7):
    img=Image.fromarray(base.copy())
    txt=str(n)
    layer=Image.new('RGBA',img.size,(0,0,0,0)); d=ImageDraw.Draw(layer)
    bx=d.textbbox((0,0),txt,font=font); w=bx[2]-bx[0]; h=bx[3]-bx[1]
    x=624-w/2-bx[0]; y=412-bx[3]
    sh=Image.new('RGBA',img.size,(0,0,0,0)); ImageDraw.Draw(sh).text((x+3,y+5),txt,font=font,fill=(10,5,40,200),stroke_width=9,stroke_fill=(10,5,40,200))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(2)))
    d.text((x,y),txt,font=font,fill=(250,248,255,255),stroke_width=5,stroke_fill=(14,10,52,255))
    img.alpha_composite(layer)
    img.save(A+f'complete_title_{n}.png',optimize=True)
Image.fromarray(base).crop((300,260,760,460)).save(ROOT+'/art/wip/complete_title_blank_crop.png')
print('ok')
