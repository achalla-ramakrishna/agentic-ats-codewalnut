package com.codewalnut.ats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Restart-scoped cutover controls. Rehearsal never admits traffic or resumes copied work. */
@Component
public class OperationsMode {
    private final boolean maintenance;
    private final boolean backgroundWork;

    public OperationsMode(@Value("${ats.operations.maintenance:false}") boolean maintenance,
            @Value("${ats.operations.background-work:true}") boolean backgroundWork,
            @Value("${ats.operations.rehearsal:false}") boolean rehearsal, Environment environment) {
        if (rehearsal && environment.matchesProfiles("dev", "demo")) {
            throw new IllegalStateException("Rehearsal must not enable dev or demo login");
        }
        this.maintenance = maintenance || rehearsal;
        this.backgroundWork = backgroundWork && !this.maintenance;
    }

    public boolean maintenance() { return maintenance; }
    public boolean backgroundWorkEnabled() { return backgroundWork; }
}
