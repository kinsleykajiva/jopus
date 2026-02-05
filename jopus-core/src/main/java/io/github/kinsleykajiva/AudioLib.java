package io.github.kinsleykajiva;

import java.io.File;
import java.util.Base64;

/**
 * High-level API for Opus audio processing.
 */
public class AudioLib {

    /**
     * Start a conversion from a Base64 encoded string.
     * 
     * @param base64Input G.711 (A-law/U-law) or PCM data encoded in Base64
     * @return An AudioBuilder to configure the conversion
     */
    public static AudioBuilder convert(String base64Input) {
        return new AudioBuilder(Base64.getDecoder().decode(base64Input));
    }

    /**
     * Start a conversion from a byte array.
     * 
     * @param input G.711 or PCM data
     * @return An AudioBuilder to configure the conversion
     */
    public static AudioBuilder convert(byte[] input) {
        return new AudioBuilder(input);
    }

    /**
     * Start a conversion from a file.
     * 
     * @param file Input audio file
     * @return An AudioBuilder to configure the conversion
     */
    public static AudioBuilder convert(File file) {
        return new AudioBuilder(file);
    }
}
