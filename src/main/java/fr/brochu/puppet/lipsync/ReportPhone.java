package fr.brochu.puppet.lipsync;

import java.util.List;

public class ReportPhone {
    public String name;
    public List<Treatment> treatments;
    public long start;
    public long end;
    public boolean ignored;

    public ReportPhone(String name, List<Treatment> treatments, long start, long end, boolean ignored) {
        this.name = name;
        this.treatments = treatments;
        this.start = start;
        this.end = end;
        this.ignored = ignored;
    }
}
