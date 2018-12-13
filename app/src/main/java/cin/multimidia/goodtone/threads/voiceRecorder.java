package cin.multimidia.goodtone.threads;

import android.media.MediaRecorder;

public class voiceRecorder extends Thread {

    private MediaRecorder audioRecorder;

    private boolean canceled;

    public voiceRecorder(String audioDirectory) {

        super();
        this.canceled = false;
        this.audioRecorder = new MediaRecorder();
        this.audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        this.audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        this.audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        this.audioRecorder.setAudioChannels(1);
        this.audioRecorder.setAudioSamplingRate(44100);
        this.audioRecorder.setAudioEncodingBitRate(96000);
        this.audioRecorder.setOutputFile(audioDirectory);

    }

    public void cancel(){
        this.canceled = true;
    }

    @Override
    public void run() {

        super.run();
        try {

            audioRecorder.prepare();
            audioRecorder.start();

            while (!this.canceled) {
                wait(125);
            }

            this.audioRecorder.stop();
            this.audioRecorder.release();
            this.audioRecorder = null;

        } catch (Exception e){
            e.printStackTrace();
        }

    }

}
