import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import numpy as np, onnxruntime as ort, cv2
_sess=None
def sess():
    global _sess
    if _sess is None:
        o=ort.SessionOptions(); o.intra_op_num_threads=1
        _sess=ort.InferenceSession(ROOT+'/tools/lama.onnx',o,providers=['CPUExecutionProvider'])
    return _sess
def inpaint(img, mask, box, feather=6):
    """img HxWx3 uint8 RGB, mask HxW bool (True=erase). box=(x0,y0,x1,y1) context crop. Returns new img."""
    x0,y0,x1,y1=box
    crop=img[y0:y1,x0:x1].astype(np.float32)/255.0
    m=mask[y0:y1,x0:x1].astype(np.float32)
    h,w=m.shape
    ci=cv2.resize(crop,(512,512),interpolation=cv2.INTER_AREA)
    cm=cv2.resize(m,(512,512),interpolation=cv2.INTER_LINEAR); cm=(cm>0.01).astype(np.float32)
    cm=cv2.dilate(cm,np.ones((3,3),np.uint8))
    out=sess().run(None,{'image':ci.transpose(2,0,1)[None],'mask':cm[None,None]})[0][0].transpose(1,2,0)
    out=np.clip(out,0,255).astype(np.float32)
    out=cv2.resize(out,(w,h),interpolation=cv2.INTER_CUBIC)
    a=cv2.GaussianBlur(cv2.dilate(m,np.ones((feather,feather),np.uint8)),(0,0),feather/2.0)
    a=np.clip(np.maximum(a,m),0,1)[...,None]
    res=img.copy().astype(np.float32)
    res[y0:y1,x0:x1]=res[y0:y1,x0:x1]*(1-a)+out*a
    return np.clip(res,0,255).astype(np.uint8)
