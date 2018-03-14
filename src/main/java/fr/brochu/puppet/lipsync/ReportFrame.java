package fr.brochu.puppet.lipsync;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.List;

public class ReportFrame extends JFrame {
    private final List<ReportWord> report;
    private JTree tree;

    public ReportFrame(List<ReportWord> report) {
        this.report = report;

        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Words");
        this.createReportTree(top);
        tree = new JTree(top);
        tree.setCellRenderer(new WordCellRenderer());
        JScrollPane treeView = new JScrollPane(tree);

        this.setContentPane(treeView);
        this.setTitle("Lip Sync Report");
        this.setSize(800, 600);
        this.setResizable(true);

        this.setVisible(true);
    }

    private void createReportTree(DefaultMutableTreeNode top) {
        for (ReportWord word : report) {
            DefaultMutableTreeNode wordNode = new DefaultMutableTreeNode(word);
            for (ReportPhone phone : word.phones) {
                wordNode.add(new DefaultMutableTreeNode(phone));
            }
            top.add(wordNode);
        }
    }
}

class WordCellRenderer extends DefaultTreeCellRenderer {

    public WordCellRenderer() {
        super();
        setFont(new Font(null, Font.BOLD, 12));
    }

    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus) {
        super.getTreeCellRendererComponent(
                tree, value, sel,
                expanded, leaf, row,
                hasFocus);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node.getUserObject() instanceof ReportPhone) {
            ReportPhone reportPhone = (ReportPhone) node.getUserObject();
            setForeground(reportPhone.getColor());
//            if (reportPhone.treatments.size() > 0) {
////                setBackground(new Color(255, 12, 12));
////                setBackgroundNonSelectionColor(Color.ORANGE);
//                setForeground(Color.RED);
//                setText("YOLO");
//            }
        }

        return this;
    }
}