package fr.brochu.puppet.lipsync;

import java.util.List;

public class ReportWord extends ReportPhone {
    public List<ReportPhone> phones;

    public ReportWord(String name, List<Treatment> treatments, long start, long end, boolean ignored, List<ReportPhone> phones) {
        super(name, treatments, start, end, ignored);
        this.phones = phones;
    }
}
