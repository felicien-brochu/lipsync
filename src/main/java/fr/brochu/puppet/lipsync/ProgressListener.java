package fr.brochu.puppet.lipsync;

public interface ProgressListener {
    public void onStart();
    public void onProgress(double progress);
    public void onStop();
}
