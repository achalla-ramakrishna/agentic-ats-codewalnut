package com.codewalnut.ats.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Restart-scoped cutover controls. Rehearsal never admits traffic or resumes copied work. */
@Slf4j
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
        if (!this.maintenance && !this.backgroundWork) {
            log.warn("HTTP is open while background work is disabled. Resume uploads, grading and storage transfers "
                    + "will remain pending until restart with ATS_BACKGROUND_WORK_ENABLED=true.");
        }
    }

    public boolean maintenance() { return maintenance; }
    public boolean backgroundWorkEnabled() { return backgroundWork; }
}
