import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
# Cut definitions for boy views (sprite pixel coords)
CUTS = {
 'back': dict(src='boy_back.png',
    arm=[(248,292),(292,282),(334,318),(372,405),(372,488),(316,498),(280,442),(248,392)],
    torso_x=262, torso=[(0,0),(262,0),(262,300),(258,360),(256,420),(260,470),(268,505),(0,505)],          # arm pixels left of this are torso (inpaint), right -> transparent
    pivot=(272,318), hand=(338,452),
    hipcut=[(0,500),(90,500),(130,512),(170,516),(215,512),(262,502),(371,500)],  # body keeps above this polyline
    hips=((128,522),(212,522)), ground=758),
 'tq': dict(src='boy_tq.png',
    arm=[(352,372),(410,360),(470,420),(520,500),(520,585),(455,590),(410,520),(352,470)],
    torso_x=372, torso=[(0,0),(398,0),(398,372),(386,420),(382,470),(392,520),(410,560),(430,600),(0,600)],
    pivot=(385,405), hand=(482,540),
    hipcut=[(0,585),(120,585),(180,600),(250,612),(320,605),(380,585),(561,585)],
    hips=((175,610),(300,605)), ground=872),
 'front': dict(src='boy_front.png',
    arm=None, torso_x=None, pivot=None, hand=None,
    hipcut=None, hips=((170,500),(330,520)), ground=600),
}
FRONT_LEGS=[(0,468),(38,452),(70,458),(110,468),(160,478),(205,494),(240,512),(280,506),(300,486),(330,482),(348,474),(375,482),(380,540),(440,560),(470,604),(0,604)]
