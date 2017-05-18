package me.kaneki.voicewaveview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author kaneki
 */
public class VoiceWaveView extends View {

    private final static int MODE_RECORDING = 0;
    private final static int MODE_PLAYING = 1;

    private float HEIGHT_HALF;
    private float WIDTH;

    //录制计时
    private long duration;
    //播放线程等待间隔
    private long playSleepTime;

    //播放波形位置
    private int current_position = 1;
    //View能包含的最大波形个数
    private int maxLines;
    //当前模式
    private int mode = -1;
    //波形最大高度
    private float maxWaveHeight;

    //录制线程运行标记
    private boolean record_flag;
    //播放线程标记
    private boolean play_flag;
    //录制暂停标记
    private boolean isRecordPause;
    //播放暂停标记
    private boolean isPlayPause;

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
    //背景图
    private Drawable backgroundDrawable;

    private Context context;
    //波形录制线程
    private VoiceDrawTask voiceDrawTask;
    //波形播放线程
    private VoicePlayDrawTask voicePlayDrawTask;
    private Paint paint;
    private Timer timer;

    private LinkedList<WaveBean> linkedList;
    private LinkedList<WaveBean> allLinkedList;
    private LinkedList<WaveBean> defaultLinkedList;
    private ArrayList<WaveBean> compressLinkedList;

    //对外波形高度参数 volatile标记作为线程同步作用
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
        backgroundDrawable = getBackground();

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
        defaultLinkedList = new LinkedList<>();
        linkedList = new LinkedList<>();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        //计算波形最大高度 View高度的60%
        maxWaveHeight = getHeight() / 2 * 0.6f;
        //计算View能够容纳显示的最大波形个数
        maxLines = (int) ((getWidth() - 10) / (dividerWidth));

        HEIGHT_HALF = getHeight()/2;
        WIDTH = getWidth();

