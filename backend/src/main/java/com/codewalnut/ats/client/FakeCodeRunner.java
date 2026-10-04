package com.codewalnut.ats.client;

import java.util.List;

/**
 * A stand-in sandbox for automated tests (ats.coding.runner-url=fake, dev profile only). It never
 * runs anything: a program containing COMPILE_ERROR fails to compile, one containing TIMEOUT runs
 * out of time, and any other program prints its input back unchanged.
 */
public class FakeCodeRunner implements CodeRunner {

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public List<Result> run(String language, String source, List<String> inputs, Limits limits) {
        return inputs.stream().map(input -> {
            if (source.contains("COMPILE_ERROR")) {
                return new Result(Status.COMPILE_ERROR, null, null, "Main.java:1: error: ';' expected", null, null);
            }
            if (source.contains("TIMEOUT")) {
                return new Result(Status.TIME_LIMIT, null, null, null, limits.cpuSeconds(), 1024);
            }
            return new Result(Status.OK, input, null, null, 0.01, 1024);
        }).toList();
    }
}
