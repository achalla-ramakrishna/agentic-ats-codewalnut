package com.codewalnut.ats.service;

import com.codewalnut.ats.bank.Roles;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewCategory;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewGuide;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewQuestion;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewRole;
import com.codewalnut.ats.dto.InterviewGuideDtos.ScaleRow;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * CodeWalnut's interview questions (INT-21…), from {@code resources/interview/guide.yml}: by
 * category and level, with what a strong answer covers and the red flags. Staff who interview
 * (VIEW_INTERVIEWS) can read them; candidates and client contacts never can.
 */
@Service
public class InterviewGuideService {

    private static final Set<String> LEVELS = Set.of("F", "J", "S");

    private final AccessPolicy accessPolicy;
    private final InterviewGuide guide;

    public InterviewGuideService(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
        this.guide = load();
    }

    public InterviewGuide guide(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        return guide;
    }

    /** The guide's content, for other features that pick questions from it (e.g. interview kits). */
    public InterviewGuide content() {
        return guide;
    }

    @SuppressWarnings("unchecked")
    static InterviewGuide load() {
        Map<String, Object> m;
        try (InputStream in = InterviewGuideService.class.getResourceAsStream("/interview/guide.yml")) {
            if (in == null) {
                throw new IllegalStateException("interview/guide.yml is missing");
            }
            m = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> howTo = (List<String>) m.get("howTo");
        List<ScaleRow> scale = ((List<Map<String, Object>>) m.get("scale")).stream()
                .map(r -> new ScaleRow(((Number) r.get("score")).intValue(), str(r, "label"), str(r, "evidence"))).toList();
        List<InterviewCategory> categories = new ArrayList<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) m.get("categories")) {
            List<InterviewQuestion> qs = new ArrayList<>();
            for (Map<String, Object> q : (List<Map<String, Object>>) c.get("questions")) {
                String level = str(q, "level");
                if (!LEVELS.contains(level)) {
                    throw new IllegalStateException("interview guide: level must be F, J or S in " + str(c, "id"));
                }
                qs.add(new InterviewQuestion(level, str(q, "topic"), str(q, "question"), str(q, "strong"), str(q, "redFlags"),
                        str(q, "language")));
            }
            categories.add(new InterviewCategory(str(c, "id"), str(c, "name"), str(c, "intro"), List.copyOf(qs)));
        }
        Set<String> ids = categories.stream().map(InterviewCategory::id).collect(Collectors.toSet());
        Map<String, List<String>> byRole = (Map<String, List<String>>) m.get("roles");
        List<InterviewRole> roles = new ArrayList<>();
        for (Roles.Role r : Roles.ROLES) {
            List<String> cats = byRole.getOrDefault(r.id(), List.of());
            for (String id : cats) {
                if (!ids.contains(id)) {
                    throw new IllegalStateException("interview guide: unknown category " + id + " for role " + r.id());
                }
            }
            if (!cats.isEmpty()) {
                roles.add(new InterviewRole(r.id(), r.name(), List.copyOf(cats)));
            }
        }
        return new InterviewGuide(List.copyOf(howTo), scale, List.copyOf(roles), List.copyOf(categories));
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
