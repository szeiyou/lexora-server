package com.songzy.dicserver.tts;

/**
 * Runtime exception raised when a TTS provider call fails and the caller should
 * persist the provider-specific failure reason instead of degrading to a
 * generic "no audio" outcome.
 */
public class TtsSynthesisException extends RuntimeException {

    public TtsSynthesisException(String message) {
        super(message);
    }

    public TtsSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }
}
