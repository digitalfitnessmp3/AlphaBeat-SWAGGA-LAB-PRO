import com.alphabeat.daw.ui.BeatGrid32;
public final class BeatGrid32Test {
    private static void eq(int expected, int actual) { if(expected != actual) throw new AssertionError(expected+" != "+actual); }
    public static void main(String[] args) {
        eq(0, BeatGrid32.count(0, 0)); eq(1, BeatGrid32.count(0, 140));
        eq(1, BeatGrid32.count(428, 140)); eq(2, BeatGrid32.count(429, 140));
        eq(32, BeatGrid32.count(13714, 140)); eq(1, BeatGrid32.count(13715, 140));
        eq(25, BeatGrid32.count(10286, 140)); eq(1, BeatGrid32.count(0, 140));
        for(int beat=0;beat<3200;beat++) { long ms=(beat*60000L+139)/140; eq(beat%32+1,BeatGrid32.count(ms,140)); }
    }
}
