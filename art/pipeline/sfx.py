import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
# Synthesizes the game's sound effects into app/assets/sfx/*.wav (22.05 kHz mono 16-bit). usage: python3 art/pipeline/sfx.py
import numpy as np, wave
SR=22050
def t(d): return np.arange(int(SR*d))/SR
def env(n,a=0.005,d=0.2,curve=4.0):
    x=np.arange(n)/SR; e=np.minimum(1,x/max(a,1e-4))*np.exp(-np.maximum(0,x-a)*curve/max(d,1e-4)); return e
def bell(f,d,amp=1.0,decay=None):
    x=t(d); decay=decay or d
    s=np.sin(2*np.pi*f*x)+0.45*np.sin(2*np.pi*f*2.01*x)*np.exp(-x*6)+0.2*np.sin(2*np.pi*f*3.02*x)*np.exp(-x*10)
    return amp*s*env(len(x),0.003,decay,3.5)
def place(out,s,at):
    i=int(at*SR); n=min(len(s),len(out)-i); out[i:i+n]+=s[:n]; return out
def saw(f,x): return 2*((f*x)%1)-1
def lowpass(s,k=0.15):
    y=np.zeros_like(s); a=0
    for i,v in enumerate(s): a+=k*(v-a); y[i]=a
    return y
def write(name,s,gain=0.8):
    s=s/ max(1e-6,np.abs(s).max())*gain
    fade=min(len(s),int(0.01*SR)); s[-fade:]*=np.linspace(1,0,fade)
    with wave.open(ROOT+'/app/assets/sfx/'+name,'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((s*32767).astype(np.int16).tobytes())
os.makedirs(ROOT+'/app/assets/sfx',exist_ok=True)
N=lambda d: np.zeros(int(SR*d))
# coin: two bright blips
o=N(0.32); place(o,bell(1976,0.12,0.8,0.08),0); place(o,bell(2637,0.28,1.0,0.22),0.06); write('coin.wav',o,0.55)
# gem: sparkly arpeggio
o=N(0.55)
for i,f in enumerate([1319,1661,1976,2637]): place(o,bell(f,0.4,0.9,0.3),i*0.05)
write('gem.wav',o,0.55)
# diamond: magical rising chime with shimmer tail
o=N(1.3)
for i,f in enumerate([1047,1319,1568,2093,2637]): place(o,bell(f,0.9,1.0,0.7),i*0.07)
x=t(1.0); sh=np.random.RandomState(1).randn(len(x))*np.exp(-x*3)*0.08; place(o,lowpass(sh,0.5)*np.sin(2*np.pi*3000*x),0.3)
write('diamond.wav',o,0.7)
# hmm: soft questioning two-note woodblock
o=N(0.4)
for i,f in enumerate([587,880]):
    x=t(0.18); s=np.sin(2*np.pi*f*x)*env(len(x),0.004,0.08,4)+0.3*np.sin(2*np.pi*f*1.5*x)*env(len(x),0.002,0.04,4)
    place(o,s,i*0.13)
write('hmm.wav',o,0.45)
# alert: two brassy stabs
o=N(0.55)
for i,f in enumerate([622,932]):
    x=t(0.22); s=(saw(f,x)+0.6*saw(f*1.505,x)+0.4*saw(f*0.5,x))*env(len(x),0.004,0.16,3)
    place(o,lowpass(s,0.35),i*0.12)
write('alert.wav',o,0.6)
# caught: sad descending wah-wah
o=N(1.4)
for i,(f0,f1) in enumerate([(392,370),(370,349),(349,330),(330,262)]):
    d=0.26 if i<3 else 0.6; x=t(d); f=np.linspace(f0,f1,len(x))*(1+0.012*np.sin(2*np.pi*6*x)*(i==3))
    ph=np.cumsum(2*np.pi*f/SR); s=(np.sin(ph)+0.5*np.sin(2*ph)+0.3*np.sin(3*ph))*env(len(x),0.02,d*0.8,1.6)
    place(o,lowpass(s,0.25),i*0.28)
write('caught.wav',o,0.6)
# unlock: latch click + chime
o=N(0.8)
x=t(0.04); place(o,np.random.RandomState(2).randn(len(x))*env(len(x),0.001,0.01,4)*0.8,0)
x=t(0.03); place(o,np.random.RandomState(3).randn(len(x))*env(len(x),0.001,0.01,4)*0.6,0.07)
for i,f in enumerate([1568,2093,2637]): place(o,bell(f,0.6,0.8,0.45),0.12+i*0.06)
write('unlock.wav',o,0.6)
# escape: whoosh + fanfare
o=N(1.8)
x=t(0.6); nz=np.random.RandomState(4).randn(len(x)); place(o,lowpass(nz,0.08)*np.sin(np.pi*x/0.6)*0.6,0)
for i,f in enumerate([523,659,784,1047]):
    x=t(0.5 if i<3 else 1.0); s=(saw(f,x)*0.5+np.sin(2*np.pi*f*x))*env(len(x),0.01,0.35 if i<3 else 0.8,2.5)
    place(o,lowpass(s,0.3),0.35+i*0.12)
place(o,bell(2093,1.0,0.5,0.8),0.75)
write('escape.wav',o,0.65)
# step: soft thump
x=t(0.09); s=np.sin(2*np.pi*np.linspace(140,70,len(x))*x)*env(len(x),0.003,0.04,4)+lowpass(np.random.RandomState(5).randn(len(x)),0.2)*env(len(x),0.001,0.02,4)*0.4
write('step.wav',s,0.5)
# tap: soft tick
x=t(0.06); write('tap.wav',np.sin(2*np.pi*1400*x)*env(len(x),0.001,0.02,4),0.3)
print(sorted(os.listdir(ROOT+'/app/assets/sfx')))
