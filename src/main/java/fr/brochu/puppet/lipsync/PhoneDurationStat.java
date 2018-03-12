package fr.brochu.puppet.lipsync;

import java.util.ArrayList;
import java.util.List;

public class PhoneDurationStat {
    private List<Long> durations = new ArrayList<>();
    private int count = 0;
    private double sum = 0;
    private double x2Sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    public void pushDuration(long duration) {
        Math.min(duration, 180);
        durations.add(duration);
        count++;
        sum += duration;
        x2Sum += duration * duration;
        min = Math.min(min, duration);
        max = Math.max(max, duration);
    }

    public double getMean() {
        return sum / (double)count;
    }

    public double getDeviation() {
        double mean = getMean();
        return Math.sqrt(x2Sum / count - mean * mean);
    }

    public int getCount() {
        return count;
    }

    public double getSum() {
        return sum;
    }

    public double getX2Sum() {
        return x2Sum;
    }

    public long getMin() {
        return min;
    }

    public long getMax() {
        return max;
    }

    public double getPositiveDeviation() {
        int count = 0;
        double mean = getMean();
        double deviationSum = 0;
        for (double duration : durations) {
            if (duration >= mean) {
                count++;
                double deviation = duration - mean;
                deviationSum += deviation * deviation;
            }
        }

        return Math.sqrt(deviationSum / count);
    }

    public int getPositiveDeviationCount() {
        int count = 0;
        double mean = getMean();
        for (Long duration : durations) {
            if (duration >= mean) {
                count++;
            }
        }
        return count;
    }

    public double getNegativeDeviation() {
        int count = 0;
        double mean = getMean();
        double deviationSum = 0;
        for (double duration : durations) {
            if (duration <= mean) {
                count++;
                double deviation = duration - mean;
                deviationSum += deviation * deviation;
            }
        }

        return Math.sqrt(deviationSum / count);
    }

    public int getNegativeDeviationCount() {
        int count = 0;
        double mean = getMean();
        for (Long duration : durations) {
            if (duration <= mean) {
                count++;
            }
        }
        return count;
    }

    public String toString() {
        return String.format(
                "\tcount:     %d\n" +
                        "\tmean:       %.2f ms\n" +
                        "\tdeviation:  %.2f ms\n" +
                        "\t+deviation: %.2f ms (%d)\n" +
                        "\t-deviation: %.2f ms (%d)\n" +
                        "\tmin:        %d ms\n" +
                        "\tmax:        %d ms\n",
                count,
                getMean(),
                getDeviation(),
                getPositiveDeviation(), getPositiveDeviationCount(),
                getNegativeDeviation(), getNegativeDeviationCount(),
                min,
                max);
    }
}
