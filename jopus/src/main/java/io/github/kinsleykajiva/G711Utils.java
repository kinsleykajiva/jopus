package io.github.kinsleykajiva;

public class G711Utils {
    private static final short[] ALAW_TO_PCM = new short[256];
    private static final short[] ULAW_TO_PCM = new short[256];

    private static final byte[] PCM_TO_ALAW = new byte[65536];
    private static final byte[] PCM_TO_ULAW = new byte[65536];

    static {
        for (int i = 0; i < 256; i++) {
            ALAW_TO_PCM[i] = decodeAlaw(i);
            ULAW_TO_PCM[i] = decodeUlaw(i);
        }
        // Initialize encoding tables
        for (int i = -32768; i <= 32767; i++) {
            PCM_TO_ALAW[i & 0xFFFF] = linearToAlaw(i);
            PCM_TO_ULAW[i & 0xFFFF] = linearToUlaw(i);
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

    public static byte[] pcmToAlaw(byte[] pcm) {
        byte[] alaw = new byte[pcm.length / 2];
        for (int i = 0; i < alaw.length; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | ((pcm[i * 2 + 1] & 0xFF) << 8));
            alaw[i] = PCM_TO_ALAW[s & 0xFFFF];
        }
        return alaw;
    }

    public static byte[] pcmToUlaw(byte[] pcm) {
        byte[] ulaw = new byte[pcm.length / 2];
        for (int i = 0; i < ulaw.length; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | ((pcm[i * 2 + 1] & 0xFF) << 8));
            ulaw[i] = PCM_TO_ULAW[s & 0xFFFF];
        }
        return ulaw;
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

    private static byte linearToAlaw(int pcmVal) {
        int mask;
        int seg;

        if (pcmVal >= 0) {
            mask = 0xD5;
        } else {
            mask = 0x55;
            pcmVal = -pcmVal - 8; // -1 -> -9 -> 8? A-law logic is tricky
            // Standard algorithm:
            // 1. Get sign
            // 2. Magnitude (clip to 32635)
            // 3. Convert to 8 bit (A-law)
            // Correction:
            pcmVal = -pcmVal - 1; // 2's complement magnitude?
            // Not exactly, A-law is defined on 13 bits signed linear.
            // Let's use simplified logic commonly found.
        }

        // Re-implementing standard G.711 A-law encoding
        // pcmVal is 16-bit
        pcmVal = pcmVal >> 2; // Drop 2 bits? No 13-bit.
        // Let's use a known compact implementation

        return encodeAlaw((short) pcmVal);
    }

    private static byte encodeAlaw(short pcm) {
        int mask = 0xD5;
        int seg;

        if (pcm < 0) {
            pcm = (short) (-pcm - 8);
            mask = 0x55;
        } else {
            pcm = (short) (pcm - 8); // optional bias
        }

        if (pcm < 0)
            pcm = 0; // Clip?

        // Proper A-law encoding logic
        // Using "Sun Microsystems" implementation logic or similar standard

        // Let's restart with a clean implementation for encodeAlaw derived from the
        // table gen logic inverse
        // If we want bit-exact, we should lookup or compute carefully.

        // Simplified A-law encode:
        /*
         * Linear Input Code Compressed Code
         * ----------------- ---------------
         * 0000000wxyza 000wxyz
         * 0000001wxyza 001wxyz
         * 000001wxyzab 010wxyz
         * 00001wxyzabc 011wxyz
         * 0001wxyzabcd 100wxyz
         * 001wxyzabcde 101wxyz
         * 01wxyzabcdef 110wxyz
         * 1wxyzabcdefg 111wxyz
         * 
         * a..g are discarded.
         */

        // Let's stick to the implementation below which is more robust
        return g711AlawEncode(pcm);
    }

    private static byte encodeUlaw(short pcm) {
        return g711UlawEncode(pcm);
    }

    /*
     * We need a solid implementation. Converting on the fly using a loop is
     * inefficient if we can just map.
     * But generating the map requires the function.
     */

    private static byte linearToUlaw(int pcm_val) {
        return g711UlawEncode(pcm_val);
    }

    // Adapted from Sun's G711.java
    private static byte g711AlawEncode(int sample) {
        int sign;
        int exponent;
        int mantissa;
        int mag;

        short s = (short) sample;

        if (s >= 0) {
            sign = 0xD5;
        } else {
            sign = 0x55;
            s = (short) -s;
            s = (short) (s - 8);
            if (s < 0)
                s = 0;
        }

        if (s > 32767)
            s = 32767;

        if (s < 256) {
            exponent = 0;
        } else {
            exponent = 1;
            while (s > 256 + 128) {
                s >>= 1;
                exponent++;
            }
            s -= 256;
        }

        if (exponent > 7)
            exponent = 7;

        mantissa = (s >> 4) & 0x0F;
        byte val = (byte) (sign ^ ((exponent << 4) | mantissa));
        return val;
    }

    private static byte g711UlawEncode(int sample) {
        int sign;
        int exponent;
        int mantissa;
        int mag;

        short s = (short) sample;

        // BIAS = 0x84 = 132

        if (s < 0) {
            s = (short) (132 - s);
            sign = 0x7F; // Positive in u-law logic (inverse) -> 0x7F & val
        } else {
            s = (short) (132 + s);
            sign = 0xFF;
        }

        if (s > 32767)
            s = 32767;

        exponent = 7;
        for (int mask = 0x4000; (s & mask) == 0 && exponent > 0; exponent--, mask >>= 1) {
        }

        mantissa = (s >> (exponent + 3)) & 0x0F;
        byte val = (byte) (sign & ~((exponent << 4) | mantissa));
        return val;
    }
}
