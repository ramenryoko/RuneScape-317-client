import javax.sound.midi.*;
import java.io.File;

public final class MidiPlayer {
    private static Sequencer sequencer;

    public static void play(String path) {
        try {
            if (sequencer != null && sequencer.isOpen()) {
                sequencer.stop();
                sequencer.close();
            }

            sequencer = MidiSystem.getSequencer();
            if (sequencer == null) {
                System.out.println("[MIDI] No sequencer available");
                return;
            }

            sequencer.open();
            Sequence sequence = MidiSystem.getSequence(new File(path));
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();

            System.out.println("[MIDI] playing " + path);
        } catch (Exception e) {
            System.out.println("[MIDI] failed to play " + path);
            e.printStackTrace();
        }
    }
}