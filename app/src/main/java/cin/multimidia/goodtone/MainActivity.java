package cin.multimidia.goodtone;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.File;

import cin.multimidia.goodtone.assets.audioAnalyzer;
import cin.multimidia.goodtone.threads.voiceRecorder;

public class MainActivity extends AppCompatActivity {

    private ViewFlipper mainContainer;
    private FloatingActionButton infoButton;

    private FloatingActionButton recordButton;
    private FloatingActionButton returnButton;
    private FloatingActionButton listenButton;
    private FloatingActionButton agreedButton;

    private Chronometer chronometer;
    private String audioDirectory;
    private TextView percentageOfConfidence;
    private MediaPlayer mediaPlayer;
    private AlertDialog.Builder optionsDialog;

    private boolean listening;

    private voiceRecorder voiceRecorder;

    private File audioFile;

    private audioAnalyzer analyzer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        initializeVariables();

        initializeProperties();

    }

    private void checkPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (!(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {

                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                finish();

            }

        }

    }

    private void initializeVariables() {

        this.mainContainer = (ViewFlipper) findViewById(R.id.flipper);
        this.infoButton = (FloatingActionButton) findViewById(R.id.info_btn);

        this.recordButton = (FloatingActionButton) findViewById(R.id.voice_btn);
        this.returnButton = (FloatingActionButton) findViewById(R.id.close_btn);
        this.listenButton = (FloatingActionButton) findViewById(R.id.play_btn);
        this.agreedButton = (FloatingActionButton) findViewById(R.id.check_btn);

        this.chronometer = (Chronometer) findViewById(R.id.chronometer);
        this.audioDirectory = null;
        this.percentageOfConfidence = (TextView) findViewById(R.id.percentageOfConfidence);
        this.mediaPlayer = null;

        this.listening = false;

    }

    private void initializeProperties() {

        this.chronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometerChanged) {
                if (chronometerChanged.getText().equals("03:00")) {
                    stopForced();
                }
            }
        });

        this.infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.optionsDialog = new AlertDialog.Builder(MainActivity.this);
                MainActivity.this.optionsDialog.setTitle("Info");
                MainActivity.this.optionsDialog.setMessage("Confidence is everything in a speech, but how to measure it?\nOne of the most classic" +
                        " ways is seeing if your voice was constant and maintained a soft pattern, this app will help you with that.\n" +
                        "Press the button to stop recording your voice.\nThe maximum time is three minutes," +
                        " when you stop recording or reached the maximum, you can start analyzing your voice to see if your speech" +
                        " was constant enough to pass confidence to your audience, if not, we will alter your audio so you can hear" +
                        " yourself in a more confident way, so you can practice!");
                MainActivity.this.optionsDialog.setIcon(R.drawable.ic_alert_24dp);
                MainActivity.this.optionsDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                MainActivity.this.optionsDialog.show();
            }
        });

        this.recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchVoiceButton();
            }
        });

        this.returnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainContainer.setDisplayedChild(0);
            }
        });

    }

    private void startChronometer() {

        this.chronometer.setBase(SystemClock.elapsedRealtime());
        this.chronometer.start();

    }

    private void stopChronometer() {

        this.chronometer.stop();

    }

    private void audioAssetsReset() {

        this.audioDirectory = null;
        this.audioFile.delete();

    }

    private void passContentToAudioFile() {

        this.audioFile = new File(this.audioDirectory);
        this.analyzer = new audioAnalyzer(this.audioFile);

    }

    //NEED FURTHER IMPLEMENTATION OF ELSE
    private void nextStep() {
        passContentToAudioFile();
        if (this.audioDirectory == null){
            this.optionsDialog = new AlertDialog.Builder(this);
            this.optionsDialog.setTitle("Talk something.");
            this.optionsDialog.setMessage("You didn't talk or your voice wasn't recognized, try again.");
            this.optionsDialog.setIcon(R.drawable.ic_alert_24dp);
            this.optionsDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            this.optionsDialog.show();
        } else {
            this.mainContainer.setDisplayedChild(3);
            Toast.makeText(MainActivity.this,R.string.text_toast_wait_while_process,Toast.LENGTH_LONG).show();
            this.analyzer.generateFileSamples();
            while (!this.analyzer.isFileRead()){

            }
            this.analyzer.loadModel(getAssets());
            this.percentageOfConfidence.setText(this.analyzer.getPercentage()+" de "+this.analyzer.getClassifier());
            this.mainContainer.setDisplayedChild(1);
        }
    }

    private void stopNormal() {
        this.listening=false;
        this.voiceRecorder.cancel();
        stopChronometer();
        this.infoButton.setVisibility(View.VISIBLE);
        this.recordButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorBlack)));
    }

    private void stopFriendly() {
        stopNormal();
        this.optionsDialog = new AlertDialog.Builder(this);
        this.optionsDialog.setTitle("Next Step");
        this.optionsDialog.setMessage("You've stopped the recording.\nAre you satisfied with what you've said?\nCan we analyze it?");
        this.optionsDialog.setIcon(R.drawable.ic_alert_24dp);
        this.optionsDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.nextStep();
                dialog.dismiss();
            }
        });
        this.optionsDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.audioAssetsReset();
                dialog.dismiss();
            }
        });
        this.optionsDialog.show();
    }

    private void stopForced() {
        stopNormal();
        this.optionsDialog = new AlertDialog.Builder(this);
        this.optionsDialog.setTitle("Reached Limit");
        this.optionsDialog.setMessage("Maximum amount of time to talk was reached.\nDo you want to analyze it anyway?");
        this.optionsDialog.setIcon(R.drawable.ic_alert_24dp);
        this.optionsDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.nextStep();
                dialog.dismiss();
            }
        });
        this.optionsDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.audioAssetsReset();
                dialog.dismiss();
            }
        });
        this.optionsDialog.show();
    }

    private void switchVoiceButton() {
        if (this.listening) {
            stopFriendly();
        } else {
            this.voiceRecorder = new voiceRecorder(this.audioDirectory);
            this.infoButton.setVisibility(View.INVISIBLE);
            this.recordButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
            this.listening = true;
            this.voiceRecorder.run();
            startChronometer();
            Toast.makeText(MainActivity.this,R.string.text_toast_speech_start,Toast.LENGTH_LONG).show();
        }
    }

}
