package cn.byyddyh.spoofingdetection.process.dataModel;

public class GNSSAnalysis {
    private String GnssClockErrors;
    private String GnssMeasurementErrors;
    private String ApiPassFail;

    public String getGnssClockErrors() {
        return GnssClockErrors;
    }

    public void setGnssClockErrors(String gnssClockErrors) {
        GnssClockErrors = gnssClockErrors;
    }

    public String getGnssMeasurementErrors() {
        return GnssMeasurementErrors;
    }

    public void setGnssMeasurementErrors(String gnssMeasurementErrors) {
        GnssMeasurementErrors = gnssMeasurementErrors;
    }

    public String getApiPassFail() {
        return ApiPassFail;
    }

    public void setApiPassFail(String apiPassFail) {
        ApiPassFail = apiPassFail;
    }
}
