package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.ApplicationSource;
import com.codewalnut.ats.dto.TrackerDtos.PortalProfile;
import com.codewalnut.ats.naukri.NaukriSheet;
import com.codewalnut.ats.naukri.RuleRanker;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidates imported from Naukri before the details were stored as fields (ADR-0026) get them from
 * their first note, once, at start-up. Idempotent: only rows without details are touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortalProfileBackfill {

    private static final Pattern FIRST = Pattern.compile("^From Naukri.*?(?:· Match (\\d{1,3})%)?(?: — (.+))?$");

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        List<Application> missing = applicationRepository.findBySourceAndPortalProfileIsNull(ApplicationSource.NAUKRI);
        int filled = 0;
        for (Application a : missing) {
            String note = eventRepository.findByApplicationIdOrderByCreatedAtDesc(a.getId()).stream()
                    .filter(e -> e.getType() == ApplicationEventType.CREATED)
                    .map(ApplicationEvent::getNote)
                    .findFirst().orElse(null);
            PortalProfile p = fromNote(note);
            if (p != null) {
                a.setPortalProfile(p.write());
                filled++;
            }
        }
        if (filled > 0) {
            log.info("Filled Naukri details for {} imported candidate(s)", filled);
        }
    }

    /** Reads the note NaukriImportService writes; null when it isn't one. */
    static PortalProfile fromNote(String note) {
        if (note == null || !note.startsWith("From Naukri")) {
            return null;
        }
        String[] lines = note.split("\n");
        Integer match = null;
        String summary = null;
        Matcher m = FIRST.matcher(lines[0]);
        if (m.matches()) {
            match = m.group(1) == null ? null : Integer.valueOf(m.group(1));
            summary = m.group(2);
        }
        String experience = null;
        String role = null;
        String notice = null;
        String location = null;
        String headline = null;
        String keySkills = null;
        for (String line : lines) {
            if (line.startsWith("Headline: ")) {
                headline = line.substring(10).strip();
            } else if (line.startsWith("Key skills: ")) {
                keySkills = line.substring(12).strip();
            } else if (line.contains(" · ") || line.startsWith("Experience: ") || line.startsWith("Now: ")) {
                for (String part : line.split(" · ")) {
                    if (part.startsWith("Experience: ")) {
                        experience = part.substring(12).strip();
                    } else if (part.startsWith("Now: ")) {
                        role = part.substring(5).strip();
                    } else if (part.startsWith("Notice: ")) {
                        notice = part.substring(8).strip();
                    } else if (part.startsWith("Location: ")) {
                        location = part.substring(10).replaceFirst("\\s*\\(prefers.*$", "").strip();
                    }
                }
            }
        }
        List<String> skills = keySkills != null
                ? java.util.Arrays.stream(keySkills.split("[,;|]")).map(String::strip).filter(s -> !s.isEmpty()).limit(20).toList()
                : RuleRanker.skillLabels((headline == null ? "" : headline) + " " + (role == null ? "" : role));
        return new PortalProfile("Naukri", experience, NaukriSheet.years(experience), role == null || role.isBlank() ? null : role,
                skills, headline, notice, location, match, NaukriImportService.aiSummary(summary));
    }
}
