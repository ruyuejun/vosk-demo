// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.SpeakerModel;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class VoskActivity extends Activity implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeakerModel speakerModel;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        setUiState(STATE_START);

        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
    }

    private void initModel() {
        StorageService.unpack(this, "vosk-model-small-cn-0.22", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
        StorageService.unpack_spk(this, "vosk-model-spk-0.4", "model_spk",
                (speakerModel) -> {
                    this.speakerModel = speakerModel;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the spk model" + exception.getMessage()));

    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
        setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            case STATE_FILE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f, "[\"one zero zero zero one\", " +
                        "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]");
                rec.setSpeakerModel(speakerModel);

                InputStream ais = getAssets().open(
                        "10001-90210-01803.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                //Recognizer rec = new Recognizer(model, 16000.0f);
                Recognizer rec = new Recognizer(model,16000.0f,"[\"小云\",\"小岛\",\"嗨 小云\",\"嗨 小岛\",\"打开 开放 模式\",\"开启 开放 模式\",\"展开 开放 模式\",\"打开 静 读 模式\",\"开启 静 读 模式\",\"展开 静 独 模式\",\"打开 私密 模式\",\"开启 私密 模式\",\"展开 私密 模式\",\"停止\",\"别动\",\"一键 停止\",\"打开 桌面\",\"开启 桌面\",\"展开 桌面\",\"关闭 桌面\",\"关 桌面\",\"升高 桌面\",\"升起 桌面\",\"降低 桌面\",\"下降 桌面\",\"升高 背板\",\"升起 背板\",\"降低 背板\",\"下降 背板\",\"展开 床\",\"打开 床\",\"收起 床\",\"关闭 床\",\"打开 柜门\",\"开启 柜门\",\"关闭 柜门\",\"打开 衣柜\",\"开启 衣柜\",\"关闭 衣柜\",\"打开 门\",\"开启 门\",\"关闭 门\",\"升高 门 柜 背板\",\"升起 门 柜 背板\",\"降低 门 柜 背板\",\"下降 门 柜 背板\",\"打开 角 柜 西 一\",\"打开 角 柜 西 二\",\"打开 角 柜 西 三\",\"打开 角 柜 西 四\",\"开启 角 柜 西 一\",\"开启 角 柜 西 二\",\"开启 角 柜 西 三\",\"开启 角 柜 西 四\",\"关闭 角 柜 西 一\",\"关闭 角 柜 西 二\",\"关闭 角 柜 西 三\",\"关闭 角 柜 西 四\",\"升高 角 柜 西 一 背板\",\"升高 角 柜 西 二 背板\",\"升高 角 柜 西 三 背板\",\"升高 角 柜 西 四 背板\",\"升起 角 柜 西 一 背板\",\"升高 角 柜 西 二 背板\",\"升高 角 柜 西 三 背板\",\"升高 角 柜 西 四 背板\",\"降低 西 一 背板\",\"降低 西 二 背板\",\"降低 西 三 背板\",\"降低 西 四 背板\",\"下降 西 一 背板\",\"下降 西 二 背板\",\"下降 西 三 背板\",\"下降 西 四 背板\",\"打开 边 柜 爱死 一\",\"打开 边 柜 爱死 二\",\"开启 边 柜 爱死 一\",\"开启 边 柜 爱死 二\",\"关闭 边 柜 爱死 一\",\"关闭 边 柜 爱死 二\",\"升高 爱死 一 背板\",\"升高 爱死 二 背板\",\"升起 爱死 一 背板\",\"升起 爱死 二 背板\",\"降低 爱死 一 背板\",\"降低 爱死 二 背板\",\"下降 爱死 一 背板\",\"下降 爱死 二 背板\",\"开始 心率 测量\",\"开启 心率 测量\",\"停止 心率 测量\",\"关闭 心率 测量\",\"开始 血氧 测量\",\"开启 血氧 测量\",\"停止 血氧 测量\",\"关闭 血氧 测量\",\"开始 睡眠 监测\",\"开启 睡眠 监测\",\"停止 睡眠 监测\",\"关闭 睡眠 监测\",\"开始 温度 测量\",\"开启 温度 测量\",\"打开 制 氧\",\"开启 制 氧\",\"停止 制 氧\",\"关闭 制 氧\",\"打开 空气 质量\",\"开启 空气 质量\",\"查看 空气 质量\", \"[unk]\"]");
                rec.setSpeakerModel(speakerModel);

                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }


    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }

}
