package com.codewalnut.ats.client;

import java.util.List;

/**
 * Runs untrusted candidate code in a sandbox (ADR-0016). One call runs the same program once per
 * input and returns one result per input, in order. Output is compared by the caller, so the
 * runner never sees the expected answers.
 */
public interface CodeRunner {

    /** Languages candidates can pick, in the order they're offered. */
    List<String> LANGUAGES = List.of("java", "python", "javascript", "cpp");

    /** False when no sandbox is set up; callers then say so instead of running. */
    boolean configured();

    List<Result> run(String language, String source, List<String> inputs, Limits limits);

    record Limits(double cpuSeconds, int memoryMb) {}

    enum Status { OK, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT, MEMORY_LIMIT, INTERNAL_ERROR }

    /** timeSeconds and memoryKb may be null when the sandbox doesn't report them. */
    record Result(Status status, String stdout, String stderr, String compileOutput, Double timeSeconds, Integer memoryKb) {}

    /** The sandbox is unreachable or broken; the caller retries later. */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
