package com.cmcc.voicewaveview.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


import com.cmcc.voicewaveview.recorder.VoiceRecorder;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

//语音波形视图
public class VoiceWaveView extends  SurfaceView implements SurfaceHolder.Callback {
    private final String COLOR_BACKGROUND = "#7f7f7f";
    private final String COLOR_LINE = "#ffffff";
    private final String COLOR_LINE_UNPLAYED = "#99ffffff";
    private final float X_DIVIDER_WIDTH = 8; //线条间隔
    private final float LINE_WIDTH = 3; //线宽
    private final int MAX_TIME = 60; //最大录音时间

    private Context context;
    private SurfaceHolder sfh;
    private VoiceDrawTask voiceDrawTask;
    private VoicePlayDrawTask voiceplayDrawTask;
    private Canvas canvas;
    private Paint paint;
    private Timer timer;
    private boolean flag = false;  //整体运行标识
    private boolean isStopDraw = false;  //停止画图线程标识
    private boolean isReDraw = false;   //是否需要重绘标识


    private int maxLines;
    private LinkedList<WaveBean> linkedList;
    private LinkedList<WaveBean> allLinkedList;
    private LinkedList<WaveBean> compressLinkedList;

    private long playSleepTime = 0;

    public VoiceWaveView(Context context) {
        this(context, null);
    }

