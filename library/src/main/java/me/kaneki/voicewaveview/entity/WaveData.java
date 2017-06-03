package me.kaneki.voicewaveview.entity;

import java.util.ArrayList;

/**
 * @author yueqian
 * @Desctription
 * @date 2017/6/3
 * @email yueqian@mogujie.com
 */
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
