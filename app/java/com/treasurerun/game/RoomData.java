package com.treasurerun.game;

/* loaded from: classes.dex */
final class RoomData {
    static final Room[] ROOMS = new Room[2];

    static {
        Room room = new Room();
        room.label = "ROOM 1";
        room.title = "DIAMOND CORRIDOR";
        room.objective = "SNEAK TO THE FAR DOOR";
        room.walk = "rooms/room1_walk.png";
        room.bg = new String[]{"rooms/room1_bg_0.jpg", "rooms/room1_bg_1.jpg"};
        room.bgY = new int[]{0, 1256};
        room.w = 1056;
        room.h = 2511;
        room.walkDiv = 4;
        room.horizon = 790.0f;
        room.yRef = 2100.0f;
        room.boyH = 730.132f;
        room.speed = 820.0f;
        room.vFactor = 0.85f;
        room.vPow = 2.0f;
        room.feetR = 40.0f;
        room.spawnX = 505.0f;
        room.spawnY = 2150.0f;
        room.enterX = 505.0f;
        room.enterY = 2640.0f;
        room.exitX0 = 395.0f;
        room.exitX1 = 660.0f;
        room.exitY = 1128.0f;
        room.doorX = 518.0f;
        room.doorY = 1005.0f;
        room.zoomFar = 1.36f;
        room.sFar = 0.3f;
        room.viewW = 0.0f;
        room.feetAt = 0.68f;
        room.occ = new Occ[]{new Occ("rooms/room1_occ_teal_box.png", 775, 1191, 86, 73, 1262.0f), new Occ("rooms/room1_occ_diamond_case.png", 123, 880, 238, 399, 1272.0f), new Occ("rooms/room1_occ_lamp_case.png", 0, 1124, 212, 671, 1770.0f), new Occ("rooms/room1_occ_statue_case.png", 825, 874, 231, 526, 1393.0f), new Occ("rooms/room1_occ_plant.png", 884, 1138, 172, 321, 1443.0f), new Occ("rooms/room1_occ_orange_box.png", 985, 1444, 71, 85, 1522.0f), new Occ("rooms/room1_occ_purple_crate.png", 925, 1574, 131, 203, 1768.0f)};
        room.coins = new float[][]{new float[]{485.0f, 1185.34f, 78.0f}, new float[]{394.0f, 1340.36f, 112.0f}, new float[]{304.0f, 1516.26f, 142.0f}, new float[]{760.0f, 1650.0f, 170.687f}, new float[]{660.0f, 1300.0f, 101.221f}, new float[]{560.0f, 1880.0f, 216.336f}};
        room.gems = new float[0][];
        // v0.5: the painted guard is live and patrols across the middle of the corridor; the case diamond is the heist
        room.objectiveHeist = "STEAL THE DIAMOND";
        room.guardH = 870.0f;
        room.visionRange = 0.95f;
        room.beamColor = 0xFFFFE9A8;
        room.guards = new Room.GuardDef[]{
                new Room.GuardDef(new float[][]{{290.0f, 1650.0f}, {850.0f, 1650.0f}}, new float[]{1.6f, 2.05f, 1.6f, -2.05f}, true)};
        Room.Heist heist = new Room.Heist();
        heist.gemX = 243.0f; heist.gemY = 995.0f; heist.gemSize = 110.0f; heist.tapR = 165.0f;
        heist.standX = 258.0f; heist.standY = 1322.0f;
        heist.emptyPatch = "rooms/room1_case_empty.png"; heist.patchX = 153; heist.patchY = 906;
        heist.occ = "rooms/room1_occ_diamond_case.png"; heist.occEmpty = "rooms/room1_occ_diamond_case_empty.png";
        heist.gate = new int[]{370, 0, 690, 1170};
        room.heist = heist;
        ROOMS[0] = room;
        Room room2 = new Room();
        room2.label = "ROOM 2";
        room2.title = "GRAND HALL";
        room2.objective = "FIND THE EXIT";
        room2.walk = "rooms/room2_walk.png";
        room2.bg = new String[]{"rooms/room2_bg_0.jpg", "rooms/room2_bg_1.jpg"};
        room2.bgY = new int[]{0, 1765};
        room2.w = 2322;
        room2.h = 3530;
        room2.walkDiv = 4;
        room2.horizon = -2860.0f;
        room2.yRef = 2840.0f;
        room2.boyH = 425.097f;
        room2.speed = 440.0f;
        room2.vFactor = 0.8f;
        room2.vPow = 1.0f;
        room2.feetR = 22.0f;
        room2.spawnX = 936.0f;
        room2.spawnY = 2680.0f;
        room2.enterX = 936.0f;
        room2.enterY = 3180.0f;
        room2.exitX0 = 1132.0f;
        room2.exitX1 = 1380.0f;
        room2.exitY = 750.0f;
        room2.doorX = 1256.0f;
        room2.doorY = 640.0f;
        room2.zoomFar = 1.0f;
        room2.sFar = 0.5f;
        room2.viewW = 1250.0f;
        room2.feetAt = 0.6f;
        room2.occ = new Occ[]{new Occ("rooms/room2_occ_plant_tl.png", 127, 599, 165, 207, 808.0f), new Occ("rooms/room2_occ_plaque.png", 274, 714, 177, 149, 856.0f), new Occ("rooms/room2_occ_knight.png", 430, 558, 175, 278, 836.0f), new Occ("rooms/room2_occ_plant_tm.png", 569, 558, 174, 217, 756.0f), new Occ("rooms/room2_occ_bench_tl.png", 378, 822, 213, 161, 976.0f), new Occ("rooms/room2_occ_case_ul.png", 654, 722, 233, 321, 1040.0f), new Occ("rooms/room2_occ_colonnade.png", 0, 824, 231, 385, 1206.0f), new Occ("rooms/room2_occ_plant_exl.png", 869, 562, 170, 159, 730.0f), new Occ("rooms/room2_occ_ropes_l.png", 1034, 650, 77, 241, 888.0f), new Occ("rooms/room2_occ_ropes_r.png", 1342, 650, 137, 249, 896.0f), new Occ("rooms/room2_occ_cluster.png", 855, 784, 247, 329, 1104.0f), new Occ("rooms/room2_occ_wall_h_r.png", 1362, 886, 213, 169, 1052.0f), new Occ("rooms/room2_occ_plant_r.png", 1361, 921, 148, 229, 1156.0f), new Occ("rooms/room2_occ_wall_v_r_0.png", 1490, 886, 114, 76, 1020.0f), new Occ("rooms/room2_occ_wall_v_r_1.png", 1490, 958, 114, 76, 1092.0f), new Occ("rooms/room2_occ_wall_v_r_2.png", 1490, 1030, 115, 76, 1164.0f), new Occ("rooms/room2_occ_wall_v_r_3.png", 1490, 1102, 116, 76, 1236.0f), new Occ("rooms/room2_occ_wall_v_r_4.png", 1490, 1174, 116, 76, 1304.0f), new Occ("rooms/room2_occ_wall_v_r_5.png", 1490, 1246, 117, 61, 1304.0f), new Occ("rooms/room2_occ_plant_tr1.png", 1504, 552, 145, 191, 730.0f), new Occ("rooms/room2_occ_cabinet.png", 1610, 654, 221, 197, 848.0f), new Occ("rooms/room2_occ_plant_tr2.png", 1798, 547, 140, 235, 764.0f), new Occ("rooms/room2_occ_trex_a.png", 1798, 654, 175, 489, 1040.0f), new Occ("rooms/room2_occ_trex_b.png", 1968, 542, 175, 741, 1210.0f), new Occ("rooms/room2_occ_trex_c.png", 2138, 528, 184, 845, 1360.0f), new Occ("rooms/room2_occ_coin_case.png", 1630, 1090, 253, 413, 1500.0f), new Occ("rooms/room2_occ_plant_crown.png", 1455, 1218, 74, 118, 1328.0f), new Occ("rooms/room2_occ_wall_r2.png", 1766, 1410, 353, 237, 1644.0f), new Occ("rooms/room2_occ_plant_r2.png", 1911, 1417, 208, 274, 1692.0f), new Occ("rooms/room2_occ_statue_r.png", 2087, 1369, 188, 402, 1756.0f), new Occ("rooms/room2_occ_cabinet_r.png", 2242, 1562, 80, 177, 1736.0f), new Occ("rooms/room2_occ_crown.png", 884, 1214, 583, 557, 1768.0f), new Occ("rooms/room2_occ_wall_v_l_0.png", 842, 1050, 125, 72, 1188.0f), new Occ("rooms/room2_occ_wall_v_l_1.png", 842, 1118, 125, 72, 1256.0f), new Occ("rooms/room2_occ_wall_v_l_2.png", 796, 1186, 171, 72, 1324.0f), new Occ("rooms/room2_occ_wall_v_l_3.png", 782, 1254, 133, 72, 1392.0f), new Occ("rooms/room2_occ_wall_v_l_4.png", 741, 1322, 173, 72, 1460.0f), new Occ("rooms/room2_occ_wall_v_l_5.png", 698, 1390, 215, 72, 1528.0f), new Occ("rooms/room2_occ_wall_v_l_6.png", 699, 1458, 213, 72, 1596.0f), new Occ("rooms/room2_occ_wall_v_l_7.png", 700, 1526, 210, 72, 1664.0f), new Occ("rooms/room2_occ_wall_v_l_8.png", 701, 1594, 208, 72, 1732.0f), new Occ("rooms/room2_occ_wall_v_l_9.png", 702, 1662, 206, 72, 1800.0f), new Occ("rooms/room2_occ_wall_v_l_10.png", 747, 1730, 160, 72, 1860.0f), new Occ("rooms/room2_occ_wall_v_l_11.png", 754, 1798, 149, 65, 1860.0f), new Occ("rooms/room2_occ_olive_crate.png", 878, 1750, 185, 181, 1928.0f), new Occ("rooms/room2_occ_pillar_l.png", 750, 1822, 141, 341, 2160.0f), new Occ("rooms/room2_occ_pillar_cannon.png", 878, 1866, 125, 287, 2150.0f), new Occ("rooms/room2_occ_low_wall.png", 186, 1842, 573, 117, 1956.0f), new Occ("rooms/room2_occ_bench_ml.png", 406, 1950, 265, 203, 2150.0f), new Occ("rooms/room2_occ_case_gold.png", 34, 1890, 449, 637, 2524.0f), new Occ("rooms/room2_occ_wall_edge_l.png", 0, 1888, 87, 475, 2360.0f), new Occ("rooms/room2_occ_pillar_m.png", 1122, 1862, 121, 205, 2064.0f), new Occ("rooms/room2_occ_wall_m.png", 1234, 1870, 369, 169, 2036.0f), new Occ("rooms/room2_occ_crates_m.png", 1122, 2022, 241, 389, 2408.0f), new Occ("rooms/room2_occ_wall_diag_0.png", 1586, 1926, 185, 72, 2064.0f), new Occ("rooms/room2_occ_wall_diag_1.png", 1586, 1994, 209, 72, 2132.0f), new Occ("rooms/room2_occ_wall_diag_2.png", 1586, 2062, 218, 72, 2200.0f), new Occ("rooms/room2_occ_wall_diag_3.png", 1586, 2130, 226, 72, 2268.0f), new Occ("rooms/room2_occ_wall_diag_4.png", 1586, 2198, 235, 72, 2336.0f), new Occ("rooms/room2_occ_wall_diag_5.png", 1586, 2266, 243, 72, 2388.0f), new Occ("rooms/room2_occ_wall_diag_6.png", 1586, 2334, 249, 61, 2388.0f), new Occ("rooms/room2_occ_case_lr.png", 1946, 1734, 337, 541, 2272.0f), new Occ("rooms/room2_occ_pillar_r.png", 2190, 1962, 132, 405, 2364.0f), new Occ("rooms/room2_occ_crate_r.png", 2054, 2170, 153, 195, 2362.0f), new Occ("rooms/room2_occ_lamp_r.png", 2178, 2166, 144, 350, 2516.0f), new Occ("rooms/room2_occ_pillar_lamp_l.png", 194, 1210, 165, 329, 1536.0f), new Occ("rooms/room2_occ_plant_l1.png", 0, 1250, 163, 326, 1564.0f), new Occ("rooms/room2_occ_crate_blue_l.png", 114, 1426, 133, 137, 1560.0f), new Occ("rooms/room2_occ_ledge_l.png", 0, 1546, 375, 125, 1668.0f), new Occ("rooms/room2_occ_statue_l.png", 351, 1342, 215, 472, 1812.0f), new Occ("rooms/room2_occ_bench_l.png", 34, 1690, 337, 149, 1836.0f), new Occ("rooms/room2_occ_plant_l2.png", 0, 2361, 278, 322, 2676.0f), new Occ("rooms/room2_occ_blocks_bl.png", 0, 2602, 379, 451, 3050.0f), new Occ("rooms/room2_occ_plant_b.png", 314, 2714, 229, 350, 3050.0f), new Occ("rooms/room2_occ_plinth.png", 1070, 2310, 177, 353, 2660.0f), new Occ("rooms/room2_occ_bench_b.png", 1254, 2486, 257, 165, 2648.0f), new Occ("rooms/room2_occ_crate_b.png", 1306, 2682, 149, 201, 2880.0f), new Occ("rooms/room2_occ_case_blue.png", 1438, 2362, 409, 625, 2984.0f), new Occ("rooms/room2_occ_blocks_br.png", 1658, 2822, 497, 231, 3050.0f), new Occ("rooms/room2_occ_plant_br.png", 2064, 2602, 258, 462, 3050.0f)};
        room2.coins = new float[0][];
        room2.gems = new float[][]{new float[]{324.0f, 1056.0f, 105.0f}, new float[]{1194.0f, 1056.0f, 102.9f}, new float[]{2002.0f, 1372.0f, 111.3f}, new float[]{634.0f, 1782.0f, 128.1f}, new float[]{1406.0f, 2180.0f, 153.3f}, new float[]{1972.0f, 2694.0f, 149.1f}};
        room2.guardH = 510.0f;
        room2.beamColor = 0xFFFFE9A8;
        room2.guards = new Room.GuardDef[]{
                new Room.GuardDef(new float[][]{{150.0f, 1282.0f}, {740.0f, 1282.0f}}, new float[]{1.3f, 2.6f, 1.3f, -2.6f}, true),
                new Room.GuardDef(new float[][]{{1190.0f, 1902.0f}, {1970.0f, 1902.0f}}, new float[]{1.3f, 2.8f, 1.3f, -2.8f}, true)};
        ROOMS[1] = room2;
    }

    private RoomData() {
    }
}
