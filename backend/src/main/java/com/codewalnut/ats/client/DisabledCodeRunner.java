package com.codewalnut.ats.client;

import java.util.List;

/** Used when ATS_CODE_RUNNER_URL isn't set: coding questions can be written, but nothing runs. */
public class DisabledCodeRunner implements CodeRunner {

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public List<Result> run(String language, String source, List<String> inputs, Limits limits) {
        throw new UnavailableException("The code runner isn't set up (ATS_CODE_RUNNER_URL)", null);
    }
}
