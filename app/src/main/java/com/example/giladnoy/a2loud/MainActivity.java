package com.example.giladnoy.a2loud;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.media.MediaRecorder;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    protected static final String EXTRA_MESSAGE = "com.example.giladnoy.a2Loud.MESSAGE";
    protected MediaRecorder recorder;
    boolean recording = false;
    protected TextView dbValue;
    Timer timer;
    public boolean vibration;
    protected boolean mute;
    public int limitValue;
    protected Vibrator vibe;
    protected static int vibeTime;
    protected MediaPlayer audio;
    protected float volume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbValue = (TextView) findViewById(R.id.dbValue);
        dbValue.setText(String.valueOf(0));
        vibe = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        //microphone permission request at startup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == -1)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        audio = new MediaPlayer();
        mute = true;
        setValues();
    }

    //Setting the configuration values after OnCreate or Resume
    private void setValues(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        vibration = prefs.getBoolean("Vibration",true);
        limitValue = Integer.valueOf(prefs.getString("editMax","80"));
        vibeTime = Integer.valueOf(prefs.getString("editVibe","200"));
        try {
            if (prefs.getString("editSound","").equals("")) {
                //When "Silent" notification is chosen or at the first launch of the app
                mute = true;
            }
            else {
                audio.reset();
                audio.setDataSource(this,
                        Uri.parse(prefs.getString("editSound", "")));
                audio.prepare();
                //Making sure the volume is in the right range
                volume = Math.min(100, Integer.valueOf(prefs.getString("editVol","100")));
                volume = (Math.max(0, volume))/100; //volume between 0.0 - 1.0
                audio.setVolume(volume, volume);
                mute = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Change to configuration activity
    public void changeSettings(View view) {
        Intent intent = new Intent(this, PrefActivity.class);
        startActivity(intent);
    }

    //Pressing the START button to begin listening
    public void onStartClick(View view) { startWorking(); }

    public void startWorking() {
        if (!recording) {
            try {
                timer = new Timer();
            } catch (IllegalStateException e) {e.printStackTrace();}
            startRecording(0);
            timer.scheduleAtFixedRate(new RecordTask(), 0, 500);
        }
    }

    public void onStopClick(View view) {
        if (audio.isPlaying()) {
            audio.pause();
            audio.seekTo(0);
        }
        if (recording) {
            timer.cancel();
            recorder.reset();
            recorder.release();
            dbValue.setText(String.valueOf(0));
            recording = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (audio.isPlaying()) {
            audio.pause();
            audio.seekTo(0);
        }
        if (recording) {
            recording = false;
            timer.cancel();
            recorder.reset();
            recorder.release();
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (audio.isPlaying()) {
            audio.pause();
            audio.seekTo(0);
        }
        if (recording) {
            recording = false;
            timer.cancel();
            recorder.reset();
            recorder.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setValues();
    }

    //Getting the last highest DB since the last call to getDB (or 0 if it is the first call)
    public static int getDB(MediaRecorder rec){
        return Math.max(0,
                (int)(20 * Math.log10(Math.abs(rec.getMaxAmplitude())))); //Formula to convert amp level to DB
    }

    //The process of starting the recording after the delay
    protected void startRecording(long delay) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                recorder = new MediaRecorder();
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setOutputFile("/dev/null");
                try {
                    recorder.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                recorder.start();
                recording = true;
            }
        },delay);
    }

    //The runnable task of checking the noise and responding with either a vibration or sound notification
    private class RecordTask extends TimerTask {
        public void run() {
            runOnUiThread(new Runnable() {
                public void run() {
                    int preValue = Integer.valueOf(dbValue.getText().toString());
                    int currValue = MainActivity.getDB(recorder);
                    dbValue.setText(String.valueOf(currValue));
                    RotateAnimation movement = new RotateAnimation
                            ((float)(preValue*2.5),(float)(currValue*2.5),
                            RotateAnimation.ABSOLUTE,290,RotateAnimation.ABSOLUTE,90);
                    movement.setDuration(500);
                    movement.setFillAfter(true);
                    ImageView needle = (ImageView) findViewById(R.id.needle);
                    needle.startAnimation(movement);
                    if (currValue >= limitValue) {
                        if (!mute || vibration) {
                            recorder.reset();
                            if (mute) { //vibration and no sound
                                vibe.vibrate(vibeTime);
                                startRecording(vibeTime);
                                return;
                            }
                            if (!vibration) { //sound and no vibration
                                audio.start();
                                startRecording(audio.getDuration());
                                return;
                            }
                            //vibration and sound
                            vibe.vibrate(vibeTime);
                            audio.start();
                            startRecording(Math.max(vibeTime, audio.getDuration()));
                            return;
                        }
                    }
                }
            });
        }
    }
}
