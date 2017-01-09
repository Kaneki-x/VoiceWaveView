/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Kaneki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.kaneki.voicewaveview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Kaneki
 * @Desctription 语音波形控件
 * @date 2017-01-01
 * @email yueqian@meili-inc.com
 */
public class VoiceWaveView extends View {

    private final static int MODE_RECORDING = 0;
    private final static int MODE_PLAYING = 1;

    //录制计时
    private long duration = 0;
    //播放线程等待间隔
    private long playSleepTime = 0;

    //播放波形位置
    private int current_position = 1;
    //View能包含的最大波形个数
    private int maxLines = 0;
    //当前模式
    private int mode = -1;
    //波形最大高度
    private float maxWaveHeight;

    //录制线程运行标记
    private boolean record_flag = false;
    //播放线程标记
    private boolean play_flag = false;
    //录制暂停标记
    private boolean isRecordPause = false;
    //播放暂停标记
    private boolean isPlayPause = false;

    //控件背景色
    private int backgroundColor;
    //波形颜色
    private int activeLineColor;
    //待播放波形颜色
    private int inactiveLineColor;
    //波形间隔
    private float dividerWidth;
    //波形线宽
    private float lineWidth;
    //刷新间隔
    private long refreshRatio;
    //最大录音时间
    private int maxDuration;

    private Context context;
    //波形录制线程
    private VoiceDrawTask voiceDrawTask;
    //波形播放线程
    private VoicePlayDrawTask voicePlayDrawTask;
    private Paint paint;
    private Timer timer;

    private LinkedList<WaveBean> linkedList;
    private LinkedList<WaveBean> allLinkedList;
    private LinkedList<WaveBean> compressLinkedList;

    //对外波形高度参数 volatile标记作为初步线程同步作用
    private volatile float waveHeight;

    public VoiceWaveView(Context context) {
        this(context, null);
    }

    public VoiceWaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VoiceWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;

