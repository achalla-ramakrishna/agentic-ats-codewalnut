package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.dto.ViewAsDtos.CandidateOption;
import com.codewalnut.ats.dto.ViewAsDtos.ClientOption;
import com.codewalnut.ats.dto.ViewAsDtos.Info;
import com.codewalnut.ats.dto.ViewAsDtos.Options;
import com.codewalnut.ats.dto.ViewAsDtos.RoleOption;
import com.codewalnut.ats.dto.ViewAsDtos.StartRequest;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.ClientContactRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.security.ViewAs;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Lets an admin see the app as a candidate, a client contact or a staff role before inviting
 * people (ADR-0013). Read-only (ViewAsReadOnlyFilter), time-limited and audited.
 */
@Service
@RequiredArgsConstructor
public class ViewAsService {

    private final CurrentUserService currentUserService;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final ClientContactRepository contactRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Options options(String q) {
        admin();
        String query = StringUtils.hasText(q) ? q.strip() : null;
        List<CandidateOption> candidates = candidateRepository.searchWithEmail(query, PageRequest.of(0, 25)).stream()
                .map(c -> new CandidateOption(c.getEmail(), c.getName(),
                        applicationRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId()).stream()
                                .map(a -> a.getJob().getTitle()).distinct().limit(3).toList()))
                .toList();
        List<ClientOption> clients = contactRepository.findByActiveTrueOrderByEmailAsc().stream()
                .map(c -> new ClientOption(c.getEmail(), c.getName(), c.getClient().getName()))
                .toList();
        List<RoleOption> roles = Arrays.stream(Role.values())
                .filter(r -> r != Role.ADMIN)
                .map(r -> new RoleOption(r, r.getLabel()))
                .toList();
        return new Options(candidates, clients, roles, ViewAs.DURATION_MINUTES);
    }

    @Transactional(readOnly = true)
    public Info start(HttpSession session, StartRequest request) {
        AppUser admin = admin();
        if (ViewAs.current(session).isPresent()) {
            throw new IllegalArgumentException("You're already viewing as someone. Go back to admin first.");
        }
        ViewAs.State state = switch (request.kind()) {
            case CANDIDATE -> {
                Candidate c = candidateRepository.findByEmail(email(request))
                        .orElseThrow(() -> new NotFoundException("No candidate with that email"));
                yield state(ViewAs.Kind.CANDIDATE, c.getEmail(), null, c.getName(), admin);
            }
            case CLIENT -> {
                ClientContact c = contactRepository.findByEmail(email(request)).filter(ClientContact::isActive)
                        .orElseThrow(() -> new NotFoundException("No active client contact with that email"));
                yield state(ViewAs.Kind.CLIENT, c.getEmail(), null,
                        (StringUtils.hasText(c.getName()) ? c.getName() : c.getEmail()) + " (" + c.getClient().getName() + ")", admin);
            }
            case ROLE -> {
                if (request.role() == null || request.role() == Role.ADMIN) {
                    throw new IllegalArgumentException("role: pick a role other than Admin");
                }
                yield state(ViewAs.Kind.ROLE, null, request.role(), request.role().getLabel(), admin);
            }
        };
        session.setAttribute(ViewAs.ATTRIBUTE, state);
        auditService.record(admin, AuditAction.VIEW_AS_STARTED, "ViewAs", null,
                Map.of("kind", state.kind(), "target", state.email() != null ? state.email() : state.role()));
        return new Info(state.kind(), state.label(), state.expiresAt());
    }

    public void stop(HttpSession session) {
        ViewAs.current(session).ifPresent(state -> {
            session.removeAttribute(ViewAs.ATTRIBUTE);
            AppUser admin = currentUserService.real();
            auditService.record(admin, AuditAction.VIEW_AS_STOPPED, "ViewAs", null,
                    Map.of("kind", state.kind(), "target", state.email() != null ? state.email() : state.role()));
        });
    }

    private static ViewAs.State state(ViewAs.Kind kind, String email, Role role, String label, AppUser admin) {
        return new ViewAs.State(kind, email, role, label, admin.getEmail(),
                Instant.now().plus(Duration.ofMinutes(ViewAs.DURATION_MINUTES)));
    }

    private static String email(StartRequest request) {
        if (!StringUtils.hasText(request.email())) {
            throw new IllegalArgumentException("email: pick who to view as");
        }
        return request.email().strip().toLowerCase(Locale.ROOT);
    }

    /** The real signed-in admin (never a "view as" identity). */
    private AppUser admin() {
        AppUser user = currentUserService.real();
        accessPolicy.require(user, Capability.MANAGE_USERS);
        return user;
    }
}