    public VoiceWaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        // TODO Auto-generated constructor stub
    }

    public VoiceWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        // TODO Auto-generated constructor stub
        super(context, attrs, defStyleAttr);
        this.context = context;
        this.sfh = this.getHolder();
        this.sfh.addCallback(this);
        timer = new Timer();
        paint = new Paint();
        paint.setFakeBoldText(true);  //设置粗体
        paint.setStrokeWidth(LINE_WIDTH); //设置线宽

        allLinkedList = new LinkedList<WaveBean>();
        linkedList = new LinkedList<WaveBean>();
    }

    public void start() {
        clearCanvas();  //清屏
        isStopDraw = false;
        maxLines = (int) ((getWidth()-10) / (X_DIVIDER_WIDTH)) - 2;  //根据控件宽度计算可以容纳单个波纹的最大个数
        voiceDrawTask = new VoiceDrawTask();  //波纹画图线程初始化
        timer.schedule(voiceDrawTask, 200);   //延迟200ms执行
    }

    public void stop() {
        voiceDrawTask.cancel();  //停止画图线程
        clearCanvas();  //清屏
        allLinkedList.removeAll(allLinkedList);
        linkedList.removeAll(linkedList);
    }

    //启动语音播放波纹，必须在在stopDraw后或者达到最长录音时间后调用
    public void startPlayVoice(int voice_duration) {
        clearCanvas(); //清屏
        playSleepTime = (voice_duration*1000)/compressLinkedList.size();  //根据语音时长计算单个波纹间隔
        voiceplayDrawTask = new VoicePlayDrawTask();  //语音播放画图线程初始化
        timer.schedule(voiceplayDrawTask, 200);  //延迟200ms执行
    }

    public void stopPlayVoice() {
        if(voiceplayDrawTask != null) {
            voiceplayDrawTask.cancel();
            clearCanvas();
        }
    }

    public void reDrawCompressWave() {  //重画压缩波形
        isReDraw = true;
    }

    public void stopDraw() {  //停止录音，画出压缩后的波形
        isStopDraw = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(isReDraw) { //重绘当前状态
            try {
                canvas = sfh.lockCanvas();//获得画布
                drawBackground();
                if(canvas != null) {
                    drawWave(compressLinkedList);
                }
                sfh.unlockCanvasAndPost(canvas);
                isReDraw = false;
            } catch (Exception e) {

            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private void clearCanvas() {
        try {
            canvas = sfh.lockCanvas();//获得画布
            if(canvas != null) {
                drawBackground();
            }
            sfh.unlockCanvasAndPost(canvas);
        } catch (Exception e) {

        }
    }

    private void drawWave(LinkedList<WaveBean> linkedList) {
        int i = linkedList.size();
        for (WaveBean bean : linkedList) {
            //从表头开始画
            canvas.drawLine(bean.WIDTH - i * X_DIVIDER_WIDTH, bean.HEIGHT_HALF - bean.getYoffset(), bean.WIDTH - i * X_DIVIDER_WIDTH, bean.HEIGHT_HALF + bean.getYoffset(), paint);
            i--;
        }
    }

    private void drawPlayWave(int current_position) {
        int i = compressLinkedList.size();
        for (WaveBean bean : compressLinkedList) {
            //从表头开始画
            if (compressLinkedList.size() - i <= current_position)
                paint.setColor(Color.parseColor(COLOR_LINE));
            else
                paint.setColor(Color.parseColor(COLOR_LINE_UNPLAYED));
            canvas.drawLine(bean.WIDTH - i * X_DIVIDER_WIDTH, bean.HEIGHT_HALF - bean.getYoffset(), bean.WIDTH - i * X_DIVIDER_WIDTH, bean.HEIGHT_HALF + bean.getYoffset(), paint);
            i--;
        }
    }

    //清屏画背景
    private void drawBackground() {
        if(canvas != null)
            canvas.drawColor(Color.parseColor(COLOR_BACKGROUND));
    }

    //压缩波形生成MAX_LINES个的波形图
    private LinkedList<WaveBean> getCompressLinkedList() {
        if(allLinkedList.size() == 0) {
            return linkedList;
        } else {
            allLinkedList.addAll(linkedList);
            LinkedList<WaveBean> compressList = new LinkedList<WaveBean>();
            int compress_ratio = allLinkedList.size()/maxLines;
            float average = 0;
            for (int i = 1; i <= allLinkedList.size(); i++) {
                if(i % compress_ratio == 0) {
                    if(compressList.size() < maxLines) {
                        average = average == 0 ? allLinkedList.get(i-1).getYoffset(): average;
                        compressList.add(new WaveBean(average / compress_ratio));
                        average = 0;
                    } else {
                        return compressList;
                    }
                } else {
                    average += allLinkedList.get(i-1).getYoffset();
                }
            }
            return compressList;
        }
    }

    //绘制波形线程
    private class VoiceDrawTask extends TimerTask {

        public VoiceDrawTask() {
            flag = true;
        }

        @Override
        public boolean cancel() {
            flag = false;
            return true;
        }

        @Override
        public void run() {
            paint.setColor(Color.parseColor(COLOR_LINE));
            while(flag) {
                canvas = sfh.lockCanvas();//获得画布
                if(canvas != null) {
                    drawBackground();
                    //录制最大时间
                    if(VoiceRecorder.getDefault().getVoiceDuration() == MAX_TIME || isStopDraw) {
                        //获得压缩后的波形并画出
                        compressLinkedList = getCompressLinkedList();
                        drawWave(compressLinkedList);
                        flag = false;
                    } else {
                        WaveBean wave = new WaveBean(VoiceRecorder.getDefault().getVoice_volume());
                        //链表长度超过能显示的最大数
                        if (linkedList.size() > maxLines) {
                            allLinkedList.add(linkedList.getFirst());
                            linkedList.removeFirst(); //移除链表头
                        }
                        linkedList.add(wave); //加入表尾
                        drawWave(linkedList);
                    }
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {

                    }finally {
                        try {
                            sfh.unlockCanvasAndPost(canvas);
                        } catch (Exception e2) {

                        }
                    }
                }
            }
        }
    }

    //绘图播放波形线程
    private class VoicePlayDrawTask extends TimerTask {
        int current_position = 1;

        public VoicePlayDrawTask() {
            flag = true;
        }

        @Override
        public boolean cancel() {
            // TODO Auto-generated method stub\
            flag = false;
            return true;
        }

        @Override
        public void run() {
            while(flag) {
                canvas = sfh.lockCanvas();//获得画布
                drawBackground();
                try {
                    if(current_position == compressLinkedList.size()) {
                        drawPlayWave(current_position);
                        flag = false;
                    } else {
                        drawPlayWave(current_position);
                        Thread.sleep(playSleepTime);
                        current_position++;
                    }
                } catch (Exception ex) {

                }finally {
                    try {
                        sfh.unlockCanvasAndPost(canvas);
                    } catch (Exception e2) {

                    }
                }
            }
        }
    }

    private class WaveBean {
        private final float HEIGHT_HALF = getHeight()/2;
        private final float WIDTH = getWidth();
        private float y_offset;

        public WaveBean(float y_offset) {
            super();
            this.y_offset = y_offset;
        }

        public float getYoffset() {
            return y_offset;
        }
    }

}

