package fr.brochu.puppet.lipsync;

import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.util.List;

public class ReportWord extends ReportPhone {
    public List<ReportPhone> phones;

    public ReportWord(String name, List<Treatment> treatments, long start, long end, boolean ignored, List<ReportPhone> phones) {
        super(name, treatments, start, end, ignored);
        this.phones = phones;
    }

    @Override
    public Color getColor() {
        int maxLevel = 0;
        for (Treatment treatment : treatments) {
            int level;
            switch (treatment) {
                case MISSING_WORD:
                    level = 2;
                    break;
                case SHRINK_TO_END:
                case MOVE_END:
                case MOVE_START:
                case INWORD_SMALL_GAP:
                case INWORD_BIG_GAP:
                case SHRINK_TO_START:
                case INWORD_NORMAL_GAP:
                    level = 1;
                    break;
                default:
                    level = 0;
            }
            maxLevel = Math.max(maxLevel, level);
        }

        Color color = Color.GRAY;

        switch (maxLevel) {
            case 0:
                for (ReportPhone reportPhone : phones) {
                    if (!reportPhone.treatments.isEmpty()) {
                        color = new Color(40, 155, 255);
                        break;
                    }
                }
                break;
            case 1:
                color = new Color(255, 125, 0);
                break;
            case 2:
                color = Color.RED;
        }
        return color;
    }
}
