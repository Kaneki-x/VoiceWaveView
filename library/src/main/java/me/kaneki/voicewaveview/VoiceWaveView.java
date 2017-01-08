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

//语音波形视图
public class VoiceWaveView extends View {

    private final static int MODE_RECORDING = 0;
    private final static int MODE_PLAYING = 1;

    private long duration = 0;
    private long playSleepTime = 0;

    private int current_position = 1;
    private int maxLines = 0;
    private int mode = -1;
    private float maxWaveHeight;

    private boolean record_flag = false;  //整体运行标识
    private boolean play_flag = false;  //整体运行标识
    private boolean isRecordPause = false;  //停止画图线程标识
    private boolean isPlayPause = false;   //是否需要重绘标识

    private int backgroundColor;
    private int activeLineColor;
    private int inactiveLineColor;
    private float dividerWidth; //线条间隔
    private float lineWidth; //线宽
    private long refreshRatio; //刷新间隔
    private int maxDuration; //最大录音时间

    private Context context;
    private VoiceDrawTask voiceDrawTask;
    private VoicePlayDrawTask voicePlayDrawTask;
    private Paint paint;
    private Timer timer;

    private LinkedList<WaveBean> linkedList;
    private LinkedList<WaveBean> allLinkedList;
    private LinkedList<WaveBean> compressLinkedList;

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

    private void initParameters() {
        timer = new Timer();
        paint = new Paint();
        paint.setFakeBoldText(true);  //设置粗体
        paint.setStrokeWidth(lineWidth); //设置线宽

        allLinkedList = new LinkedList<>();
        linkedList = new LinkedList<>();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        maxWaveHeight = getHeight() / 2 * 0.6f;
        maxLines = (int) ((getWidth() - 10) / (dividerWidth));  //根据控件宽度计算可以容纳单个波纹的最大个数
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        if(mode == MODE_RECORDING) {
            paint.setColor(activeLineColor);
            //录制最大时间
            if (duration/1000 == maxDuration || isRecordPause)
                drawWave(canvas, compressLinkedList);
            else
                drawWave(canvas, linkedList);
        } else if (mode == MODE_PLAYING) {
            if(!isPlayPause) {
                if (current_position == compressLinkedList.size()) {
                    drawPlayWave(canvas, current_position);
                    play_flag = false;
                } else {
                    drawPlayWave(canvas, current_position);
                    current_position++;
                }
            } else {
                drawPlayWave(canvas, current_position);
            }
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if(visibility != VISIBLE)
            releaseAll();
    }

    private void drawWave(Canvas canvas, LinkedList<WaveBean> linkedList) {
        int i = linkedList.size();
        for (WaveBean bean : linkedList) {
            //从表头开始画
            canvas.drawLine(bean.WIDTH - i * dividerWidth, bean.HEIGHT_HALF - bean.getYoffset(), bean.WIDTH - i * dividerWidth, bean.HEIGHT_HALF + bean.getYoffset(), paint);
            i--;
        }
    }

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

    //清屏画背景
    private void drawBackground(Canvas canvas) {
        if(canvas != null)
            canvas.drawColor(backgroundColor);
    }

    //压缩波形生成MAX_LINES个的波形图
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
                int current_postion = 1;
                for(WaveBean waveBean : linkedList) {
                    compressList.add(waveBean);
                    if(current_postion % compress_ratio == 0)
                        compressList.add(new WaveBean(waveBean.getYoffset()));
                    current_postion++;
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

    public void startRecord() {
        releaseAll();
        mode = MODE_RECORDING;
        isRecordPause = false;
        duration = 0;
        voiceDrawTask = new VoiceDrawTask();  //波纹画图线程初始化
        timer.schedule(voiceDrawTask, 200);   //延迟200ms执行
    }

    public void pauseRecord() {
        isRecordPause = true;
    }

    public void stopRecord() {
        isRecordPause = true;
        releaseAll();
    }

    public void setWaveHeight(int percent) {
        if (!isRecordPause) {
            if (percent >= 100 || percent <= 0)
                percent = 1;

            waveHeight = maxWaveHeight * percent / 100;
        }
    }
    //启动语音播放波纹，必须在stopRecord后或者达到最长录音时间后调用
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

    public void pausePlay() {
        if (isPlayPause)
            isPlayPause = false;
        else
            isPlayPause = true;
    }

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

    //绘制波形线程
    private class VoiceDrawTask extends TimerTask {

        public VoiceDrawTask() {
            record_flag = true;
        }

        @Override
        public boolean cancel() {
            record_flag = false;
            return true;
        }

        @Override
        public void run() {
            while (record_flag) {
                try {
                    Thread.sleep(50);
                    duration += 50;
                    //录制最大时间
                    if (duration/1000 >= maxDuration || isRecordPause) {
                        //获得压缩后的波形并画出
                        compressLinkedList = getCompressLinkedList();
                        postInvalidate();
                        isRecordPause = true;
                        record_flag = false;
                    } else {
                        System.out.println(waveHeight);
                        WaveBean wave = new WaveBean(waveHeight);
                        //链表长度超过能显示的最大数
                        if (linkedList.size() > maxLines) {
                            allLinkedList.add(linkedList.getFirst());
                            linkedList.removeFirst(); //移除链表头
                        }
                        linkedList.add(wave); //加入表尾
                        postInvalidate();
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
            // TODO Auto-generated method stub\
            play_flag = false;
            return true;
        }

        @Override
        public void run() {
            while(play_flag) {
                try {
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

