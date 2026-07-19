package myau.clickgui.util;

public class Animation {
    private long startTime;
    private long delayMS;
    private AnimationType animation;
    public boolean reversed;
    private boolean lastReversed, finished;
    
    public Animation(final long delayMS, AnimationType animation) {
        this.delayMS = delayMS;
        this.animation = animation;
        lastReversed = true;
    }
    
    public double calculateAnimation() {
        if(reversed != lastReversed) {
            reset();
            double a = lastReversed ? 0 : 1;
            lastReversed = reversed;
            return a;
        } else {
            if(getTimeDifference() >= 1) return reversed ? 0 : 1;
        }
        lastReversed = reversed;
        return reversed ? Math.max(0, 1 - animation.get().apply((double) getTimeDifference())) : animation.get().apply((double) getTimeDifference());
    }
    
    public boolean isFinished() {
        if(reversed != lastReversed) return false;
        return System.currentTimeMillis()-startTime >= (delayMS);
    }
    public float getTimeDifference() { return (float)(System.currentTimeMillis()-startTime)/delayMS; }
    public void reset() { startTime = System.currentTimeMillis(); }
    public long getFinishTime() { return startTime; }
    public void setFinishTime(long finishTime) { this.startTime = finishTime; }
    public long getDelayMS() { return delayMS; }
    public void setDelayMS(long delayMS) { this.delayMS = delayMS; }
    public AnimationType getAnimation() { return animation; }
    public void setAnimation(AnimationType animation) { this.animation = animation; }
}
