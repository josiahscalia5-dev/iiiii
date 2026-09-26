import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import numpy as np, onnxruntime as ort, cv2
_sess=None
def sess():
    global _sess
    if _sess is None:
        o=ort.SessionOptions(); o.intra_op_num_threads=4
        _sess=ort.InferenceSession(ROOT+'/tools/lama.onnx',o,providers=['CPUExecutionProvider'])
    return _sess
def _run(crop, m):
    """crop 512x512x3 float 0..1, m 512x512 float {0,1} -> 512x512x3 float 0..255"""
    # this ONNX export does not blank the hole itself: without zeroing, LaMa copies what it sees through the mask
    crop=crop*(1-m[...,None])
    out=sess().run(None,{'image':crop.transpose(2,0,1)[None].astype(np.float32),'mask':m[None,None].astype(np.float32)})[0][0].transpose(1,2,0)
    return np.clip(out,0,255).astype(np.float32)
def inpaint(img, mask, box, feather=6):
    """img HxWx3 uint8 RGB, mask HxW bool (True=erase). box=(x0,y0,x1,y1) context crop, resampled to 512. Returns new img."""
    x0,y0,x1,y1=box
    crop=img[y0:y1,x0:x1].astype(np.float32)/255.0
    m=mask[y0:y1,x0:x1].astype(np.float32)
    h,w=m.shape
    ci=cv2.resize(crop,(512,512),interpolation=cv2.INTER_AREA)
    cm=cv2.resize(m,(512,512),interpolation=cv2.INTER_LINEAR); cm=(cm>0.01).astype(np.float32)
    cm=cv2.dilate(cm,np.ones((3,3),np.uint8))
    out=_run(ci,cm)
    out=cv2.resize(out,(w,h),interpolation=cv2.INTER_CUBIC)
    a=cv2.GaussianBlur(cv2.dilate(m,np.ones((feather,feather),np.uint8)),(0,0),feather/2.0)
    a=np.clip(np.maximum(a,m),0,1)[...,None]
    res=img.copy().astype(np.float32)
    res[y0:y1,x0:x1]=res[y0:y1,x0:x1]*(1-a)+out*a
    return np.clip(res,0,255).astype(np.uint8)
def inpaint_native(img, mask, feather=3, margin=72, order=None):
    """Inpaint at 1:1 pixel scale with 512x512 windows (LaMa's native size), so no detail is lost to resampling.
    Windows are placed over the mask's bounding box; each fills the masked pixels inside its inner area
    (margin px of context on every side that isn't the image border), then later windows see those as context."""
    img=img.copy(); mask=mask.copy(); H,W=mask.shape
    if not mask.any(): return img
    ys,xs=np.where(mask); bx0,by0,bx1,by1=xs.min(),ys.min(),xs.max()+1,ys.max()+1
    step=512-2*margin
    cols=max(1,int(np.ceil((bx1-bx0)/step))); rows=max(1,int(np.ceil((by1-by0)/step)))
    wins=[]
    for r in range(rows):
        for c in range(cols):
            cx=bx0+(c+0.5)*(bx1-bx0)/cols; cy=by0+(r+0.5)*(by1-by0)/rows
            x0=int(np.clip(cx-256,0,max(0,W-512))); y0=int(np.clip(cy-256,0,max(0,H-512)))
            wins.append((x0,y0))
    if order=='bottom_up': wins.sort(key=lambda p:-p[1])
    for (x0,y0) in wins:
        x1=min(W,x0+512); y1=min(H,y0+512)
        ix0=x0 if x0==0 else x0+margin; iy0=y0 if y0==0 else y0+margin
        ix1=x1 if x1==W else x1-margin; iy1=y1 if y1==H else y1-margin
        sub=np.zeros_like(mask); sub[iy0:iy1,ix0:ix1]=mask[iy0:iy1,ix0:ix1]
        if not sub.any(): continue
        crop=np.zeros((512,512,3),np.float32); cm=np.zeros((512,512),np.float32)
        crop[:y1-y0,:x1-x0]=img[y0:y1,x0:x1].astype(np.float32)/255.0
        cm[:y1-y0,:x1-x0]=mask[y0:y1,x0:x1]   # every hole pixel in view is blanked; only the inner part is committed
        out=_run(crop,cm)[:y1-y0,:x1-x0]
        m=sub[y0:y1,x0:x1].astype(np.float32)
        a=cv2.GaussianBlur(cv2.dilate(m,np.ones((2*feather+1,2*feather+1),np.uint8)),(0,0),feather/2.0)
        a=np.clip(np.maximum(a,m),0,1)[...,None]
        reg=img[y0:y1,x0:x1].astype(np.float32)
        img[y0:y1,x0:x1]=np.clip(reg*(1-a)+out*a,0,255).astype(np.uint8)
        mask[sub]=False
    return img
