import os; ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
# Cut definitions for the boy views (sprite pixel coords of the original v0.4 poses).
# arm: the visible arm, cut below the cheek; torso: the body keeps everything inside this polygon (its right
# side is the torso contour hidden under the arm; arm pixels outside it become transparent, inside get inpainted).
CUTS = {
 'back': dict(src='boy_back.png',
    arm=[(250,318),(272,300),(296,312),(318,338),(345,380),(372,420),(380,470),(355,498),(318,500),(288,470),(262,440),(252,400)],
    torso=[(0,0),(371,0),(371,300),(270,302),(263,330),(260,380),(258,430),(261,480),(268,505),(0,505)],
    pivot=(272,320), hand=(338,452),
    hipcut=[(0,500),(90,500),(130,512),(170,516),(215,512),(262,502),(371,500)],
    hips=((128,522),(212,522)), ground=758),
 'tq': dict(src='boy_tq.png',
    arm=[(358,398),(380,378),(404,383),(428,420),(452,452),(480,478),(522,505),(530,560),(492,590),(440,578),(398,560),(362,545),(356,505),(356,440)],
    torso=[(0,0),(561,0),(561,372),(420,375),(404,386),(399,420),(397,470),(391,520),(380,560),(362,600),(0,600)],
    pivot=(380,405), hand=(482,540),
    hipcut=[(0,585),(120,585),(180,600),(250,612),(320,605),(380,585),(561,585)],
    hips=((175,610),(300,605)), ground=872),
 'front': dict(src='boy_front.png',
    arm=None, torso=None, pivot=None, hand=None,
    hipcut=None, hips=((170,500),(330,520)), ground=600),
}
FRONT_LEGS=[(0,468),(38,452),(70,458),(110,468),(160,478),(205,494),(240,512),(280,506),(300,486),(330,482),(348,474),(375,482),(380,540),(440,560),(470,604),(0,604)]
