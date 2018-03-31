package fr.brochu.puppet.lipsync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PapagayoExporter {
    private List<AlignedWord> alignedWords;
    private List<AlignedWord> alignedPhones;
    private int[] wordIndexes;
    private String wavPath;

    private final static Map<String, String> phoneShapeMap = new HashMap<>();
    private final static int FPS = 100;

    static {
        phoneShapeMap.put("aa", "AI");
        phoneShapeMap.put("ai", "E");
        phoneShapeMap.put("an", "E");
        phoneShapeMap.put("au", "O");
        phoneShapeMap.put("bb", "MBP");
        phoneShapeMap.put("ch", "etc");
        phoneShapeMap.put("dd", "etc");
        phoneShapeMap.put("ee", "E");
        phoneShapeMap.put("ei", "E");
        phoneShapeMap.put("eu", "O");
        phoneShapeMap.put("ff", "FV");
        phoneShapeMap.put("gg", "etc");
        phoneShapeMap.put("gn", "etc");
        phoneShapeMap.put("ii", "E");
        phoneShapeMap.put("in", "E");
        phoneShapeMap.put("jj", "etc");
        phoneShapeMap.put("kk", "etc");
        phoneShapeMap.put("ll", "L");
        phoneShapeMap.put("mm", "MBP");
        phoneShapeMap.put("nn", "etc");
        phoneShapeMap.put("oe", "E");
        phoneShapeMap.put("on", "O");
        phoneShapeMap.put("oo", "O");
        phoneShapeMap.put("ou", "U");
        phoneShapeMap.put("pp", "MBP");
        phoneShapeMap.put("rr", "etc");
        phoneShapeMap.put("ss", "etc");
        phoneShapeMap.put("tt", "etc");
        phoneShapeMap.put("un", "E");
        phoneShapeMap.put("uu", "O");
        phoneShapeMap.put("uy", "E");
        phoneShapeMap.put("vv", "FV");
        phoneShapeMap.put("ww", "WQ");
        phoneShapeMap.put("yy", "etc");
        phoneShapeMap.put("zz", "etc");
    }

    public PapagayoExporter(List<AlignedWord> alignedWords, List<AlignedWord> alignedPhones, int[] wordIndexes, String wavPath) {
        this.alignedWords = alignedWords;
        this.alignedPhones = alignedPhones;
        this.wordIndexes = wordIndexes;
        this.wavPath = wavPath;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb
                .append("lipsync version 1\n")
                .append(wavPath + "\n")
                .append(FPS)
                .append("\n");

        // Get last timing
        long lastTime = -1;
        for (int i = alignedPhones.size() - 1; i >= 0; i--) {
            if (!alignedPhones.get(i).ignored) {
                lastTime = alignedPhones.get(i).getBestTimeFrame().getEnd();
                break;
            }
        }

        sb
                .append(timeToFrame(lastTime))
                .append("\n");

        sb
                .append("1\n")
                .append("\tVoice 1\n");

        int wordCount = 0;
        StringBuilder transcript = new StringBuilder();
        for (AlignedWord word : alignedWords) {
            if (!word.ignored) {
                transcript.append(word.spelling + " ");
                wordCount++;
            }
        }

        sb
                .append("\t" + transcript + "\n")
                .append("\t1\n")
                .append("\t\t" + transcript + "\n")
                .append("\t\t0\n")
                .append("\t\t" + timeToFrame(lastTime) + "\n")
                .append("\t\t" + wordCount + "\n");

        for (int i = 0; i < alignedWords.size(); i++) {
            if (!alignedWords.get(i).ignored) {
                sb.append(getWordStructure(i));
            }
        }

        return sb.toString();
    }

    private static long timeToFrame(long time) {
        return (long) (time / 1000d * FPS);
    }

    private String getWordStructure(int wordIndex) {
        AlignedWord alignedWord = alignedWords.get(wordIndex);
        int phoneCount = 0;
        int wordLength = (wordIndex < alignedWords.size() - 1 ? wordIndexes[wordIndex + 1] : alignedPhones.size()) - wordIndexes[wordIndex];
        StringBuilder phoneSb = new StringBuilder();
        for (int i = 0; i < wordLength; i++) {
            AlignedWord phone = alignedPhones.get(wordIndexes[wordIndex] + i);
            phoneCount++;
            phoneSb.append("\t\t\t\t" + getPhoneStart(wordIndexes[wordIndex], i) + " " + phoneToMouthShape(phone.spelling) + "\n");
        }
        StringBuilder sb = new StringBuilder();
        sb
                .append(String.format("\t\t\t%s %d %d %d\n",
                        alignedWord.spelling,
                        timeToFrame(alignedWord.getBestTimeFrame().getStart()),
                        timeToFrame(alignedWord.getBestTimeFrame().getEnd()),
                        phoneCount))
                .append(phoneSb);

        return  sb.toString();
    }

    private long getPhoneStart(int wordIndex, int phoneIndex) {
        AlignedWord phone = alignedPhones.get(wordIndex + phoneIndex);
        long start = -1;
        if (!phone.ignored) {
            start = timeToFrame(phone.getBestTimeFrame().getStart());
        } else {
            for (int i = phoneIndex; i >= 0; i--) {
                AlignedWord prevPhone = alignedPhones.get(wordIndex + i);
                if (!prevPhone.ignored) {
                    start = timeToFrame(prevPhone.getBestTimeFrame().getEnd());
                    break;
                }
            }
            if (start < 0) {
                AlignedWord alignedWord = alignedWords.get(wordIndex);
                start = timeToFrame(alignedWord.getBestTimeFrame().getStart());
            }
        }
        return start;
    }

    /**
     * See phoneShapeMap
     */
    private static String phoneToMouthShape(String phone) {
        return phoneShapeMap.get(phone);
    }
}
