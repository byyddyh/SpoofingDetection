package cn.byyddyh.spoofingdetection.process.dataModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Iono {
    public List<BigDecimal> alpha;
    public List<Long> beta;

    public Iono() {
        alpha = new ArrayList<>();
        beta = new ArrayList<>();
    }

    public Iono(List<BigDecimal> alpha, List<Long> beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public String toString() {
        return "Iono{" +
                "alpha=" + alpha +
                ", beta=" + beta +
                '}';
    }
}
