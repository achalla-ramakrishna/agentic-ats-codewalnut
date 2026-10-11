package com.codewalnut.ats.client;

import java.util.List;

/** No AI configured (or the dev profile): portal profiles are ranked by rules only. */
public class DisabledProfileRanker implements ProfileRanker {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String model() {
        return "rules";
    }

    @Override
    public List<Score> rank(Job job, List<Profile> profiles) {
        throw new CalendarException("AI ranking is not set up.");
    }
}
