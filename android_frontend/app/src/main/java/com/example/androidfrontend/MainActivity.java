package com.example.androidfrontend;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import java.util.ArrayList;
import java.util.List;

/**
 * PUBLIC_INTERFACE
 * Main activity for hearing range measurement app.
 * Provides onboarding, guided hearing test, tone generation, user response, and displays results.
 */
public class MainActivity extends AppCompatActivity {
    // Test frequencies in Hz for hearing test (standard audiometry-like range)
    private static final int[] FREQUENCY_ARRAY = {125, 250, 500, 1000, 2000, 4000, 8000, 12000, 16000};
    private static final int SAMPLE_RATE = 44100;
    private static final float TONE_DURATION_SEC = 2f;
    private static final int REQUEST_CODE_RECORD_AUDIO = 1;

    private LinearLayout mainContainer;
    private TextView onboardingText;
    private MaterialButton startTestBtn;
    private LinearLayout testSection;
    private TextView freqText;
    private MaterialButton playToneBtn;
    private LinearLayout responseBtns;
    private MaterialButton heardBtn, notHeardBtn;
    private LinearLayout resultSection;
    private TextView resultText;
    private MaterialButton restartBtn;
    private ProgressBar progressBar;

    private int currentStep = 0;
    private final List<Integer> heardFrequencies = new ArrayList<>();
    private boolean testStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Uses ConstraintLayout as base

        // Bind Views (will be set in improved activity_main.xml)
        mainContainer = findViewById(R.id.mainVerticalLayout);
        // Hide keyboard/bar if present, set light theme colors if needed

        setupOnboarding();

        // Request audio permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestAudioPermission();
        }
    }

    // Setup onboarding/instruction section
    private void setupOnboarding() {
        onboardingText = findViewById(R.id.textOnboarding);
        startTestBtn = findViewById(R.id.btnStartTest);
        testSection = findViewById(R.id.testSection);
        freqText = findViewById(R.id.textFreq);
        playToneBtn = findViewById(R.id.btnPlayTone);
        responseBtns = findViewById(R.id.responseBtns);
        heardBtn = findViewById(R.id.btnHeard);
        notHeardBtn = findViewById(R.id.btnNotHeard);
        resultSection = findViewById(R.id.resultSection);
        resultText = findViewById(R.id.textResult);
        restartBtn = findViewById(R.id.btnRestart);
        progressBar = findViewById(R.id.testProgressBar);

        testSection.setVisibility(View.GONE);
        resultSection.setVisibility(View.GONE);

        onboardingText.setText(getResources().getString(R.string.onboarding_msg));
        startTestBtn.setText(getResources().getString(R.string.start_test));
        startTestBtn.setOnClickListener(view -> {
            startTestBtn.setVisibility(View.GONE);
            onboardingText.setVisibility(View.GONE);
            startHearingTest();
        });
    }

    // USER INITIATES THE TEST
    private void startHearingTest() {
        currentStep = 0;
        heardFrequencies.clear();
        testSection.setVisibility(View.VISIBLE);
        resultSection.setVisibility(View.GONE);
        testStarted = true;

        updateTestStep();
    }

    // Play tone, ask for user input, handle UI for each frequency
    private void updateTestStep() {
        if (currentStep >= FREQUENCY_ARRAY.length) {
            // Test complete
            showFinalResult();
            return;
        }

        int freq = FREQUENCY_ARRAY[currentStep];
        freqText.setText(getString(R.string.freq_prompt, freq));
        progressBar.setMax(FREQUENCY_ARRAY.length);
        progressBar.setProgress(currentStep+1);

        // Set up play and user response
        playToneBtn.setOnClickListener((v) -> {
            playToneBtn.setEnabled(false);
            playPureTone(freq, SAMPLE_RATE, TONE_DURATION_SEC, () -> {
                runOnUiThread(() -> playToneBtn.setEnabled(true));
            });
        });

        heardBtn.setOnClickListener((v) -> {
            heardFrequencies.add(freq);
            currentStep++;
            updateTestStep();
        });

        notHeardBtn.setOnClickListener((v) -> {
            currentStep++;
            updateTestStep();
        });
    }

    // Generate and play a single-frequency sine wave in a background thread
    private void playPureTone(int frequency, int sampleRate, float durationSeconds, Runnable onComplete) {
        new Thread(() -> {
            int sampleCount = (int) (durationSeconds * sampleRate);
            short[] samples = new short[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                samples[i] = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.25); // Scale to safe volume
            }

            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.length * 2,
                    AudioTrack.MODE_STATIC
            );
            audioTrack.write(samples, 0, samples.length);
            audioTrack.play();

            try {
                Thread.sleep((long) (durationSeconds * 1000));
            } catch (InterruptedException e) {
                // ignored
            }
            audioTrack.stop();
            audioTrack.release();
            if (onComplete != null) onComplete.run();
        }).start();
    }

    // Display frequency range result summary
    private void showFinalResult() {
        testSection.setVisibility(View.GONE);
        resultSection.setVisibility(View.VISIBLE);

        if (heardFrequencies.isEmpty()) {
            resultText.setText(getResources().getString(R.string.result_none));
        } else {
            // Determine range
            int min = heardFrequencies.get(0);
            int max = heardFrequencies.get(heardFrequencies.size()-1);
            String resultStr = getResources().getString(R.string.result_template, min, max);
            resultText.setText(resultStr);
        }

        restartBtn.setOnClickListener(v -> {
            currentStep = 0;
            heardFrequencies.clear();
            resultSection.setVisibility(View.GONE);
            onboardingText.setVisibility(View.VISIBLE);
            startTestBtn.setVisibility(View.VISIBLE);
            testStarted = false;
        });
    }

    // Check audio playback permission (Android 6+)
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_CODE_RECORD_AUDIO);
        }
    }

    // Display permission error if needed
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Microphone permission is required for the hearing range test. The app can not play sound otherwise.")
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        }
    }

}
