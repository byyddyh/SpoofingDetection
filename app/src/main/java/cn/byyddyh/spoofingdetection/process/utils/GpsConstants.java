package cn.byyddyh.spoofingdetection.process.utils;

import java.math.BigDecimal;

public class GpsConstants {
    public final static double EARTHECCEN2 = 6.69437999014e-3;     // WGS 84 (Earth eccentricity)^2 (m^2)
    public final static long EARTHMEANRADIUS = 6371009;            // Mean R of ellipsoid(m) IU Gedosey& Geophysics
    public final static long EARTHSEMIMAJOR = 6378137;             // WGS 84 Earth semi-major axis (m)
    public final static long EPHVALIDSECONDS = 7200;             // +- 2 hours ephemeris validity
    public final static long DAYSEC = 86400;                     // number of seconds in a day
    public final static double FREL = -4.442807633e-10;            // Clock relativity parameter, (s/m^1/2)
    public final static double GPSEPOCHJD = 2444244.5;             // GPS Epoch in Julian Days
    public final static long HORIZDEG = 5;                       // angle above horizon at which GPS models break down
    public final static double LIGHTSPEED = 2.99792458e8;          // WGS-84 Speed of light in a vacuum (m/s)
    // mean time of flight btwn closest GPS sat (~66 ms) & furthest (~84 ms):
    public final static double MEANTFLIGHTSECONDS = 75e-3;
    public final static double mu = 3.986005e14;                   // WGS-84 Universal gravitational parameter (m^3/sec^2)
    public final static BigDecimal muBig = new BigDecimal("3.986005e14");
    public final static double WE = 7.2921151467e-5;               // WGS 84 value of earth's rotation rate (rad/s)
    public final static long WEEKSEC = 604800;                   // number of seconds in a week
}
