package fr.brochu.puppet.lipsync;

import edu.cmu.sphinx.result.WordResult;
import edu.cmu.sphinx.util.TimeFrame;

import java.util.ArrayList;
import java.util.List;

public class AlignedWord {
    public String spelling;
    public WordResult wordResult;
    public TimeFrame timeFrame;
    public boolean deleted = false;
    public boolean inserted = false;
    public boolean ignored = false;
    public List<Treatment> treatments = new ArrayList<>();

    public AlignedWord(String spelling, WordResult wordResult, boolean deleted, boolean inserted) {
        this.spelling = spelling;
        this.wordResult = wordResult;
        this.deleted = deleted;
        this.inserted = inserted;
    }

    public void addTreatment(Treatment treatment) {
        this.treatments.add(treatment);
    }

    public TimeFrame getBestTimeFrame() {
        if (timeFrame != null)
            return timeFrame;

        if (wordResult != null)
            return wordResult.getTimeFrame();

        return null;
    }

    public String toString() {
        return spelling;
    }
}