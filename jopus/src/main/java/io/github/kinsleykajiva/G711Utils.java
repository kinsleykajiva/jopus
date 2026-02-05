package io.github.kinsleykajiva;

public class G711Utils {
    private static final short[] ALAW_TO_PCM = new short[256];
    private static final short[] ULAW_TO_PCM = new short[256];

    static {
        for (int i = 0; i < 256; i++) {
            ALAW_TO_PCM[i] = decodeAlaw(i);
            ULAW_TO_PCM[i] = decodeUlaw(i);
        }
    }

    public static byte[] aLawToPcm(byte[] alaw) {
        byte[] pcm = new byte[alaw.length * 2];
        for (int i = 0; i < alaw.length; i++) {
            short s = ALAW_TO_PCM[alaw[i] & 0xFF];
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return pcm;
    }

    public static byte[] uLawToPcm(byte[] ulaw) {
        byte[] pcm = new byte[ulaw.length * 2];
        for (int i = 0; i < ulaw.length; i++) {
            short s = ULAW_TO_PCM[ulaw[i] & 0xFF];
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return pcm;
    }

    private static short decodeAlaw(int alaw) {
        alaw ^= 0xD5;
        int sign = (alaw & 0x80) != 0 ? -1 : 1;
        int exponent = (alaw & 0x70) >> 4;
        int mantissa = alaw & 0x0F;
        int sample;
        if (exponent == 0) {
            sample = (mantissa << 4) + 8;
        } else {
            sample = ((mantissa << 4) + 136) << (exponent - 1);
        }
        return (short) (sign * sample * 4);
    }

    private static short decodeUlaw(int ulaw) {
        ulaw = ~ulaw;
        int sign = (ulaw & 0x80) != 0 ? -1 : 1;
        int exponent = (ulaw & 0x70) >> 4;
        int mantissa = ulaw & 0x0F;
        int sample = (((mantissa << 3) + 132) << exponent) - 132;
        return (short) (sign * sample * 4); // Scaled
    }
}
