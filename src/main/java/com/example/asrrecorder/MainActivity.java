package com.example.asrrecorder;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.init.HciCloudSysHelper;
import com.sinovoice.hcicloudsdk.android.asr.recorder.ASRRecorder;
import com.sinovoice.hcicloudsdk.common.asr.AsrConfig;
import com.sinovoice.hcicloudsdk.common.asr.AsrInitParam;
import com.sinovoice.hcicloudsdk.common.asr.AsrRecogResult;
import com.sinovoice.hcicloudsdk.common.tts.TtsInitParam;
import com.sinovoice.hcicloudsdk.recorder.ASRRecorderListener;
import com.sinovoice.hcicloudsdk.recorder.RecorderEvent;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int INIT_SUCCESS = 0;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String CAP_KEY = "asr.cloud.freetalk";
    private static final int ERROR_CALLBACK = 3;
    private static final int STATE_CALLBACK = 1;
    private static final int RESULT_CALLBACK = 2;
    private TextView stateTextView;
    private TextView resultTextView;
    private TextView errorTextView;
    private Button recogButton;
    private HciCloudSysHelper mHciCloudSysHelper;
    private ASRRecorder mAsrRecorder;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case INIT_SUCCESS:
                    Bundle bundle = msg.getData();
                    boolean b = bundle.getBoolean("errCode");
                    if(true == b){
                        Toast.makeText(MainActivity.this, "初始化成功", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case ERROR_CALLBACK:
                    Bundle errorBundle = msg.getData();
                    String error = errorBundle.getString("error");
                    errorTextView.setText(error);
                    break;
                case STATE_CALLBACK:
                    Bundle stateBundle = msg.getData();
                    String state = stateBundle.getString("state");
                    stateTextView.setText(state);
                    break;
                case RESULT_CALLBACK:
                    Bundle resultBundle = msg.getData();
                    String result = resultBundle.getString("result");
                    resultTextView.setText(result);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stateTextView = (TextView) findViewById(R.id.state_textview);
        resultTextView = (TextView) findViewById(R.id.result_textview);
        errorTextView = (TextView) findViewById(R.id.error_textview);
        recogButton = (Button) findViewById(R.id.recog_button);

        mHciCloudSysHelper = HciCloudSysHelper.getInstance();
        mAsrRecorder = new ASRRecorder();
        /**
         * 初始化放到子线程中实现
         */
        new Thread(new Runnable() {
            @Override
            public void run() {
                //灵云系统和TTS播放器初始化放到子线程中，初始化成功之后通过Handler通知主线程
                boolean b = init();
                Log.e(TAG, "初始化：" + b);
                //获取Message信息
                Message message = Message.obtain();
                //设置成功时的返回状态
                message.what = INIT_SUCCESS;
                //初始化的返回值通过Bundle传送给主线程
                Bundle bundle = message.getData();
                //设置key=errCode，value=b
                bundle.putBoolean("errCode", b);
                message.setData(bundle);
                handler.sendMessage(message);
            }
        }).start();
        recogButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.recog_button:
                startRecorder();
        }
    }

    /**
     * 开始录音并识别
     */
    private void startRecorder() {
        // 配置识别参数
        final AsrConfig asrConfig = new AsrConfig();
        // PARAM_KEY_CAP_KEY 设置使用的能力
        asrConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, CAP_KEY);
        // PARAM_KEY_AUDIO_FORMAT 音频格式根据不同的能力使用不用的音频格式
        asrConfig.addParam(AsrConfig.AudioConfig.PARAM_KEY_AUDIO_FORMAT, AsrConfig.AudioConfig.VALUE_OF_PARAM_AUDIO_FORMAT_PCM_16K16BIT);
        // PARAM_KEY_ENCODE 音频编码压缩格式，使用OPUS可以有效减小数据流量
        asrConfig.addParam(AsrConfig.AudioConfig.PARAM_KEY_ENCODE, AsrConfig.AudioConfig.VALUE_OF_PARAM_ENCODE_SPEEX);
        //非实时识别
        asrConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_REALTIME, "no");
        if(mAsrRecorder.getRecorderState() == ASRRecorder.RECORDER_STATE_IDLE){
            mAsrRecorder.start(asrConfig.getStringConfig(), null);
        }else{
            Log.e(TAG, "录音机未处于空闲状态，请稍等");
        }
    }

    /**
     * 灵云系统初始化和录音机初始化
     * @return
     */
    private boolean init() {
        boolean isSuccess = mHciCloudSysHelper.init(this);
        if (!isSuccess) {
            Log.e(TAG, "hci init error.");
            return false;
        }
        //录音机初始化
        isSuccess = initRecorder();
        if (!isSuccess) {
            Log.e(TAG, "hci initRecorder error");
            return false;
        }
        return isSuccess;
    }

    /**
     * 录音机初始化
     * @return
     */
    private boolean initRecorder() {
        // 构造Tts初始化的帮助类的实例
        AsrInitParam asrInitParam = new AsrInitParam();
        // 获取App应用中的lib的路径
        String dataPath = getBaseContext().getFilesDir().getAbsolutePath().replace("files", "lib");
        asrInitParam.addParam(TtsInitParam.PARAM_KEY_DATA_PATH, dataPath);
        asrInitParam.addParam(TtsInitParam.PARAM_KEY_INIT_CAP_KEYS, CAP_KEY);
        asrInitParam.addParam(TtsInitParam.PARAM_KEY_FILE_FLAG, "android_so");

        mAsrRecorder.init(asrInitParam.getStringConfig(), new ASRResultProcess());
        return true;
    }

    private class ASRResultProcess implements ASRRecorderListener {
        /**
         * 错误事件回调
         * @param arg0
         * @param arg1
         */
        @Override
        public void onRecorderEventError(RecorderEvent arg0, int arg1) {
            String sError = "错误码为：" + arg1;
            Log.e(TAG, sError);
            Message message = Message.obtain();
            message.what = ERROR_CALLBACK;
            Bundle bundle = message.getData();
            bundle.putString("error", sError);
            message.setData(bundle);
            handler.sendMessage(message);
        }

        /**
         * 识别结果状态回调
         * @param recorderEvent
         * @param arg1
         */
        @Override
        public void onRecorderEventRecogFinsh(RecorderEvent recorderEvent, AsrRecogResult arg1) {
            if (recorderEvent == RecorderEvent.RECORDER_EVENT_RECOGNIZE_COMPLETE) {
                String sState = "状态为：识别结束";
                Log.e(TAG, sState);
//                Message m = mUIHandle.obtainMessage(1, 1, 1, sState);
//                mUIHandle.sendMessage(m);
                Message message = Message.obtain();
                message.what = STATE_CALLBACK;
                Bundle bundle = message.getData();
                bundle.putString("state", sState);
                message.setData(bundle);
                handler.sendMessage(message);
            }
            if (arg1 != null) {
                String sResult;
                if (arg1.getRecogItemList().size() > 0) {
                    sResult = "识别结果为："
                            + arg1.getRecogItemList().get(0).getRecogResult();
                } else {
                    sResult = "未能正确识别,请重新输入";
                }
                Log.e(TAG, sResult);
//                Message m = mUIHandle.obtainMessage(1, 2, 1, sResult);
//                mUIHandle.sendMessage(m);
                Message msg = Message.obtain();
                msg.what = RESULT_CALLBACK;
                Bundle bundle = msg.getData();
                bundle.putString("result", sResult);
                msg.setData(bundle);
                handler.sendMessage(msg);
            }
        }

        /**
         * 状态变化回调函数
         * @param recorderEvent
         */
        @Override
        public void onRecorderEventStateChange(RecorderEvent recorderEvent) {
            String sState = "状态为：初始状态";
            if (recorderEvent == RecorderEvent.RECORDER_EVENT_BEGIN_RECORD) {
                sState = "状态为：开始录音";
            } else if (recorderEvent == RecorderEvent.RECORDER_EVENT_BEGIN_RECOGNIZE) {
                sState = "状态为：开始识别";
            } else if (recorderEvent == RecorderEvent.RECORDER_EVENT_NO_VOICE_INPUT) {
                sState = "状态为：无音频输入";
            }
            Log.e(TAG, sState);
//            Message m = mUIHandle.obtainMessage(1, 1, 1, sState);
//            mUIHandle.sendMessage(m);
            Message message = Message.obtain();
            message.what = STATE_CALLBACK;
            Bundle bundle = message.getData();
            bundle.putString("state", sState);
            message.setData(bundle);
            handler.sendMessage(message);
        }

        /**
         * 录音数据结果回调
         * @param volumedata 声音数据
         * @param volume    音量大小
         */
        @Override
        public void onRecorderRecording(byte[] volumedata, int volume) {
        }

        /**
         * 中间结果回调函数，只有在设置为realtime=rt 也就是实时反馈时才会生效
         * @param recorderEvent
         * @param arg1
         */
        @Override
        public void onRecorderEventRecogProcess(RecorderEvent recorderEvent, AsrRecogResult arg1) {
            // TODO Auto-generated method stub
            if (recorderEvent == RecorderEvent.RECORDER_EVENT_RECOGNIZE_PROCESS) {
                String sState = "状态为：识别中间反馈";
//                Message m = mUIHandle.obtainMessage(1, 1, 1, sState);
//                mUIHandle.sendMessage(m);
            }
            if (arg1 != null) {
                String sResult;
                if (arg1.getRecogItemList().size() > 0) {
                    sResult = "识别中间结果结果为："
                            + arg1.getRecogItemList().get(0).getRecogResult();
                } else {
                    sResult = "未能正确识别,请重新输入";
                }
//                Message m = mUIHandle.obtainMessage(1, 2, 1, sResult);
//                mUIHandle.sendMessage(m);
            }
        }
    }
}
