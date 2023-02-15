package cn.byyddyh.spoofingdetection.process.utils;

import java.math.BigDecimal;
import java.util.*;

/**
 * 工具类
 */
public class MathUtils {
    private static final BigDecimal BIG_DECIMAL_TEMP = new BigDecimal("0.5");

    /**
     * 获取矩阵的逆
     */
    public static Double[][] inverse(Double[][] A) {
        Double[][] B = new Double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) {
            System.arraycopy(A[i], 0, B[i], 0, A[0].length);
        }
        Double[][] C = new Double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[0].length; j++) {
                C[i][j] = (int) Math.pow(-1, i + j) * determinant(minor(B, j, i));
            }
        }
        Double[][] D = new Double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[0].length; j++) {
                D[i][j] = C[i][j] / determinant(A);
            }
        }
        return D;
    }

    /**
     * 求解矩阵的行列式
     */
    private static Double determinant(Double[][] a) {
        if (a.length == 1) {
            return a[0][0];
        }
        double det = 0.0;
        for (int i = 0; i < a[0].length; i++) {
            det += (int) Math.pow(-1, i) * a[0][i] * determinant(minor(a, 0, i));
        }
        return det;
    }

    /**
     * 求解二维矩阵在某一位置的伴随矩阵
     */
    private static Double[][] minor(Double[][] b, int i, int j) {
        Double[][] a = new Double[b.length - 1][b[0].length - 1];
        for (int x = 0, y = 0; x < b.length; x++) {
            if (x == i) {
                continue;
            }
            for (int m = 0, n = 0; m < b[0].length; m++) {
                if (m == j) {
                    continue;
                }
                a[y][n] = b[x][m];
                n++;
            }
            y++;
        }
        return a;
    }

    public static double[][] matrixMultipleArr(Double[][] mat1, Double[][] mat2) {
        double[][] res = new double[mat1.length][mat2[0].length];
        for (int i = 0; i < res.length; i++) {
            for (int j = 0; j < res[0].length; j++) {
                res[i][j] = 0.0;
                for (int k = 0; k < mat1[0].length; k++) {
                    res[i][j] += mat1[i][k] * mat2[k][j];
                }
            }
        }
        return res;
    }

    /**
     * 矩阵相乘
     */
    public static Double[][] matrixMultiple(Double[][] mat1, Double[][] mat2) {
        Double[][] res = new Double[mat1.length][mat2[0].length];
        for (int i = 0; i < res.length; i++) {
            for (int j = 0; j < res[0].length; j++) {
                res[i][j] = 0.0;
                for (int k = 0; k < mat1[0].length; k++) {
                    res[i][j] += mat1[i][k] * mat2[k][j];
                }
            }
        }
        return res;
    }

    /**
     * 矩阵相减
     */
    public static void matrixSub(Double[][] v, Double[][] svXyzTrx) {
        for (int i = 0; i < v.length; i++) {
            for (int j = 0; j < svXyzTrx.length; j++) {
                v[i][j] = v[i][j] - svXyzTrx[j][i];
            }
        }
    }

    /**
     * 向量 * 向量得到矩阵
     */
    public static Double[][] arrayMultipleArray(Double[] arr1, Double[] arr2) {
        Double[][] res = new Double[arr1.length][arr2.length];

        for (int i = 0; i < arr1.length; i++) {
            for (int j = 0; j < arr2.length; j++) {
                res[i][j] = arr1[i] * arr2[j];
            }
        }

        return res;
    }

    /**
     * 矩阵乘数组
     */
    public static Double[] matrixMultipleArray(Double[][] matrix, Double[] array) {
        Double[] res = new Double[matrix.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = 0.0;
            for (int j = 0; j < array.length; j++) {
                res[i] += matrix[i][j] * array[j];
            }
        }

        return res;
    }

    /**
     * Compute rotated satellite ECEF coordinates caused by Earth rotation during signal flight time
     */
    public static Double[] flightTimeCorrection(Double[] xE, double dTflightSeconds) {
        // Rotation angle (radians)
        double theta = GpsConstants.WE * dTflightSeconds;

        // Apply rotation from IS GPS 200-E, 20.3.3.4.3.3.2
        // Note: IS GPS 200-E shows the rotation from ecef to eci
        // so our rotation R3, is in the opposite direction:
        Double[][] R3 = {
                {Math.cos(theta), Math.sin(theta), 0.0},
                {-Math.sin(theta), Math.cos(theta), 0.0},
                {0.0, 0.0, 1.0}
        };

        Double[] xERot = MathUtils.matrixMultipleArray(R3, xE);
        return xERot;
    }

    public static Double norm(Double[] data) {
        double val = 0.0;
        for (Double datum : data) {
            val += Math.pow(datum, 2);
        }

        return Math.sqrt(val);
    }

    public static Double[][] generateDiag(Double d, List<List<Double>> prs, Integer index) {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < prs.size(); i++) {
            data.add(1 / prs.get(i).get(index));
        }
        return diag(data);
    }

    /**
     * 根据向量生成矩阵
     */
    public static Double[][] diag(List<Double> data) {
        Double[][] matrix = new Double[data.size()][data.size()];
        for (int i = 0; i < data.size(); i++) {
            for (int j = 0; j < data.size(); j++) {
                if (i == j) {
                    matrix[i][j] = data.get(i);
                } else {
                    matrix[i][j] = 0.0;
                }
            }
        }

        return matrix;
    }

    /**
     * ek = Kepler(mk,e)
     * Kepler - Solves Kepler's equation for ek through iteration.
     */
    public static Double[] Kepler(List<Double> mk, List<Double> e) {
        double err;
        Double[] ek = new Double[mk.size()];
        for (int i = 0; i < mk.size(); i++) {
            ek[i] = mk.get(i);
        }

        int iterCount = 0;
        int maxIterCount = 20;
        double minVal = 100;

        while (Math.abs(minVal) > 1e-8 && iterCount < maxIterCount) {
            for (int i = 0; i < mk.size(); i++) {
                err = ek[i] - mk.get(i) - (e.get(i) * Math.sin(ek[i]));
                if (i == 0) {
                    minVal = err;
                } else {
                    minVal = Math.min(minVal, err);
                }

                ek[i] -= err;
            }
            iterCount = iterCount + 1;
            if (iterCount == maxIterCount) {
                System.out.println("Failed convergence on Kepler''s equation.");
            }
        }
        return ek;
    }

    /**
     * double 转换为 long
     */
    public static long bigDecimalToLong(String str) {
        BigDecimal bigDecimal = new BigDecimal(str);
        if (bigDecimal.compareTo(BigDecimal.ZERO) > 0) {
            return bigDecimal.add(BIG_DECIMAL_TEMP).longValue();
        } else {
            return bigDecimal.subtract(BIG_DECIMAL_TEMP).longValue();
        }
    }

    /**
     * 对应matlab中sign
     */
    public static int sign(float num) {
        return num == 0 ? 0 : (num > 0 ? 1 : -1);
    }

    /**
     * 对应matlab中sign
     */
    public static int sign(double num) {
        return num == 0 ? 0 : (num > 0 ? 1 : -1);
    }

    /**
     * 对应matlab中sign
     */
    public static int sign(int num) {
        return Integer.compare(num, 0);
    }

    /**
     * 对应matlab中sign
     */
    public static int[] sign(int[] array) {
        int[] result = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = Integer.compare(array[i], 0);
        }
        return result;
    }

    /**
     * 对应matlab中sign
     */
    public static double[] sign(double[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] == 0 ? 0 : (array[i] > 0 ? 1 : -1);
        }
        return result;
    }

    /**
     * 两个数组相加
     */
    public static int[] arraySum(int[] array1, int[] array2) {
        int[] result = new int[Math.max(array1.length, array2.length)];
        for (int i = 0; i < result.length; i++) {
            if (i < array1.length && i < array2.length) {
                result[i] = array1[i] + array2[i];
            } else {
                result[i] = i < array1.length ? array1[i] : array2[i];
            }
        }
        return result;
    }

    /**
     * 两个数组相加
     */
    public static double[] arraySum(double[] array1, double[] array2) {
        double[] result = new double[Math.max(array1.length, array2.length)];
        for (int i = 0; i < result.length; i++) {
            if (i < array1.length && i < array2.length) {
                result[i] = array1[i] + array2[i];
            } else {
                result[i] = i < array1.length ? array1[i] : array2[i];
            }
        }
        return result;
    }

    /**
     * 两个数组相加
     */
    public static float[] arraySum(float[] array1, float[] array2) {
        float[] result = new float[Math.max(array1.length, array2.length)];
        for (int i = 0; i < result.length; i++) {
            if (i < array1.length && i < array2.length) {
                result[i] = array1[i] + array2[i];
            } else {
                result[i] = i < array1.length ? array1[i] : array2[i];
            }
        }
        return result;

    }

    /**
     * 数组一定区间元素和
     */
    public static double arraySingleSumRange(double[] array, int start, int end) {
        double result = 0;
        for (int i = start; i < end; i++) {
            result += array[i];
        }
        return result;
    }

    /**
     * 数组元素和
     */
    public static double arraySingleSum(double[] array) {
        double result = 0;
        for (double v : array) {
            result += v;
        }
        return result;
    }

    /**
     * 数组元素和
     */
    public static int arraySingleSum(int[] array) {
        int result = 0;
        for (int j : array) {
            result += j;
        }
        return result;
    }

    /**
     * 两个List相减
     */
    public static List<Integer> listSub(List<Integer> array1, List<Integer> array2) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < array1.size(); i++) {
            result.add(array1.get(i) - array2.get(i));
        }
        return result;
    }

    /**
     * 两个数组相减
     */
    public static int[] arraySub(int[] array1, int[] array2) {
        int[] result = new int[Math.max(array1.length, array2.length)];
        for (int i = 0; i < result.length; i++) {
            if (i < array1.length && i < array2.length) {
                result[i] = array1[i] - array2[i];
            } else {
                result[i] = i < array1.length ? array1[i] : -array2[i];
            }
        }
        return result;
    }

    /**
     * 两个数组相减
     */
    public static double[] arraySub(double[] array1, double[] array2) {
        double[] result = new double[Math.max(array1.length, array2.length)];
        for (int i = 0; i < result.length; i++) {
            if (i < array1.length && i < array2.length) {
                result[i] = array1[i] - array2[i];
            } else {
                result[i] = i < array1.length ? array1[i] : -array2[i];
            }
        }
        return result;
    }

    /**
     * 两个数组相减
     */
    public static double[] arraySub(int num, double[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = num - array[i];
        }
        return result;
    }

    /**
     * 两个数组相减
     */
    public static double[] arraySubAbs(double num, double[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = Math.abs(num - array[i]);
        }
        return result;
    }

    /**
     * 单个数组绝对值
     */
    public static double[] arraySingleAbs(double[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = Math.abs(array[i]);
        }
        return result;
    }

    /**
     * 数组乘法后求和
     */
    public static double arrayMultipleSum(double[] array1, double[] array2) {
        double result = 0;
        if (array1.length != array2.length) {
            return result;
        }
        for (int i = 0; i < array1.length; i++) {
            result += array1[i] * array2[i];
        }
        return result;
    }

    /**
     * 数组乘法
     */
    public static float[] arrayMultiple(float[] array, int num) {
        float[] result = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] * num;
        }
        return result;
    }

    /**
     * 数组乘法
     */
    public static double[] arrayMultiple(double[] array, double num) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] * num;
        }
        return result;
    }

    /**
     * 数组乘法
     */
    public static float[] arrayMultiple(float[] array, double num) {
        float[] result = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = (float) (array[i] * num);
        }
        return result;
    }

    /**
     * 数组乘法
     */
    public static int[] arrayMultiple(int[] array, int num) {
        int[] result = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] * num;
        }
        return result;
    }

    /**
     * 数组除法
     */
    public static double[] arrayDivide(int num, int[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = num * 1.0 / array[i];
        }
        return result;
    }

    /**
     * 数组除法
     * <p>
     * 这个方法是如果array中存储的是别的数组下标，matlab中数组是以1为起始点的，而java中是以0开始
     */
    public static double[] arrayDividePos(int num, int[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = num * 1.0 / (array[i] + 1);
        }
        return result;
    }

    /**
     * 一个数组平方和
     */
    public static double arraySquareSum(double[] array) {
        double result = 0;
        for (int i = 0; i < array.length; i++) {
            result += array[i] * array[i];
        }
        return result;
    }

    /**
     * 一个数组中的最小值
     */
    public static int arrayMin(int[] array) {
        int result = array[0];
        for (int num : array) {
            if (num < result) {
                result = num;
            }
        }
        return result;
    }

    /**
     * 一个数组中的最小值
     */
    public static double arrayMin(double[] array) {
        double result = array[0];
        for (double num : array) {
            if (num < result) {
                result = num;
            }
        }
        return result;
    }

    /**
     * 一个数组中的最小值的位置
     */
    public static int arrayIndexMin(int[] array) {
        int index = 0;
        int result = array[0];
        for (int i = 0; i < array.length; ++i) {
            if (array[i] < result) {
                result = array[i];
                index = i;
            }
        }
        return index;
    }

    /**
     * 一个数组中的最小值的位置
     */
    public static int arrayIndexMin(double[] array) {
        int index = 0;
        double result = array[0];
        for (int i = 0; i < array.length; ++i) {
            if (array[i] < result) {
                result = array[i];
                index = i;
            }
        }
        return index;
    }

    /**
     * 生成一个数组
     */
    public static double[] createArray(double start, double step, double end) {
        int length = (int) ((end - start) / step);
        if (length * step != end - start) {
            length += 1;
        }
        double[] result = new double[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = start + step * i;
        }
        return result;
    }

    /**
     * 获取一定范围内的数组
     */
    public static double[] rangeArray(double[] array, int start, int end) {
        double[] newArray = new double[end - start];
        for (int i = start; i < end; i++) {
            newArray[i - start] = array[i];
        }
        return newArray;
    }

    /**
     * 初始化数组
     */

    public static void initArray(int[] array) {
        Arrays.fill(array, 0);
    }

    /**
     * 初始化数组
     */
    public static void initArray(double[] array) {
        Arrays.fill(array, 0);
    }

    /**
     * 初始化数组
     */
    public static void initArray(float[] array) {
        Arrays.fill(array, 0);
    }

    /**
     * 初始化数组
     */

    public static void initArray(double[] array, int start, int end) {
        for (int i = start; i < end; i++) {
            array[i] = 0;
        }
    }

    /**
     * 初始化数组
     */
    public static void initArray(double[] array, int start, int end, double defaultValue) {
        for (int i = start; i < end; i++) {
            array[i] = defaultValue;
        }
    }

    /**
     * 求数组平均值
     */
    public static double arrayAverage(double[] array) {
        double total = 0;
        for (double v : array) {
            total += v;
        }
        return total / array.length;
    }

    /**
     * 求数组平均值
     */
    public static float arrayAverage(float[] array) {
        double total = 0;
        for (float v : array) {
            total += v;
        }
        return (float) (total / array.length);
    }

    /**
     * 根据起始位置，并且步长生成一个数组
     */
    public static float[] initArrayByStep(float start, float step, float end) {
        int length = (int) Math.floor(MathUtils.divide((float) (end - start), step)) + 1;
        float[] result = new float[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = start + i * step;
        }
        return result;
    }

    /**
     * 根据起始位置，并且步长生成一个数组
     */
    public static float[] initArrayByStepF(float start, float step, float end) {
        int length = (int) Math.floor((end - start) / step) + 1;
        float[] result = new float[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = start + i * step;
        }
        return result;
    }

    /**
     * 产生一个正态分布的随机数
     */
    public static float randomGauss(Random random) {
        return (float) random.nextGaussian();
    }

    /**
     * 两个float数相除
     */
    public static float divide(float num1, float num2) {
        BigDecimal decimal1 = new BigDecimal(String.valueOf(num1));

        BigDecimal decimal2 = new BigDecimal(String.valueOf(num2));

        return decimal1.divide(decimal2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    /**
     * 连接数组，生成一个新的数组
     */

    public static float[] concatArray(float[]... arrays) {
        int length = 0;
        for (float[] floats : arrays) {
            length += floats.length;
        }

        float[] array = new float[length];
        int start = 0;
        for (float[] tempArr : arrays) {
            System.arraycopy(tempArr, 0, array, start, tempArr.length);
            start += tempArr.length;

        }
        return array;
    }
}
