package cn.byyddyh.spoofingdetection.process.dataModel;

import cn.byyddyh.spoofingdetection.process.utils.GpsConstants;

public class UtcTime {
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int sec;

    public UtcTime() {
    }

    private final static long HOURSEC = 3600;
    private final static long MINSEC = 60;

    public UtcTime(int year, int month, int day, int hour, int minute, int sec) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.sec = sec;
    }

    /**
     * Convert GPS time (week & seconds), or Full Cycle Time (seconds) to UTC
     */
    public static UtcTime gps2Utc(long fctSeconds) {
        // fct at 2100/1/1 00:00:00, not counting leap seconds:
        long fct2100 = 6260 * GpsConstants.WEEKSEC + 432000;
        if (fct2100 < 0 || fctSeconds >= fct2100) {
            throw new Error("gpsTime must be in this range: [0,0] <= gpsTime < [6260, 432000]");
        }

        // 应用算法处理跳跃秒
        // 1. convert gpsTime to time = [yyyy,mm,dd,hh,mm,ss] (with no leap seconds)
        UtcTime utcTime = fct2Ymdhms(fctSeconds);
        // 2. look up leap seconds for time: ls = LeapSeconds(time)
        int ls = leapSeconds(utcTime);
        // 3. convert gpsTime-ls to timeMLs
        UtcTime timeMLs = fct2Ymdhms(fctSeconds - ls);
        // 4. look up leap seconds: ls1 = LeapSeconds(timeMLs);
        int ls1 = leapSeconds(timeMLs);
        // 5. if ls1~=ls, convert (gpsTime-ls1) to UTC Time
        if (ls == ls1) {
            utcTime = timeMLs;
        } else {
            utcTime = fct2Ymdhms(fctSeconds - ls1);
        }
        return utcTime;
    }

    /**
     * Convert the UTC date and time to GPS week & seconds
     */
    public static long[] utc2Gps(UtcTime... utcTime) {
        long[] gpsTime = new long[2 * utcTime.length];

        checkUtcTimeInputs(utcTime);
        double[] julianDays = julianDay(utcTime);

        for (int i = 0; i < julianDays.length; ++i) {
            int daysSinceEpoch = (int) (julianDays[i] - GpsConstants.GPSEPOCHJD);
            long gpsWeek = (long) (daysSinceEpoch / 7.0);

            int dayOfWeek = daysSinceEpoch % 7;
            // calculate the number of seconds since Sunday at midnight
            long gpsSeconds = dayOfWeek * GpsConstants.DAYSEC + utcTime[i].hour * HOURSEC + utcTime[i].minute * MINSEC + utcTime[i].sec;
            gpsWeek = (int) (gpsWeek + gpsSeconds * 1.0 / GpsConstants.WEEKSEC);
            gpsSeconds = gpsSeconds % GpsConstants.WEEKSEC;

            // now add leapSeconds
            long fctSeconds = gpsWeek * GpsConstants.WEEKSEC + gpsSeconds + leapSeconds(utcTime[i]);

            // when a leap second happens, utc time stands still for one second,
            // so gps seconds get further ahead, so we add leapsecs in going to gps time
            gpsWeek = (long) (fctSeconds * 1.0 / GpsConstants.WEEKSEC);
            if (gpsWeek == 0) {
                gpsSeconds = fctSeconds;
            } else {
                gpsSeconds = fctSeconds % (gpsWeek * GpsConstants.WEEKSEC);
            }

            gpsTime[i] = gpsWeek;
            gpsTime[i + utcTime.length] = gpsSeconds;
        }

        return gpsTime;
    }

    private final static long[] monthDays = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    /**
     * 工具类
     * 将GPS全周期时间转换为[yyyy，mm，dd，hh，mm，ss.s]格式
     */
    private static UtcTime fct2Ymdhms(long fctSeconds) {
        // days since 1980/1/1
        long days = fctSeconds / GpsConstants.DAYSEC + 6;
        System.out.println("days: " + days);
        System.out.println("fctSeconds: " + fctSeconds);
        int years = 1980;

        // 每次递减一年的天数，直到我们计算出年份
        int leap = 1;       // 1980年为闰年
        while (days > (leap + 365)) {
            days = days - (leap + 365);
            years += 1;
            // leap = 1 on a leap year, 0 otherwise
            // This works from 1901 till 2099, 2100 isn't a leap year (2000 is).
            // Calculate the year, ie time(1)
            leap = years % 4 == 0 ? 1 : 0;
        }

        UtcTime time = new UtcTime();
        time.year = years;

        // 递减每一月的天数，直到我们计算出月份
        int month = 0;
        if (years % 4 == 0) {
            monthDays[1] = 29;
        } else {
            monthDays[1] = 28;
        }
        while (days > monthDays[month]) {
            days -= monthDays[month];
            month++;
        }

        time.month = month + 1;
        time.day = (int) days;

        long sinceMidnightSeconds = fctSeconds % GpsConstants.DAYSEC;
        System.out.println("sinceMidnightSeconds: \t\t" + sinceMidnightSeconds);
        time.hour = (int) (sinceMidnightSeconds / HOURSEC);

        long lastHourSeconds = sinceMidnightSeconds % HOURSEC;
        System.out.println("lastHourSeconds: \t\t" + lastHourSeconds);
        time.minute = (int) (lastHourSeconds / MINSEC);
        time.sec = (int) (lastHourSeconds % MINSEC);
        return time;
    }

    // UTC table contains UTC times (in the form of [year,month,day,hours,mins,secs])
    // At each of these times a leap second had just occurred
    // TODO 需要更新
    //  when a new leap second is announced in IERS Bulletin C
    //  update the table with the UTC time right after the new leap second
    private static UtcTime[] utcTable = {
            new UtcTime(1982, 1, 1, 0, 0, 0),
            new UtcTime(1982, 7, 1, 0, 0, 0),
            new UtcTime(1983, 7, 1, 0, 0, 0),
            new UtcTime(1985, 7, 1, 0, 0, 0),
            new UtcTime(1988, 1, 1, 0, 0, 0),
            new UtcTime(1990, 1, 1, 0, 0, 0),
            new UtcTime(1991, 1, 1, 0, 0, 0),
            new UtcTime(1992, 7, 1, 0, 0, 0),
            new UtcTime(1993, 7, 1, 0, 0, 0),
            new UtcTime(1994, 7, 1, 0, 0, 0),
            new UtcTime(1996, 1, 1, 0, 0, 0),
            new UtcTime(1997, 7, 1, 0, 0, 0),
            new UtcTime(1999, 1, 1, 0, 0, 0),
            new UtcTime(2006, 1, 1, 0, 0, 0),
            new UtcTime(2009, 1, 1, 0, 0, 0),
            new UtcTime(2012, 7, 1, 0, 0, 0),
            new UtcTime(2015, 7, 1, 0, 0, 0),
            new UtcTime(2017, 1, 1, 0, 0, 0)
    };

    /**
     * days since GPS Epoch
     */
    private static double[] tableJDays;
    private static double[] tableSeconds;

    static {
        tableJDays = julianDay(utcTable);
        tableSeconds = new double[utcTable.length];

        for (int i = 0; i < tableJDays.length; i++) {
            // days since GPS Epoch
            tableJDays[i] -= GpsConstants.GPSEPOCHJD;

            // tableSeconds = tableJDays*GpsConstants.DAYSEC + utcTable(:,4:6)*[3600;60;1];
            // NOTE: JulianDay returns a realed value number, corresponding to days and fractions thereof, so we multiply it by DAYSEC to get the full time in seconds
            // JulianDay返回一个实数值，对应于天数及其分数，因此我们将其乘以DAYSEC，得到以秒为单位的完整时间
            tableSeconds[i] = tableJDays[i] * GpsConstants.DAYSEC + utcTable[i].hour * 3600 + utcTable[i].minute * 60 + utcTable[i].sec;
        }
    }

    /**
     * find the number of leap seconds since the GPS Epoch
     */
    private static int leapSeconds(UtcTime utcTime) {
        double[] jDay = julianDay(utcTime);
        double[] timeSeconds = new double[jDay.length];

        // tableSeconds和timeSeconds现在包含自GPS历元以来的秒数
        for (int i = 0; i < jDay.length; i++) {
            jDay[i] = jDay[i] - GpsConstants.GPSEPOCHJD;
            timeSeconds[i] = jDay[i] * GpsConstants.DAYSEC;
        }

        int leapSecs = 0;
        for (double tableSecond : tableSeconds) {
            if (tableSecond <= timeSeconds[0]) {
                leapSecs++;
            }
        }

        return leapSecs;
    }

    /**
     * days since GPS Epoch
     */
    public static double[] julianDay(UtcTime... utcTimeList) {
        UtcTime[] temp = new UtcTime[utcTimeList.length];
        double[] tableJDays = new double[utcTimeList.length];
        double[] hours = new double[utcTimeList.length];

        for (int i = 0; i < temp.length; i++) {
            UtcTime data = new UtcTime();
            hours[i] = utcTimeList[i].hour + utcTimeList[i].minute / 60.0 + utcTimeList[i].sec / 3600.0;

            if (utcTimeList[i].month <= 2) {
                data.month = utcTimeList[i].month + 12;
                data.year = utcTimeList[i].year - 1;
            } else {
                data.month = utcTimeList[i].month;
                data.year = utcTimeList[i].year;
            }

            data.day = utcTimeList[i].day;
            data.hour = utcTimeList[i].hour;
            temp[i] = data;

            tableJDays[i] = Math.floor(365.25 * temp[i].year) + Math.floor(30.6001 * (temp[i].month + 1)) - 15 + 1720996.5 + temp[i].day + hours[i] / 24;
        }

        return tableJDays;
    }

    /**
     * utility function for Utc2Gps
     */
    private static void checkUtcTimeInputs(UtcTime... utcTime) {
        // 校验输入
        for (UtcTime time : utcTime) {
            if (time.year < 1980 || time.year > 2099) {
                throw new Error("year must have values in the range: [1980:2099]");
            }

            if (time.month < 1 || time.month > 12) {
                throw new Error("The month in utcTime must be a number in the set [1:12]");
            }

            if (time.day < 1 || time.day > 31) {
                throw new Error("The day in utcTime must be a number in the set [1:31]");
            }

            if (time.hour < 0 || time.hour >= 24) {
                throw new Error("The hour in utcTime must be in the range [0,24)");
            }

            if (time.minute < 0 || time.minute >= 60) {
                throw new Error("The minutes in utcTime must be in the range [0,60)");
            }

            if (time.sec < 0 || time.sec > 60) {
                throw new Error("The seconds in utcTime must be in the range [0,60]");
            }
        }
    }

    @Override
    public String toString() {
        return "UtcTime{" +
                "year=" + year +
                ", month=" + month +
                ", day=" + day +
                ", hour=" + hour +
                ", minute=" + minute +
                ", sec=" + sec +
                '}';
    }

    public static void main(String[] args) {
        UtcTime[] utcTimes = {new UtcTime(2021, 8, 23, 0, 0, 0),
                new UtcTime(2023, 12, 25, 0, 0, 0),
                new UtcTime(2050, 12, 28, 0, 0, 0)};

        int N = 30;
        UtcTime[] utcTimes1 = new UtcTime[30];
        for (int i = 0; i < N; i++) {
            UtcTime utcTime = new UtcTime(2050, 12, i+1, 0, 0, 0);
            utcTimes1[i] = utcTime;
        }
    }
}