        initDefaultWaveList();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        if(mode == MODE_RECORDING) {
            paint.setColor(activeLineColor);
            //录制是否暂停
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

    /********************* 对外方法 *********************/

    /**
     * 开始录制
     */
    public void startRecord() {
        releaseAll();
        mode = MODE_RECORDING;
        isRecordPause = false;
        duration = 0;
        voiceDrawTask = new VoiceDrawTask();
        linkedList.addAll(defaultLinkedList);
        timer.schedule(voiceDrawTask, 200);
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        isRecordPause = true;
        linkedList.clear();
    }

    /**
     * 录制波形高度设置 0-100%
     * @param percent
     */
    public void setWaveHeightPercent(int percent) {
        if (!isRecordPause) {
            if (percent >= 100 || percent <= 0)
                percent = 1;

            waveHeight = maxWaveHeight * percent / 100;
        }
    }

    /**
     * 启动录制波形播放，必须在stopRecord后或者达到最长录音时间后调用
     * @param waveData 准备播放的波形列表，传null则播放上次stopRecord后的列表
     */
    public void startPlay(WaveData waveData) {
        if(waveData != null) {
            compressLinkedList = waveData.getWaveList();
            duration = waveData.getDuration();
        }
        if (compressLinkedList != null) {
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
    public void pauseOrResumePlay() {
        isPlayPause = !isPlayPause;
    }

    /**
     * 获取当前录制的语音波形列表
     * @return
     */
    public WaveData getLastWaveData() {
        if (compressLinkedList != null && !compressLinkedList.isEmpty()) {
            return new WaveData(compressLinkedList, duration);
        } else {
            return null;
        }
    }

    public class WaveData {
        private ArrayList<WaveBean> waveList;
        private long duration;

        public WaveData(ArrayList<WaveBean> waveList, long duration) {
            this.waveList = waveList;
            this.duration = duration;
        }

        public ArrayList<WaveBean> getWaveList() {
            return waveList;
        }

        public void setWaveList(ArrayList<WaveBean> waveList) {
            this.waveList = waveList;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }
    }

    public class WaveBean {
        private float y_offset;

        private WaveBean(float y_offset) {
            this.y_offset = y_offset;
        }

        public float getY_offset() {
            return y_offset;
        }
    }


    /********************* 内部方法 *********************/

    private void initDefaultWaveList() {
        for (int i = 0; i < maxLines; i++) {
            defaultLinkedList.add(new WaveBean(1));
        }
    }

    /**
     * 根据波形链表话波形
     * @param canvas
     * @param waveBeanList
     */
    private void drawWave(Canvas canvas, List<WaveBean> waveBeanList) {
        if (waveBeanList == null)
            return;
        int i = waveBeanList.size();
        for (WaveBean bean : waveBeanList) {
            //从表头开始画
            canvas.drawLine(WIDTH - i * dividerWidth, HEIGHT_HALF - bean.getY_offset(), WIDTH - i * dividerWidth, HEIGHT_HALF + bean.getY_offset(), paint);
            i--;
        }
    }

    /**
     * 根据当前播放位置画播放状态波形图
     * @param canvas
     * @param current_position
     */
    private void drawPlayWave(Canvas canvas, int current_position) {
        if (compressLinkedList == null)
            return;
        int i = compressLinkedList.size();
        for (WaveBean bean : compressLinkedList) {
            //从表头开始画
            if (compressLinkedList.size() - i <= current_position)
                paint.setColor(activeLineColor);
            else
                paint.setColor(inactiveLineColor);
            canvas.drawLine(WIDTH - i * dividerWidth, HEIGHT_HALF - bean.getY_offset(), WIDTH - i * dividerWidth, HEIGHT_HALF + bean.getY_offset(), paint);
            i--;
        }
    }

    /**
     * 画背景
     * @param canvas
     */
    private void drawBackground(Canvas canvas) {
        if(canvas != null) {
            if (backgroundDrawable == null)
                canvas.drawColor(backgroundColor);
        }
    }

    /**
     * 压缩波形生成MAX_LINES个的波形列表
     * @return
     */
    private ArrayList<WaveBean> getCompressLinkedList() {
        ArrayList<WaveBean> compressList = new ArrayList<>(maxLines);
        if(allLinkedList.size() < maxLines) {
            int remain_size = maxLines - allLinkedList.size();
            if(remain_size >= allLinkedList.size()) {
                int compress_ratio = (int) Math.ceil((double) remain_size / (double)allLinkedList.size());
                for(WaveBean waveBean : allLinkedList) {
                    if (compressList.size() < maxLines)
                        compressList.add(waveBean);
                    for(int i = 0; i < compress_ratio; i++) {
                        if (compressList.size() < maxLines) {
                            compressList.add(new WaveBean(waveBean.getY_offset()));
                        }
                    }
                }
            } else {
                int compress_ratio = allLinkedList.size() / remain_size;
                int current_position = 1;
                for(WaveBean waveBean : allLinkedList) {
                    if (compressList.size() < maxLines) {
                        compressList.add(waveBean);
                    }
                    if(current_position % compress_ratio == 0) {
                        if (compressList.size() < maxLines) {
                            compressList.add(new WaveBean(waveBean.getY_offset()));
                        }
                    }
                    current_position++;
                }
            }
            return compressList;
        } else {
            int compress_ratio = allLinkedList.size() / maxLines;
            float average = 0;
            for (int i = 1; i <= allLinkedList.size(); i++) {
                if(i % compress_ratio == 0) {
                    if(compressList.size() < maxLines) {
                        average = average == 0 ? allLinkedList.get(i - 1).getY_offset() : average;
                        if (compressList.size() < maxLines) {
                            compressList.add(new WaveBean(average / compress_ratio));
                        }
                        average = 0;
                    } else {
                        return compressList;
                    }
                } else {
                    average += allLinkedList.get(i-1).getY_offset();
                }
            }
            return compressList;
        }
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
                            //移除链表头
                            linkedList.removeFirst();
                        }
                        allLinkedList.add(wave);
                        //加入表尾
                        linkedList.add(wave);
                        postInvalidate();
                        Thread.sleep(refreshRatio);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * 录制波形播放线程
     */
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
                    ex.printStackTrace();
                }
            }
        }
    }
}

