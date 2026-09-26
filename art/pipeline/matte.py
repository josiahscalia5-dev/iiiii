import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
import numpy as np, onnxruntime as ort, cv2
_s={}
def S(m):
    if m not in _s:
        o=ort.SessionOptions(); o.intra_op_num_threads=1
        _s[m]=ort.InferenceSession(ROOT+'/tools/%s.onnx'%m,o,providers=['CPUExecutionProvider'])
    return _s[m]
def matte(rgb, model='isnet-anime'):
    h,w=rgb.shape[:2]
    if model=='isnet-anime':
        x=cv2.resize(rgb,(1024,1024),interpolation=cv2.INTER_AREA).astype(np.float32)/255.0
        x=(x-0.5)/1.0
        out=S(model).run(None,{'img':x.transpose(2,0,1)[None]})[0][0,0]
    else:
        x=cv2.resize(rgb,(320,320),interpolation=cv2.INTER_AREA).astype(np.float32)/255.0
        x=(x-np.array([0.485,0.456,0.406]))/np.array([0.229,0.224,0.225])
        out=S(model).run(None,{'input.1':x.transpose(2,0,1)[None].astype(np.float32)})[0][0,0]
    out=(out-out.min())/(out.max()-out.min()+1e-6)
    return cv2.resize(out,(w,h),interpolation=cv2.INTER_LINEAR)
