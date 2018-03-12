package fr.brochu.puppet.lipsync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlignmentStats {
    private int phoneCount = 0;
    private int nonAlignedCount = 0;
    private int insertedCount = 0;
    private PhoneDurationStat allPhonesStats;
    private HashMap<String, PhoneDurationStat> phoneStats;

    public void analyze(List<AlignedWord> alignedPhones) {
        // Alignment accuracy stats
        phoneCount = alignedPhones.size();
        nonAlignedCount = 0;
        insertedCount = 0;
        for (AlignedWord phone : alignedPhones) {
            if (phone.deleted) {
                nonAlignedCount++;
            }
            if (phone.inserted) {
                insertedCount++;
            }
        }

        // Phone duration stats
        allPhonesStats = new PhoneDurationStat();
        phoneStats = new HashMap<String, PhoneDurationStat>();

        for (AlignedWord w : alignedPhones) {
            if (w.wordResult != null) {
                long duration = w.wordResult.getTimeFrame().getEnd() - w.wordResult.getTimeFrame().getStart();
                allPhonesStats.pushDuration(duration);

                if (!phoneStats.containsKey(w.spelling)) {
                    phoneStats.put(w.spelling, new PhoneDurationStat());
                }
                phoneStats.get(w.spelling).pushDuration(duration);
            }
        }
    }

    public double getPhoneDeviation(String phone) {
        PhoneDurationStat phoneStat = getPhoneStat(phone);

        double deviation = phoneStat.getDeviation();
        if (phoneStat.getCount() <= 2) {
            deviation = Math.max(phoneStat.getDeviation(), (allPhonesStats.getDeviation() / allPhonesStats.getMean()) * phoneStat.getMean());
        }
        return deviation;
    }

    public double getPhoneMean(String phone) {
        return getPhoneStat(phone).getMean();
    }

    public PhoneDurationStat getPhoneStat(String phone) {
        PhoneDurationStat phoneStat = phoneStats.get(phone);
        if (phoneStat == null) {
            phoneStat = allPhonesStats;
        }
        return phoneStat;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, PhoneDurationStat> entry : phoneStats.entrySet()) {
            sb.append(
                String.format("[%s]:\n%s", entry.getKey(), entry.getValue())
            );
        }
        sb.append(String.format("All:\n%s\n", allPhonesStats));

        sb.append(
                String.format("Non aligned phones: %f.2%% (%d / %d), inserted: %d\n",
                        ((float)nonAlignedCount / (float)phoneCount)*100,
                        nonAlignedCount,
                        phoneCount,
                        insertedCount)
        );

        return sb.toString();
    }
}
