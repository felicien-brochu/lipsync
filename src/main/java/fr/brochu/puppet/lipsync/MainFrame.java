package fr.brochu.puppet.lipsync;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class MainFrame extends JFrame implements ActionListener, ProgressListener {
    private final JLabel wavLabel;
    private final JLabel transcriptLabel;
    private final JButton syncButton;
    private final JProgressBar progressBar;
    private File wavFile;
    private File transcriptFile;

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
        syncButton = new JButton("Sync");
        syncButton.addActionListener(this);
        mainPanel.add(syncButton, BorderLayout.SOUTH);

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
    }

    private void startSyncing() throws IOException {
        syncButton.setEnabled(false);
        final LipSync lipSync = new LipSync(this.wavFile.getAbsolutePath(), this.transcriptFile.getAbsolutePath(), this);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    lipSync.sync();
                    String papagayoFile = wavFile.getAbsolutePath().replace(".wav", ".pgo");
                    lipSync.exportPapagayo(papagayoFile);

//                    // Open file
//                    Runtime runtime = Runtime.getRuntime();
//                    runtime.exec("Papagayo \"" + papagayoFile + "\"");

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
    }
}