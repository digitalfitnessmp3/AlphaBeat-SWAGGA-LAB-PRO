package com.digitalfitnessmp3.app.audio;

import java.util.Arrays;

public final class BpmEngine {

    private BpmEngine() {}

    public static double calculateBpm(long[] beatTimesMs) {
        if (beatTimesMs == null || beatTimesMs.length < 3) {
            return 0;
        }

        long[] intervals = new long[beatTimesMs.length - 1];

        for (int i = 1; i < beatTimesMs.length; i++) {
            long interval = beatTimesMs[i] - beatTimesMs[i - 1];

            if (interval <= 0) {
                return 0;
            }

            intervals[i - 1] = interval;
        }

        Arrays.sort(intervals);

        double median;
        int middle = intervals.length / 2;

        if (intervals.length % 2 == 0) {
            median = (intervals[middle - 1] / 2.0)
                   + (intervals[middle] / 2.0);
        } else {
            median = intervals[middle];
        }

        if (median <= 0) {
            return 0;
        }

        return Math.round((60000.0 / median) * 10.0) / 10.0;
    }
}
