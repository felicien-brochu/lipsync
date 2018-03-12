package fr.brochu.puppet.lipsync;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileTransferHandler extends TransferHandler {
    private final FileTransferListener listener;

    public FileTransferHandler(FileTransferListener listener) {
        this.listener = listener;
    }

    public boolean canImport(TransferHandler.TransferSupport info) {
        if (!info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return false;
        }
        return true;
    }

    public boolean importData(TransferHandler.TransferSupport support) {
        if (!canImport(support))
            return false;

        Transferable data = support.getTransferable();
        List<File> files = null;

        try {
            files = (List<File>) data.getTransferData(DataFlavor.javaFileListFlavor);
        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.listener.onFileTransfer(files.get(0));
//    JLabel lab = (JLabel) support.getComponent();
//        setWavFile(files.get(0));
//        lab.setText(files.get(0).getAbsolutePath());

        return false;
    }
}
