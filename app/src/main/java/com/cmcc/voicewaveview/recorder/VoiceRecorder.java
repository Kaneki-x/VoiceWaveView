package com.cmcc.voicewaveview.recorder;

import android.media.MediaRecorder;


import com.cmcc.voicewaveview.event.RecordEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

import de.greenrobot.event.EventBus;


/*
 * 语音录制类
 * @author:Lion
 */
public class VoiceRecorder {

    public static final String TAG = "VoiceRecorder:";
    public static final String EXTENSION = ".amr";

    public static final int VOICE_RECORDER_TIP = 1;
    public static final int VOICE_PLAYER_TIP = 2;

    //最大录音时间
    private static final int MAX_VOICE_TIME = 60;

    private static VoiceRecorder mVoiceRecorder;

    private MediaRecorder mRecorder;

    //录音文件
    private File mRecordFile;
    //定时返回音量的timer
    private RecordTimer mVoiceTimer;
    private RecordTimer mDurationTimer;

    //是否正在录音
    private boolean isRecording = false;

    //录音时长
    private int mVoiceDuration = -1;
    //音频名称格式

    //音量
    private float voice_volume;

    public static VoiceRecorder getDefault() {
        if(mVoiceRecorder == null){
            synchronized(VoiceRecorder.class){
                if(mVoiceRecorder == null){
                    mVoiceRecorder = new VoiceRecorder();
                }
            }
        }
        return mVoiceRecorder;
    }

    private abstract class RecordTimer extends Thread {

        private boolean isCancel = false;

        @Override
        public void run() {
            //根据cancel停止线程
            while (!isCancel) {
                doTask();
            }
        }

        public abstract void doTask();

        public void cancel() {
            isCancel = true;
        }
    }

    private VoiceRecorder() {
    }

    public float getVoice_volume() {
        return voice_volume;
    }

    //重新设置默认参数
    private void reSetVoiceDefault() {
        this.isRecording = false;
        this.mVoiceDuration = 0;
        this.mRecordFile = null;
        this.mVoiceTimer = null;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public int getVoiceDuration() {
        return mVoiceDuration;
    }

    public void startRecording(String filePath) {
        try {
            if(isRecording)
                discardRecording();
            isRecording = true;
            mRecordFile = new File(filePath);
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(1);
            mRecorder.setOutputFormat(3);
            mRecorder.setAudioEncoder(1);
            mRecorder.setAudioChannels(1);
            mRecorder.setAudioSamplingRate(8000);
            mRecorder.setAudioEncodingBitRate(64);
            mRecorder.setOutputFile(mRecordFile.getAbsolutePath());
            mRecorder.prepare();
            mRecorder.start();
        } catch (IOException localIOException) {
            reSetVoiceDefault();
        }
        initTimer();
    }

    //取消录音
    public void discardRecording() {
        if (mRecorder != null) {
            try {
                //停止录音释放资源
                isRecording = false;
                mRecorder.stop();
                mRecorder.release();
                mRecorder = null;
                if ((mRecordFile != null) && (mRecordFile.exists()) && (!mRecordFile.isDirectory())) {
                    //删除已经录制的音频
                    mRecordFile.delete();
                }
                if(mVoiceTimer != null) {
                    mVoiceTimer.cancel();
                }
                if(mDurationTimer != null) {
                    mDurationTimer.cancel();
                }
            } catch (IllegalStateException localIllegalStateException) {
            }
            reSetVoiceDefault();
        }
    }

    //停止录音返回录音文件路径
    public String stopRecoding() {
        if (mRecorder != null) {
            try {
                isRecording = false;
                mRecorder.stop();
            } catch (Exception e) {
            }finally{
                mRecorder.release();
                mRecorder = null;
                if(mVoiceTimer != null) {
                    mVoiceTimer.cancel();
                }
                if(mDurationTimer != null) {
                    mDurationTimer.cancel();
                }
            }
            String fileName = getVoiceFileNameWithDuration();
            reSetVoiceDefault();
            return fileName;
        } else {
            reSetVoiceDefault();
            return null;
        }
    }

    //删除指定语音文件
    public void deleteVoiceFile(String filePath) {
        try {
            File file = new File(filePath);
            if(file.exists())
                file.delete();
        } catch (Exception e) {

        }
    }

    private void initTimer() {
        mVoiceTimer = new RecordTimer() {
            @Override
            public void doTask() {
                try {
                    while (isRecording && mRecorder != null) {
                        double ratio = (double) mRecorder.getMaxAmplitude();
                        if (ratio > 1) {
                            voice_volume = (float) (20 * Math.log10(ratio*ratio)) / 5 - 5;
                            if(voice_volume < 15)
                                voice_volume = voice_volume / 2;
                        }
                        mVoiceTimer.sleep(100L);
                    }
                } catch (Exception localException) {
                }
            }
        };
        mVoiceTimer.start();
        mDurationTimer = new RecordTimer() {
            @Override
            public void doTask() {
                int durations = 0;
                while (isRecording && mRecorder != null) {
                    try {
                        mVoiceDuration++;
                        durations ++;
                        mDurationTimer.sleep(1000);
                        RecordEvent recordEvent = new RecordEvent(durations);
                        EventBus.getDefault().post(recordEvent);
                    } catch (Exception e) {

                    }
                }
            }
        };
        mDurationTimer.start();
    }

    //获取最后拼接上时长的录音文件路径
    private String getVoiceFileNameWithDuration() {
        if(mRecordFile != null) {
            if(mVoiceDuration > 60)
                mVoiceDuration = 60;
            if(mVoiceDuration == 0)
                mVoiceDuration++;
            String fileName = mRecordFile.getAbsolutePath().split(EXTENSION)[0] + "_" + mVoiceDuration + EXTENSION;
            mRecordFile.renameTo(new File(fileName));
            return fileName;
        } else
            return null;
    }

}
