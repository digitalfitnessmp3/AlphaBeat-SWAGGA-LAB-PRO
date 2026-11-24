import com.alphabeat.daw.ui.BeatGrid32;
public final class BeatGrid32Test {
    private static void eq(int expected, int actual) { if(expected != actual) throw new AssertionError(expected+" != "+actual); }
    public static void main(String[] args) {
        eq(0, BeatGrid32.count(0, 0)); eq(1, BeatGrid32.count(0, 140));
        eq(1, BeatGrid32.count(428, 140)); eq(2, BeatGrid32.count(429, 140));
        eq(32, BeatGrid32.count(13714, 140)); eq(1, BeatGrid32.count(13715, 140));
        eq(25, BeatGrid32.count(10286, 140)); eq(1, BeatGrid32.count(0, 140));
        eq(0, BeatGrid32.markerState(0, 1));
        eq(1, BeatGrid32.markerState(1, 1));
        eq(0, BeatGrid32.markerState(1, 2));
        eq(1, BeatGrid32.markerState(7, 1));
        eq(2, BeatGrid32.markerState(8, 1));
        eq(2, BeatGrid32.markerState(8, 8));
        eq(0, BeatGrid32.markerState(8, 9));
        eq(1, BeatGrid32.markerState(9, 9));
        eq(2, BeatGrid32.markerState(16, 16));
        eq(2, BeatGrid32.markerState(24, 24));
        eq(2, BeatGrid32.markerState(32, 32));
        eq(0, BeatGrid32.markerState(1, 32));
        for(int count=1;count<=32;count++) {
            int lit=0, neon=0;
            for(int cell=1;cell<=32;cell++) {
                int state=BeatGrid32.markerState(count,cell);
                if(state>0) lit++;
                if(state==2) neon++;
            }
            eq(count,lit); eq((count/8)*8,neon);
        }
        for(int beat=0;beat<3200;beat++) { long ms=(beat*60000L+139)/140; eq(beat%32+1,BeatGrid32.count(ms,140)); }
    }
}
