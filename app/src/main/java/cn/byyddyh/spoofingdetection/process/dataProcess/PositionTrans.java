package cn.byyddyh.spoofingdetection.process.dataProcess;

import cn.byyddyh.spoofingdetection.process.utils.GpsConstants;

public class PositionTrans {

    public static Double[] Xyz2Lla(Double[] xyzM) {
        // if x and y ecef positions are both zero then lla is undefined
        if (xyzM[0] == 0.0 && xyzM[1] == 0.0) {
            return new Double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        }

        double a2 = Math.pow(GpsConstants.EARTHSEMIMAJOR, 2);
        double b2 = a2 * (1 - GpsConstants.EARTHECCEN2);
        double b = Math.sqrt(b2);
        double ep2 = (a2 - b2) / b2;
        double p = Math.sqrt(Math.pow(xyzM[0], 2) + Math.pow(xyzM[1], 2));

        // two sides and hypotenuse of right angle triangle with one angle = theta
        double s1 = xyzM[2] * GpsConstants.EARTHSEMIMAJOR;
        double s2 = p * b;
        double h = Math.sqrt(Math.pow(s1, 2) + Math.pow(s2, 2));
        double sin_theta = s1 / h;
        double cos_theta = s2 / h;

        // two sides and hypotenuse of right angle triangle with one angle = lat
        s1 = xyzM[2] + ep2 * b * Math.pow(sin_theta, 3);
        s2 = p - GpsConstants.EARTHSEMIMAJOR * GpsConstants.EARTHECCEN2 * Math.pow(cos_theta, 3);
        h = Math.sqrt(Math.pow(s1, 2) + Math.pow(s2, 2));
        double tan_lat = s1 / s2;
        double sin_lat = s1 / h;
        double cos_lat = s2 / h;
        double latDeg = Math.atan(tan_lat);
        double r2D = 180 / Math.PI;
        latDeg = latDeg * r2D;

        double N = a2 * (Math.pow(a2 * Math.pow(cos_lat, 2) + b2 * Math.pow(sin_lat, 2), -0.5));
        double altM = p / cos_lat - N;

        // rotate longitude to where it would be for a fixed point in ECI
        double lonDeg = Math.atan2(xyzM[1], xyzM[0]);
        lonDeg = (lonDeg % (Math.PI * 2)) * r2D;
        if (lonDeg > 180) {
            lonDeg -= 360;
        }

        return new Double[]{latDeg, lonDeg, altM};
    }

    public static Double[][] RotEcef2Ned(double latDeg, double lonDeg) {
        double D2R = Math.PI / 180;
        double latRad = D2R * latDeg;
        double lonRad = D2R * lonDeg;

        double clat = Math.cos(latRad);
        double slat = Math.sin(latRad);
        double clon = Math.cos(lonRad);
        double slon = Math.sin(lonRad);

        Double[][] Re2n = new Double[3][3];
        Re2n[0][0] = -slat * clon;
        Re2n[0][1] = -slat * slon;
        Re2n[0][2] = clat;

        Re2n[1][0] = -slon;
        Re2n[1][1] = clon;
        Re2n[1][2] = 0.0;

        Re2n[2][0] = -clat * clon;
        Re2n[2][1] = -clat * slon;
        Re2n[2][2] = -slat;

        return Re2n;
    }
}