        initAttrs(attrs);
        initParameters();
    }

    /**
     * 初始化自定义参数
     * @param attrs
     */
    private void initAttrs(AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.VoiceWaveView);

        backgroundColor = ta.getColor(R.styleable.VoiceWaveView_backgroundColor, Color.parseColor("#7f7f7f"));
        activeLineColor = ta.getColor(R.styleable.VoiceWaveView_activeLineColor, Color.parseColor("#ffffff"));
        inactiveLineColor = ta.getColor(R.styleable.VoiceWaveView_inactiveLineColor, Color.parseColor("#99ffffff"));
        lineWidth = ta.getDimension(R.styleable.VoiceWaveView_lineWidth, 3.0f);
        dividerWidth = ta.getDimension(R.styleable.VoiceWaveView_duration, 8.0f);
        refreshRatio = ta.getInt(R.styleable.VoiceWaveView_refreshRatio, 50);
        maxDuration = ta.getInt(R.styleable.VoiceWaveView_duration, 60);

        ta.recycle();
    }

    /**
     * 初始化变量
     */
    private void initParameters() {
        timer = new Timer();
        paint = new Paint();
        //设置抗锯齿
        paint.setAntiAlias(true);
        //设置粗体
        paint.setFakeBoldText(true);
        //设置画笔宽度
        paint.setStrokeWidth(lineWidth);

        allLinkedList = new LinkedList<>();
        linkedList = new LinkedList<>();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        //计算波形最大高度 View高度的60%
        maxWaveHeight = getHeight() / 2 * 0.6f;
        //计算View能够容纳显示的最大波形个数
        maxLines = (int) ((getWidth() - 10) / (dividerWidth));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        if(mode == MODE_RECORDING) {
            paint.setColor(activeLineColor);
            //露珠是否暂停
            if (isRecordPause)
                drawWave(canvas, compressLinkedList);
            else
                drawWave(canvas, linkedList);
        } else if (mode == MODE_PLAYING) {
            drawPlayWave(canvas, current_position);
        }
    }

    @Override
    public boolean isHardwareAccelerated() {
        return true;
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if(visibility != VISIBLE) {
            //可见性发生变化时 释放资源
            isRecordPause = true;
            releaseAll();
        }
    }

    /**
     * 根据波形链表话波形
     * @param canvas
     * @param linkedList
     */
    private void drawWave(Canvas canvas, LinkedList<WaveBean> linkedList) {
        int i = linkedList.size();
        for (WaveBean bean : linkedList) {
            //从表头开始画
            canvas.drawLine(bean.WIDTH - i * dividerWidth, bean.HEIGHT_HALF - bean.getYoffset(), bean.WIDTH - i * dividerWidth, bean.HEIGHT_HALF + bean.getYoffset(), paint);
            i--;
        }
    }

    /**
     * 根据当前播放位置画播放状态波形图
     * @param canvas
     * @param current_position
     */
    private void drawPlayWave(Canvas canvas, int current_position) {
        int i = compressLinkedList.size();
        for (WaveBean bean : compressLinkedList) {
            //从表头开始画
            if (compressLinkedList.size() - i <= current_position)
                paint.setColor(activeLineColor);
            else
                paint.setColor(inactiveLineColor);
            canvas.drawLine(bean.WIDTH - i * dividerWidth, bean.HEIGHT_HALF - bean.getYoffset(), bean.WIDTH - i * dividerWidth, bean.HEIGHT_HALF + bean.getYoffset(), paint);
            i--;
        }
    }

    /**
     * 画背景
     * @param canvas
     */
    private void drawBackground(Canvas canvas) {
        if(canvas != null)
            canvas.drawColor(backgroundColor);
    }

    /**
     * 压缩波形生成MAX_LINES个的波形列表
     * @return
     */
    private LinkedList<WaveBean> getCompressLinkedList() {
        if(allLinkedList.size() == 0) {
            int remain_size = maxLines - linkedList.size();
            LinkedList<WaveBean> compressList = new LinkedList<>();
            if(remain_size >= linkedList.size()) {
                int compress_ratio = remain_size / linkedList.size();
                for(WaveBean waveBean : linkedList) {
                    compressList.add(waveBean);
                    for(int i = 0; i < compress_ratio; i++)
                        compressList.add(new WaveBean(waveBean.getYoffset()));
                }
            } else {
                int compress_ratio = linkedList.size() / remain_size;
                int current_position = 1;
                for(WaveBean waveBean : linkedList) {
                    compressList.add(waveBean);
                    if(current_position % compress_ratio == 0)
                        compressList.add(new WaveBean(waveBean.getYoffset()));
                    current_position++;
                }
            }
            return compressList;
        } else {
            allLinkedList.addAll(linkedList);
            LinkedList<WaveBean> compressList = new LinkedList<>();
            int compress_ratio = allLinkedList.size()/maxLines;
            float average = 0;
            for (int i = 1; i <= allLinkedList.size(); i++) {
                if(i % compress_ratio == 0) {
                    if(compressList.size() < maxLines) {
                        average = average == 0 ? allLinkedList.get(i - 1).getYoffset() : average;
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

    /**
     * 开始录制
     */
    public void startRecord() {
        releaseAll();
        mode = MODE_RECORDING;
        isRecordPause = false;
        duration = 0;
        voiceDrawTask = new VoiceDrawTask();
        timer.schedule(voiceDrawTask, 200);
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        isRecordPause = true;
    }

    /**
     * 录制波形高度设置 0-100%
     * @param percent
     */
    public void setWaveHeight(int percent) {
        if (!isRecordPause) {
            if (percent >= 100 || percent <= 0)
                percent = 1;

            waveHeight = maxWaveHeight * percent / 100;
        }
    }

    /**
     * 启动录制波形播放，必须在stopRecord后或者达到最长录音时间后调用
     */
    public void startPlay() {
        if(compressLinkedList != null) {
            releaseThread();
            mode = MODE_PLAYING;
            isPlayPause = false;
            current_position = 1;
            playSleepTime = duration / compressLinkedList.size();  //根据语音时长计算单个波纹间隔
            voicePlayDrawTask = new VoicePlayDrawTask();  //语音播放画图线程初始化
            timer.schedule(voicePlayDrawTask, 200);  //延迟200ms执行
        }
    }

    /**
     * 暂停播放
     */
    public void pausePlay() {
        if (isPlayPause)
            isPlayPause = false;
        else
            isPlayPause = true;
    }

    /**
     * 释放线程和链表资源
     */
    private void releaseAll() {
        releaseThread();
        if(allLinkedList != null) {
            allLinkedList.clear();
        }
        if(linkedList != null) {
            linkedList.clear();
        }
        if(compressLinkedList != null)
            compressLinkedList.clear();

    }

    private void releaseThread() {
        if(voiceDrawTask != null && record_flag)
            voiceDrawTask.cancel();
        if(voicePlayDrawTask != null && play_flag)
            voicePlayDrawTask.cancel();
    }

    /**
     * 波形绘制线程
     */
    private class VoiceDrawTask extends TimerTask {

        public VoiceDrawTask() {
            record_flag = true;
        }

        @Override
        public boolean cancel() {
            //停止标记
            record_flag = false;
            return true;
        }

        @Override
        public void run() {
            while (record_flag) {
                try {
                    duration += refreshRatio;
                    //录制最大时间
                    if (duration/1000 >= maxDuration || isRecordPause) {
                        //获得压缩后的波形并画出
                        compressLinkedList = getCompressLinkedList();
                        postInvalidate();
                        isRecordPause = true;
                        record_flag = false;
                    } else {
                        WaveBean wave = new WaveBean(waveHeight);
                        //链表长度超过能显示的最大数
                        if (linkedList.size() > maxLines) {
                            allLinkedList.add(linkedList.getFirst());
                            linkedList.removeFirst(); //移除链表头
                        }
                        linkedList.add(wave); //加入表尾
                        postInvalidate();
                        Thread.sleep(refreshRatio);
                    }
                } catch (Exception ex) {
                }
            }
        }
    }

    //  绘图播放波形线程
    private class VoicePlayDrawTask extends TimerTask {

        public VoicePlayDrawTask() {
            play_flag = true;
        }

        @Override
        public boolean cancel() {
            play_flag = false;
            return true;
        }

        @Override
        public void run() {
            while(play_flag) {
                try {
                    if(!isPlayPause) {
                        if (current_position == compressLinkedList.size())
                            play_flag = false;
                        else
                            current_position++;
                    }
                    postInvalidate();
                    Thread.sleep(playSleepTime);
                } catch (Exception ex) {

                }
            }
        }
    }

    private class WaveBean {
        private final float HEIGHT_HALF = getHeight()/2;
        private final float WIDTH = getWidth();
        private float y_offset;

        public WaveBean(float y_offset) {
            this.y_offset = y_offset;
        }

        public float getYoffset() {
            return y_offset;
        }
    }

}

