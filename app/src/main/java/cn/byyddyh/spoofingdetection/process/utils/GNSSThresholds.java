package cn.byyddyh.spoofingdetection.process.utils;

public class GNSSThresholds {
    public final static Integer MAXDELPOSFORNAVM = 20;      // 最大位置可以在导航解的一次迭代中发生变化，而los矢量的变化不超过1微弧度
    public final static Integer MAXPRRUNCMPS = 10;          // 最大伪距速率（多普勒）不确定性。较大的值可能只是搜索箱大小，因此对导航无效。
    public final static Integer MAXTOWUNCNS = 500;          // 最大Tow不确定度，500 ns。在这段时间内，卫星的射程可以改变大约半毫米
    public final static Integer MINNUMGPSEPH = 24;          // 满足条件的最小GPS星历数量
}
