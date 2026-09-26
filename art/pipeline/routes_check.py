import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
# Draws every level's patrol routes over its room (build/harness/routes_L*.png) and checks waypoints are standable
# and connected on the walk mask. usage: python3 art/pipeline/routes_check.py
import re, numpy as np, collections
from PIL import Image, ImageDraw
src=open(ROOT+'/app/java/com/treasurerun/game/Level.java').read()
rooms={0:dict(walk='room1_walk.png',bg=['room1_bg_0.jpg','room1_bg_1.jpg'],feetR=40,h=790,yref=2100,spawn=(505,2150)),
       1:dict(walk='room2_walk.png',bg=['room2_bg_0.jpg','room2_bg_1.jpg'],feetR=22,h=-2860,yref=2840,spawn=(936,2680))}
levels=[]
for m in re.finditer(r'new Level\((\d+), (\d), (.*?)\n        \}?\),?\n(?=        //|    \};)',src,re.S): pass
body=src[src.index('static final Level[] ALL'):]
for blk in re.split(r'\n        new Level\(',body)[1:]:
    num,room=map(int,re.match(r'(\d+), (\d)',blk).groups())
    guards=[]
    for g in re.findall(r'new Wp\[\]\{(.*?)\}\)?,?\n',blk):
        pts=[tuple(map(float,p[1].split(',')[:2])) for p in re.findall(r'(stop|wp)\(([^)]*)\)',g)]
        guards.append(pts)
    levels.append((num,room,guards))
os.makedirs(ROOT+'/build/harness',exist_ok=True)
ok=True
for num,room,guards in levels:
    R=rooms[room]; w=np.array(Image.open(ROOT+'/app/assets/rooms/'+R['walk']).convert('L'))>127
    def depth(y): return (y-R['h'])/(R['yref']-R['h'])
    def stand(x,y):
        m=R['feetR']*max(0.15,depth(y)); 
        for (px,py) in [(x,y),(x-m,y),(x+m,y),(x,y-m*.6),(x,y+m*.6)]:
            i,j=int(px/4),int(py/4)
            if not(0<=i<w.shape[1] and 0<=j<w.shape[0] and w[j,i]): return False
        return True
    # connectivity via BFS on mask from spawn
    lab=np.zeros(w.shape,np.int32); sx,sy=int(R['spawn'][0]/4),int(R['spawn'][1]/4)
    q=collections.deque([(sx,sy)]); lab[sy,sx]=1
    while q:
        x,y=q.popleft()
        for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
            nx,ny=x+dx,y+dy
            if 0<=nx<w.shape[1] and 0<=ny<w.shape[0] and w[ny,nx] and not lab[ny,nx]: lab[ny,nx]=1; q.append((nx,ny))
    ims=[Image.open(ROOT+'/app/assets/rooms/'+b).convert('RGB') for b in R['bg']]
    bg=Image.new('RGB',(ims[0].width,sum(i.height for i in ims))); y=0
    for i in ims: bg.paste(i,(0,y)); y+=i.height
    m=Image.fromarray((w*255).astype(np.uint8)).resize(bg.size,Image.NEAREST)
    bg=Image.composite(Image.blend(bg,Image.new('RGB',bg.size,(0,255,80)),0.25),bg,m)
    d=ImageDraw.Draw(bg); cols=[(255,60,60),(60,160,255),(255,220,0),(255,0,255)]
    for gi,pts in enumerate(guards):
        for k,(x,y) in enumerate(pts):
            s=stand(x,y); conn=lab[int(y/4),int(x/4)]>0
            if not(s and conn): ok=False; print(f'L{num} guard{gi} wp{k} ({x:.0f},{y:.0f}) stand={s} reachable={conn}')
            nx,ny=pts[(k+1)%len(pts)]; d.line([(x,y),(nx,ny)],fill=cols[gi],width=8)
            d.ellipse([x-14,y-14,x+14,y+14],fill=cols[gi] if s and conn else (0,0,0),outline=(255,255,255),width=3)
    sc=1000/bg.height; bg.resize((int(bg.width*sc),1000)).save(ROOT+f'/build/harness/routes_L{num}.png')
print('all waypoints OK' if ok else 'PROBLEMS above')
