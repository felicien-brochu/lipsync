package fr.brochu.puppet.lipsync;

import java.awt.*;
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

    public Color getColor() {
        Color color = Color.GRAY;
        if (treatments.size() > 0) {
            color = new Color(255, 125, 0);
        }
        return color;
    }

    @Override
    public String toString() {
        String deleteString = ignored ? "- " : "";
        return String.format("%s[%d:%d] %s   %s", deleteString, start, end, name, treatments);
    }
}
