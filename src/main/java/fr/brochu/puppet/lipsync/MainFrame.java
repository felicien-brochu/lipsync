package fr.brochu.puppet.lipsync;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class MainFrame extends JFrame implements ActionListener, ProgressListener {
    private final JLabel wavLabel;
    private final JLabel transcriptLabel;
    private final JButton syncButton;
    private final JButton resultFolderButton;
    private final JProgressBar progressBar;
    private final JButton reportButton;
    private File wavFile;
    private File transcriptFile;
    private LipSync lipSync;

    public MainFrame() {
        super();

        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(800, 600);
        this.setTitle("LipSync");

        JPanel mainPanel = new JPanel();
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridLayout(1, 2));
        mainPanel.setLayout(new BorderLayout());
        wavLabel = new JLabel("16 Kb mono .WAV file");
        wavLabel.setTransferHandler(new FileTransferHandler(new FileTransferListener() {
            @Override
            public void onFileTransfer(File file) {
                onWavFileTransfer(file);
            }
        }));
        centerPanel.add(wavLabel);

        transcriptLabel = new JLabel("Transcript file");
        transcriptLabel.setTransferHandler(new FileTransferHandler(new FileTransferListener() {
            @Override
            public void onFileTransfer(File file) {
                onTranscriptFileTransfer(file);
            }
        }));
        centerPanel.add(transcriptLabel);


        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        mainPanel.add(progressBar, BorderLayout.NORTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BorderLayout());
        syncButton = new JButton("Sync");
        syncButton.addActionListener(this);
        buttonsPanel.add(syncButton, BorderLayout.CENTER);


        JPanel resultPanel = new JPanel();
        resultPanel.setLayout(new GridLayout(2, 1));

        resultFolderButton = new JButton("Open Folder");
        resultFolderButton.setEnabled(false);
        resultFolderButton.addActionListener(this);
        resultPanel.add(resultFolderButton);

        reportButton = new JButton("Report");
        reportButton.setEnabled(false);
        reportButton.addActionListener(this);
        resultPanel.add(reportButton);

        buttonsPanel.add(resultPanel, BorderLayout.EAST);

        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        this.setContentPane(mainPanel);
        this.setVisible(true);
    }

    private void onTranscriptFileTransfer(File file) {
        this.transcriptFile = file;
        transcriptLabel.setText(file.getAbsolutePath());
    }

    private void onWavFileTransfer(File file) {
        this.wavFile = file;
        wavLabel.setText(file.getAbsolutePath());
    }

    public static void main(String[] args) {
        new MainFrame();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == syncButton && this.wavFile != null && this.transcriptFile != null) {
            try {
                startSyncing();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        else if (e.getSource() == resultFolderButton) {
            openResultFolder();
        }
        else if (e.getSource() == reportButton) {
            showReportWindow();
        }
    }

    private void openResultFolder() {
        try {
            Desktop.getDesktop().open(this.lipSync.getResultFolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startSyncing() throws IOException {
        syncButton.setEnabled(false);
        lipSync = new LipSync(this.wavFile.getAbsolutePath(), this.transcriptFile.getAbsolutePath(), this);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    lipSync.sync();

                    java.awt.Toolkit.getDefaultToolkit().beep();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread t = new Thread(runnable);
        t.start();
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onProgress(double progress) {
        progressBar.setValue((int)progress);
    }

    @Override
    public void onStop() {
        syncButton.setEnabled(true);
        resultFolderButton.setEnabled(true);
        reportButton.setEnabled(true);
        showReportWindow();
    }

    private void showReportWindow() {
        new ReportFrame(lipSync.getReport());
    }
}