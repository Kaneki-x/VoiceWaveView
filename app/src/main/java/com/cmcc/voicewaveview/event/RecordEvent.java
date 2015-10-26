package com.cmcc.voicewaveview.event;


/**
 * Created by Lion on 2015/7/8 0008.
 */
public class RecordEvent  {

    private int duration;

    public RecordEvent(int duration) {
        this.duration = duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getDuration() {
        return duration;
    }
}
