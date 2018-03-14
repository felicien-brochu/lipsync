package fr.brochu.puppet.lipsync;

import edu.cmu.sphinx.alignment.LongTextAligner;
import edu.cmu.sphinx.result.WordResult;
import edu.cmu.sphinx.util.TimeFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LipSync implements ProgressListener {
    private static final String ACOUSTIC_MODEL_PATH = "resource:/fr/brochu/puppet/lipsync/fr-fr/fr-fr";
    private static final String WORD_DICTIONARY_PATH = "resource:/fr/brochu/puppet/lipsync/fr-fr/fr.dict";
    private static final String PHONE_DICTIONARY_PATH = "resource:/fr/brochu/puppet/lipsync/fr-fr/fr-phone.dict";
    private static final String G2P_MODEL_PATH = "resource:/fr/brochu/puppet/lipsync/fr-fr/g2p/model.fst.ser";

    private final static double MAX_POSITIVE_DEVIATION = 1.2;
    private final static double MAX_NEGATIVE_DEVIATION = 1.;

    private final String wavPath;
    private final String transcriptPath;
    private Transcript transcript;
    private List<AlignedWord> alignedWords;
    private List<AlignedWord> alignedPhones;
    private AlignmentStats alignmentStats;
    private double progress = 0;
    private ProgressListener progressListener;
    private File resultFolder;


    public void exportPapagayo(String filePath) throws IOException {
        File file = new File(filePath);
        file.createNewFile();
        PapagayoExporter exporter = new PapagayoExporter(alignedWords, alignedPhones, this.transcript.getWordIndexes(), wavPath);
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(exporter.toString().getBytes("ISO-8859-1"));
        fileOutputStream.close();
        System.err.println(exporter.toString());
    }

    public LipSync(String wavPath, String transcriptPath, ProgressListener progressListener) throws IOException {
        this.wavPath = wavPath;
        this.transcriptPath = transcriptPath;
        this.progressListener = progressListener;
        this.alignmentStats = new AlignmentStats();

        readTranscript();
    }

    private void readTranscript() throws IOException {
        String transcriptText = readFile(this.transcriptPath);
        this.transcript = new Transcript(transcriptText, ACOUSTIC_MODEL_PATH, WORD_DICTIONARY_PATH, G2P_MODEL_PATH);
    }

    private static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, Charset.forName("UTF-8"));
    }

    public void sync() throws IOException {
        this.progressListener.onStart();

        createResultFolder();
        createLogFile();
        this.alignedWords = getAlignedPhones(this.transcript.getText(), WORD_DICTIONARY_PATH, new PartialProgressListener(this, 0, 30));
        System.err.printf("#####Words: %s\n", alignedWords);
        transcript.updateWordsPronunciation(alignedWords);
        this.alignedPhones = getAlignedPhones(this.transcript.toPhoneString(), PHONE_DICTIONARY_PATH, new PartialProgressListener(this, 30, 100));
        System.err.printf("#####Phones: %s\n", alignedPhones);

        dumpAlignmentState();
        alignmentStats.analyze(alignedPhones);
        dumpStatistics();

        fixIncompleteWords();
        fixWordBoundaries();
        fixMissingWords();

        createPapagayoFile();
        this.progressListener.onProgress(100);
        this.progressListener.onStop();
    }

    private void createPapagayoFile() {

        String papagayoFile = new File(wavPath).getName().replace(".wav", ".pgo");
        try {
            exportPapagayo(String.format("%s/%s", resultFolder.getAbsolutePath(), papagayoFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createResultFolder() {
        String folderPath = new File(wavPath).getAbsolutePath().replace(".wav", "_lipsync");
        resultFolder = new File(folderPath);
        if (!resultFolder.exists()) {
            resultFolder.mkdir();
        }
    }

    private void createLogFile() {
        String fileName = new File(wavPath).getName().replace(".wav", ".log");
        File logFile = new File(String.format("%s/%s", resultFolder.getAbsolutePath(), fileName));
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            System.setErr(new PrintStream(new FileOutputStream(logFile)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fixWordBoundaries() {
        int[] wordIndexes = this.transcript.getWordIndexes();
        for (int i = 0; i < alignedWords.size(); i++) {
            AlignedWord alignedWord = alignedWords.get(i);
            int wordLength = (i < alignedWords.size() - 1 ? wordIndexes[i + 1] : alignedPhones.size()) - wordIndexes[i];
            List<AlignedWord> phones = alignedPhones.subList(wordIndexes[i], wordIndexes[i] + wordLength);

            if (!alignedWord.deleted) {
                int start = -1, end = -1;
                for (int j = 0; j < wordLength; j++) {
                    if (!alignedPhones.get(wordIndexes[i]+j).ignored) {
                        if (start < 0) {
                            start = j;
                        }
                        end = j;
                    }
                }

                if (start >= 0) {
                    long wordStart = alignedWord.wordResult.getTimeFrame().getStart();
                    AlignedWord startPhone = alignedPhones.get(wordIndexes[i] + start);
                    long phoneStart = startPhone.getBestTimeFrame().getStart();

                    if (phoneStart != wordStart) {
                        // Fix word start boundary

                        System.err.printf("## Fix word start boundary: %s %s [%s] offset: %d\n",
                                alignedWord.spelling,
                                phones,
                                alignedWord.wordResult.getTimeFrame(),
                                phoneStart - wordStart);

                        if (phoneStart > wordStart) {
                            System.err.println("Fix:: move word start boundary");
                            alignedWord.timeFrame = new TimeFrame(phoneStart, alignedWord.wordResult.getTimeFrame().getEnd());
                            alignedWord.addTreatment(Treatment.MOVE_START);
                        }
                        else if (phoneStart < wordStart) {

                            // Try to expand the word
                            long previousWordEnd = -1;
                            if (i == 0) {
                                previousWordEnd = 0;
                            } else {
                                AlignedWord previousWord = alignedWords.get(i - 1);
                                if (!previousWord.deleted) {
                                    previousWordEnd = previousWord.getBestTimeFrame().getEnd();
                                }
                            }

                            if (previousWordEnd >= 0 && previousWordEnd < wordStart - 10) {
                                long newStart = Math.max(previousWordEnd + 10, phoneStart);
                                System.err.println("Fix:: Expand word start of " + (wordStart - newStart) + " ms");
                                alignedWord.timeFrame = new TimeFrame(newStart, alignedWord.getBestTimeFrame().getEnd());
                                wordStart = newStart;
                                alignedWord.addTreatment(Treatment.MOVE_START);
                            }

                            if (alignedWord.getBestTimeFrame().getStart() > phoneStart) {
                                System.err.println("Fix:: shrink or delete start phones");
                                for (int j = 0; j < wordLength; j++) {
                                    AlignedWord phone = alignedPhones.get(wordIndexes[i] + start + j);
                                    phone.addTreatment(Treatment.SHRINK_TO_START);
                                    if (phone.ignored) {
                                        continue;
                                    }
                                    long phoneEnd = phone.getBestTimeFrame().getEnd();
                                    long minDuration = 10;
                                    if (phoneEnd - minDuration >= wordStart) {
                                        // shrink this phone
                                        System.err.println("shrink phone [" + phone.toString() + "]");
                                        phone.timeFrame = new TimeFrame(wordStart, phoneEnd);
                                        break;
                                    } else {
                                        // delete this phone
                                        System.err.println("##### DELETE phone [" + phone.toString() + "]");
                                        phone.ignored = true;
                                    }
                                }
                            }
                        }
                    }
                }
                if (end >= 0) {
                    long wordEnd = alignedWord.wordResult.getTimeFrame().getEnd();
                    AlignedWord endPhone = alignedPhones.get(wordIndexes[i] + end);
                    long phoneEnd = endPhone.getBestTimeFrame().getEnd();

                    if (phoneEnd != wordEnd) {
                        // Fix word start boundary

                        System.err.printf("## Fix word end boundary: %s %s [%s] offset: %d\n",
                                alignedWord.spelling,
                                phones,
                                alignedWord.wordResult.getTimeFrame(),
                                wordEnd - phoneEnd);

                        if (phoneEnd > wordEnd) {
                            System.err.println("Fix:: move word end boundary");
                            long wordStart = alignedWord.getBestTimeFrame().getStart();
                            alignedWord.timeFrame = new TimeFrame(wordStart, phoneEnd);
                            alignedWord.addTreatment(Treatment.MOVE_END);
                        }
                        else if (phoneEnd < wordEnd) {
                            long nextWordStart = -1;
                            if (i == alignedWords.size()) {
                                nextWordStart = Long.MAX_VALUE;
                            } else {
                                AlignedWord nextWord = alignedWords.get(i + 1);
                                if (!nextWord.deleted) {
                                    nextWordStart = nextWord.getBestTimeFrame().getStart();
                                }
                            }

                            if (nextWordStart >= 0 && nextWordStart > wordEnd + 10) {
                                long newEnd = Math.min(nextWordStart - 10, phoneEnd);
                                System.err.println("Fix:: Expand word end of " + (newEnd - wordEnd) + " ms");
                                alignedWord.timeFrame = new TimeFrame(alignedWord.getBestTimeFrame().getStart(), newEnd);
                                wordEnd = newEnd;
                                alignedWord.addTreatment(Treatment.MOVE_END);
                            }

                            if (alignedWord.getBestTimeFrame().getEnd() < phoneEnd) {
                                System.err.println("Fix:: shrink or delete end phones");

                                for (int j = wordLength - 1 - start; j >= 0; j--) {
                                    AlignedWord phone = alignedPhones.get(wordIndexes[i] + start + j);
                                    phone.addTreatment(Treatment.SHRINK_TO_END);
                                    if (phone.ignored) {
                                        continue;
                                    }
                                    long phoneStart = phone.getBestTimeFrame().getStart();
                                    long minDuration = 10;
                                    if (phoneStart + minDuration <= wordEnd) {
                                        // shrink this phone
                                        System.err.println("#####shrink phone [" + phone.toString() + "]");
                                        phone.timeFrame = new TimeFrame(phoneStart, wordEnd);
                                        break;
                                    } else {
                                        // delete this phone
                                        System.err.println("#####delete phone [" + phone.toString() + "]");
                                        phone.ignored = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void fixMissingWords() {
        int[] wordIndexes = this.transcript.getWordIndexes();
        for (int i = 0; i < alignedWords.size(); i++) {
            AlignedWord alignedWord = alignedWords.get(i);
            int wordLength = (i < alignedWords.size() - 1 ? wordIndexes[i + 1] : alignedPhones.size()) - wordIndexes[i];
            List<AlignedWord> phones = alignedPhones.subList(wordIndexes[i], wordIndexes[i] + wordLength);

            if (alignedWord.deleted) {
                // Fix missing word
                System.err.printf("## Missing word: %s %s\n",
                        alignedWord.spelling,
                        phones);
                fixMissingWord(i);
                alignedWord.addTreatment(Treatment.MISSING_WORD);
            }
        }
    }

    private void fixMissingWord(int wordIndex) {
        long wordStart = -1, wordEnd = -1;
        if (wordIndex == 0) {
            wordStart = 0; 
        }
        else {
            for (int i = wordIndex - 1; i >= 0; i--) {
                if (!alignedWords.get(i).ignored && !alignedWords.get(i).deleted) {
                    wordStart = alignedWords.get(i).getBestTimeFrame().getEnd() + 10;
                    break;
                }
            }
        }
        if (wordIndex == alignedWords.size() - 1) {
            wordEnd = Long.MAX_VALUE;
        }
        else {
            for (int i = wordIndex + 1; i < alignedWords.size(); i++) {
                if (!alignedWords.get(i).ignored && !alignedWords.get(i).deleted) {
                    wordEnd = alignedWords.get(i).getBestTimeFrame().getStart();
                    break;
                }
            }
        }

        if (wordStart > 0 && wordEnd > 0 && wordStart < wordEnd) {
            int[] wordIndexes = this.transcript.getWordIndexes();
            int wordLength = (wordIndex < alignedWords.size() - 1 ? wordIndexes[wordIndex + 1] : alignedPhones.size()) - wordIndexes[wordIndex];

            List<Double> durations = new ArrayList<>();
            List<Double> deviations = new ArrayList<>();
            double totalDuration = 0;
            double deviationSum = 0;

            for (int i = wordIndexes[wordIndex]; i < wordIndexes[wordIndex] + wordLength; i++) {
                AlignedWord phone = alignedPhones.get(i);

                double mean = alignmentStats.getPhoneMean(phone.spelling);
                double deviation = alignmentStats.getPhoneDeviation(phone.spelling);
                double duration = mean;

                durations.add(duration);
                deviations.add(deviation);
                totalDuration += duration + 10;
                deviationSum += deviation;
            }

            double factor = ((double) (wordEnd - wordStart) - totalDuration) / deviationSum;
            factor = Math.max(MAX_NEGATIVE_DEVIATION, factor);
            factor = Math.min(MAX_POSITIVE_DEVIATION, factor);
            long lastTime = wordStart;
            int lastPhone = wordLength;

            for (int i = 0; i < wordLength; i++) {
                AlignedWord alignedPhone = alignedPhones.get(wordIndexes[wordIndex] + i);
                long phoneDuration = Math.round((durations.get(i) + deviations.get(i) * factor) / 10) * 10;
                long phoneEnd = lastTime + 10 + phoneDuration;
                if (phoneEnd <= wordEnd) {
                    alignedPhone.timeFrame = new TimeFrame(lastTime + 10, phoneEnd);
                    alignedPhone.addTreatment(Treatment.MISSING_WORD);
                    System.err.println("Fix:: add phone [" + alignedPhone.spelling + "] [" + alignedPhone.getBestTimeFrame() + "]");
                } else {
                    lastPhone = i;
                    break;
                }
                lastTime = phoneEnd;
            }

            if (lastPhone > 0) {
                alignedWords.get(wordIndex).timeFrame = new TimeFrame(alignedPhones.get(wordIndexes[wordIndex]).getBestTimeFrame().getStart(), lastTime);
            }

            for (int i = lastPhone; i < wordLength; i++) {
                AlignedWord alignedPhone = alignedPhones.get(wordIndexes[wordIndex] + i);
                alignedPhone.ignored = true;
                alignedPhone.addTreatment(Treatment.MISSING_WORD);
                System.err.println("#### Ignore phone [" + alignedPhone.spelling + "]");
            }
        } else {
            System.err.println("##### Wrong word boundary => ignoring word.");
            AlignedWord missingWord = alignedWords.get(wordIndex);
            missingWord.ignored = true;
            missingWord.addTreatment(Treatment.MISSING_WORD);
        }
    }

    private void dumpAlignmentState() {
        int[] wordIndexes = this.transcript.getWordIndexes();
        List<TranscriptWord> transcriptWords = transcript.getWords();

        for (int i = 0; i < this.alignedWords.size(); i++) {
            AlignedWord word = alignedWords.get(i);
            TranscriptWord transcriptWord = transcriptWords.get(i);
            String timeFrameString = "";
            if (word.wordResult != null) {
                timeFrameString = "[" + word.wordResult.getTimeFrame() + "]";
            }
            System.err.printf("%s %s %s\n",
                    word.deleted ? "-" : " ",
                    transcriptWord.toString(),
                    timeFrameString);

            for (int j = wordIndexes[i]; j < wordIndexes[i] + transcriptWord.getPhones().size(); j++) {
                AlignedWord phone = this.alignedPhones.get(j);
                timeFrameString = "";
                if (phone.wordResult != null) {
                    timeFrameString = "[" + phone.wordResult.getTimeFrame() + "]";
                }
                System.err.printf("\t%s %s %s\n",
                        phone.deleted ? "-" : " ",
                        phone.spelling,
                        timeFrameString);
            }
        }
    }

    private void fixIncompleteWords() {
        int[] wordIndexes = this.transcript.getWordIndexes();
        List<List<Integer>> patchedIntervals = new ArrayList<>();

        for (int i = 0; i < alignedWords.size(); i++) {
            AlignedWord alignedWord = alignedWords.get(i);
            if (alignedWord.deleted) {
                continue;
            }

            int wordLength = (i < alignedWords.size() - 1 ? wordIndexes[i + 1] : alignedPhones.size()) - wordIndexes[i];
            List<AlignedWord> phones = alignedPhones.subList(wordIndexes[i], wordIndexes[i] + wordLength);

            boolean incomplete = false;
            for (AlignedWord phone : phones) {
                if (phone.deleted) {
                    incomplete = true;
                }
            }

            if (incomplete) {
                System.err.printf("##Incomplete word: %s %s [%s]\n",
                        alignedWord.spelling,
                        phones,
                        alignedWord.wordResult.getTimeFrame());

                int intervalStart = -1;
                for (int j = 0; j < wordLength; j++) {
                    if (phones.get(j).deleted) {
                        if (intervalStart < 0) {
                            intervalStart = wordIndexes[i] + j;
                        }
                    }

                    if (intervalStart >= 0 && (!phones.get(j).deleted || j == wordLength - 1)) {
                        int intervalEnd = wordIndexes[i] + j;
                        if (phones.get(j).deleted && j == phones.size() - 1) {
                            intervalEnd++;
                        }

                        patchInterval(intervalStart, intervalEnd, wordIndexes[i], wordIndexes[i] + wordLength, i);

                        List<Integer> interval = new ArrayList<>();
                        interval.add(intervalStart);
                        interval.add(intervalEnd);
                        patchedIntervals.add(interval);

                        intervalStart = -1;
                    }
                }
            }
        }
    }

    private void patchInterval(int start, int end, int wordStart, int wordEnd, int wordIndex) {
        System.err.println("PATCH INTERVAL: " + start + ", " + end + ", " + wordStart + ", " + wordEnd);

        long startTime, endTime;
        boolean alignStart = false, alignEnd = false;
        AlignedWord alignedWord = alignedWords.get(wordIndex);

        if (start == wordStart) {
            startTime = alignedWord.wordResult.getTimeFrame().getStart();
        } else {
            alignStart = true;
            AlignedWord previousPhone = alignedPhones.get(start - 1);

            TimeFrame previousTimeFrame = previousPhone.wordResult.getTimeFrame();
            startTime = previousTimeFrame.getStart();
            start--;
        }
        if (end == wordEnd) {
            endTime = alignedWord.wordResult.getTimeFrame().getEnd() + 10;
        } else {
            alignEnd = true;
            AlignedWord nextPhone = alignedPhones.get(end);

            TimeFrame nextTimeFrame = nextPhone.wordResult.getTimeFrame();
            endTime = nextTimeFrame.getEnd() + 10;
            end++;
        }

        List<Double> durations = new ArrayList<>();
        List<Double> deviations = new ArrayList<>();
        double totalDuration = 0;
        double deviationSum = 0;

        for (int i = start; i < end; i++) {
            AlignedWord phone = alignedPhones.get(i);

            double mean = alignmentStats.getPhoneMean(phone.spelling);
            double deviation = alignmentStats.getPhoneDeviation(phone.spelling);
            double duration = mean;

            durations.add(duration);
            deviations.add(deviation);
            totalDuration += duration + 10;
            deviationSum += deviation;
        }

        double factor = ((double)(endTime - startTime) - totalDuration) / deviationSum;

        // We do not want phones to be this stretched (not exceed (mean + deviation))
        if (factor > MAX_POSITIVE_DEVIATION) {
            System.err.println("####### TOO BIG GAP");
            long totalCompactDuration = Math.round((totalDuration + MAX_POSITIVE_DEVIATION * deviationSum) / 10) * 10;

            if (alignStart && !alignEnd) {
                System.err.println("FIX:: Stretch maximum and align start");
                // compact aligned at start of the gap
                endTime = startTime + totalCompactDuration;
            } else if (alignEnd && !alignStart) {
                System.err.println("FIX:: Stretch maximum and align end");
                // compact aligned at end of the gap
                startTime = endTime - totalCompactDuration;
            } else if (!alignStart && !alignEnd || alignStart && alignEnd) {
                System.err.println("FIX:: Stretch maximum and align middle");
                // compact in the middle of the gap
                long padding = Math.round(((endTime - startTime) - totalCompactDuration) / 20) * 10;
                startTime = startTime + padding;
                endTime = endTime - padding;
            }

            totalDuration = 0;
            for (int i = 0; i < end - start; i++) {
                long duration = Math.round((durations.get(i) + MAX_POSITIVE_DEVIATION * deviations.get(i)) / 10) * 10;
                long phoneStart;
                if (i == 0) {
                    phoneStart = startTime;
                } else {
                    phoneStart = alignedPhones.get(start + i - 1).timeFrame.getEnd() + 10;
                }
                duration = Math.min(duration, endTime - 10 - phoneStart);
                AlignedWord alignedPhone = alignedPhones.get(start + i);
                alignedPhone.addTreatment(Treatment.INWORD_BIG_GAP);
                alignedPhone.timeFrame = new TimeFrame(phoneStart, phoneStart + duration);

                totalDuration += duration + 10;
                System.err.println(alignedPhones.get(start + i).spelling + " = " + duration + " [" + alignedPhones.get(start + i).timeFrame + "]");
            }
            System.err.println("totalDuration: " + totalDuration + ", gap: " + (endTime - startTime));
        }
        // We do not want phones to be this shrunk (not below (mean - deviation))
        else if (factor < - MAX_NEGATIVE_DEVIATION) {
            System.err.println("####### TOO SMALL GAP");
            // remove phonems
            long totalCompactDuration = Math.round((totalDuration - MAX_NEGATIVE_DEVIATION * deviationSum) / 10) * 10;
            if (alignStart || !alignEnd) {
                System.err.println("FIX:: Align start and remove end phones");
                // Remove end phones
                int chosenEnd = start;

                for (int i = end - 1; i >= start; i--) {
                    totalCompactDuration -= durations.get(i - start) - MAX_NEGATIVE_DEVIATION * deviations.get(i - start) - 10;
                    if (totalCompactDuration <= (endTime - startTime)) {
                        chosenEnd = i + 1;
                        break;
                    }
                }

                totalDuration = 0;
                for (int i = 0; i < end - start; i++) {
                    AlignedWord alignedPhone = alignedPhones.get(start + i);
                    alignedPhone.addTreatment(Treatment.INWORD_SMALL_GAP);

                    if (i < chosenEnd - start) {
                        long duration = Math.round((durations.get(i) - MAX_NEGATIVE_DEVIATION * deviations.get(i)) / 10) * 10;
                        long phoneStart;
                        if (i == 0) {
                            phoneStart = startTime;
                        } else {
                            phoneStart = alignedPhones.get(start + i - 1).timeFrame.getEnd() + 10;
                        }
                        duration = Math.min(duration, endTime - 10 - phoneStart);
                        alignedPhone.timeFrame = new TimeFrame(phoneStart, phoneStart + duration);
                        totalDuration += duration + 10;
                        System.err.println(alignedPhones.get(start + i).spelling + " = " + duration + " [" + alignedPhones.get(start + i).timeFrame + "]");
                    } else {
                        alignedPhones.get(start + i).ignored = true;
                    }
                }
                System.err.println("totalDuration: " + totalDuration + ", gap: " + (endTime - startTime));
            } else {
                System.err.println("FIX:: Align end and remove start phones");
                // Remove start phones

                int chosenStart = end;

                for (int i = start; i < end; i++) {
                    totalCompactDuration -= durations.get(i - start) - MAX_NEGATIVE_DEVIATION * deviations.get(i - start) - 10;
                    if (totalCompactDuration <= (endTime - startTime)) {
                        chosenStart = i + 1;
                        break;
                    }
                }

                totalDuration = 0;
                for (int i = end - start - 1; i >= 0; i--) {
                    AlignedWord alignedPhone = alignedPhones.get(start + i);
                    alignedPhone.addTreatment(Treatment.INWORD_SMALL_GAP);

                    if (i > chosenStart - start) {
                        long duration = Math.round((durations.get(i) - MAX_NEGATIVE_DEVIATION * deviations.get(i)) / 10) * 10;
                        long phoneEnd;
                        if (i == end - start - 1) {
                            phoneEnd = endTime - 10;
                        } else {
                            phoneEnd = alignedPhones.get(start + i + 1).timeFrame.getStart() - 10;
                        }
                        duration = Math.min(duration, phoneEnd - startTime);
                        alignedPhone.timeFrame = new TimeFrame(phoneEnd - duration, phoneEnd);
                        totalDuration += duration + 10;
                        System.err.println(alignedPhones.get(start + i).spelling + " = " + duration + " [" + alignedPhones.get(start + i).timeFrame + "]");
                    } else {
                        alignedPhones.get(start + i).ignored = true;
                    }
                }
                System.err.println("totalDuration: " + totalDuration + ", gap: " + (endTime - startTime));
            }
        }
        else {
            System.err.println("FIX:: Stretch or shrink phones");
            totalDuration = 0;
            for (int i = 0; i < end - start; i++) {
                long duration = Math.round((durations.get(i) + (deviations.get(i) * factor)) / 10) * 10;
                long phoneStart;
                if (i == 0) {
                    phoneStart = startTime;
                } else {
                    phoneStart = alignedPhones.get(start + i - 1).timeFrame.getEnd() + 10;
                }
                duration = Math.min(duration, endTime - 10 - phoneStart);
                AlignedWord alignedPhone = alignedPhones.get(start + i);
                alignedPhone.addTreatment(Treatment.INWORD_NORMAL_GAP);
                alignedPhone.timeFrame = new TimeFrame(phoneStart, phoneStart + duration);

                totalDuration += duration + 10;
                System.err.println(alignedPhones.get(start + i).spelling + " = " + duration + " [" + alignedPhones.get(start + i).timeFrame + "]");
            }
            System.err.println("totalDuration: " + totalDuration + ", gap: " + (endTime - startTime));
        }
    }

    private void dumpStatistics() {
        System.err.println(alignmentStats.toString());
    }

    private List<AlignedWord> getAlignedPhones(String transcript, String dictionaryPath, ProgressListener progressListener) throws IOException {
        URL audioUrl = new File(this.wavPath).toURI().toURL();


        PhoneticSpeechAligner aligner = new PhoneticSpeechAligner(ACOUSTIC_MODEL_PATH, dictionaryPath, G2P_MODEL_PATH, progressListener);


        List<WordResult> results = aligner.align(audioUrl, transcript);
        List<String> stringResults = new ArrayList<>();
        for (WordResult wr : results) {
            stringResults.add(wr.getWord().getSpelling());
        }

        LongTextAligner textAligner =
                new LongTextAligner(stringResults, 2);
        List<String> sentences = aligner.getTokenizer().expand(transcript);
        List<String> words = aligner.sentenceToWords(sentences);

        int[] aid = textAligner.align(words);
        List<AlignedWord> alignedPhones = new ArrayList<>();

        int lastId = -1;
        for (int i = 0; i < aid.length; ++i) {
            if (aid[i] == -1) {
                System.err.format("- %s\n", words.get(i));
                alignedPhones.add(new AlignedWord(words.get(i), null, true, false));
            } else {
                if (aid[i] - lastId > 1) {
                    for (WordResult result : results.subList(lastId + 1, aid[i])) {
                        System.err.format("+ %-25s [%s]\n", result.getWord().getSpelling(), result.getTimeFrame());
//                        alignedPhones.add(new AlignedWord(result.getWord().getSpelling(), result, false, true));
                    }
                }
                WordResult result = results.get(aid[i]);
                System.err.format("  %-25s %s [%s]\n",
                        result.getWord().getSpelling(),
                        result.getWord().getPronunciations()[0],
                        result.getTimeFrame());
                alignedPhones.add(new AlignedWord(result.getWord().getSpelling(), result, false, false));
                lastId = aid[i];
            }
        }

        if (lastId >= 0 && results.size() - lastId > 1) {
            for (WordResult result : results.subList(lastId + 1, results.size())) {
                System.err.format("+ %-25s [%s]\n", result.getWord().getSpelling(), result.getTimeFrame());
//                alignedPhones.add(new AlignedWord(result.getWord().getSpelling(), result, false, true));
            }
        }

        return alignedPhones;
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onProgress(double progress) {
        if (progress != this.progress) {
            this.progressListener.onProgress(progress);
            this.progress = progress;
        }
    }

    @Override
    public void onStop() {

    }

    private class PartialProgressListener implements ProgressListener {
        private final double startProgress;
        private final double endProgress;
        private final ProgressListener progressListener;
        private double progress = 0;

        public PartialProgressListener(ProgressListener progressListener, double startProgress, double endProgress) {
            this.progressListener = progressListener;
            this.startProgress = startProgress;
            this.endProgress = endProgress;
        }

        @Override
        public void onStart() {

        }

        @Override
        public void onProgress(double progress) {
            if (progress != this.progress) {
                this.progress = progress;
                this.progressListener.onProgress(startProgress + (endProgress - startProgress) * progress);
            }
        }

        @Override
        public void onStop() {

        }
    }

    public File getResultFolder() {
        return resultFolder;
    }

    public List<ReportWord> getReport() {
        int[] wordIndexes = this.transcript.getWordIndexes();
        List<ReportWord> report = new ArrayList<>();

        for (int i = 0; i < alignedWords.size(); i++) {
            AlignedWord alignedWord = alignedWords.get(i);
            long wordStart = -1, wordEnd = -1;
            if (!alignedWord.ignored) {
                TimeFrame timeFrame = alignedWord.getBestTimeFrame();
                wordStart = timeFrame.getStart();
                wordEnd = timeFrame.getEnd();
            }

            List<ReportPhone> phones = new ArrayList<>();
            int wordOut = i < alignedWords.size() - 1 ? wordIndexes[i + 1] : alignedPhones.size();
            for (int j = wordIndexes[i]; j < wordOut && !alignedWord.ignored; j++) {
                AlignedWord alignedPhone = alignedPhones.get(j);
                long phoneStart = -1, phoneEnd = -1;
                if (!alignedPhone.ignored) {
                    TimeFrame timeFrame = alignedPhone.getBestTimeFrame();
                    phoneStart = timeFrame.getStart();
                    phoneEnd = timeFrame.getEnd();
                }
                ReportPhone phone = new ReportPhone(alignedPhone.spelling, alignedPhone.treatments, phoneStart, phoneEnd, alignedPhone.ignored);
                phones.add(phone);
            }

            ReportWord word = new ReportWord(alignedWord.spelling, alignedWord.treatments, wordStart, wordEnd, alignedWord.ignored, phones);
            report.add(word);
        }
        return report;
    }

}
