package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AssessmentDtos.RunCodeRequest;
import com.codewalnut.ats.dto.AssessmentDtos.RunCodeResult;
import com.codewalnut.ats.dto.CodingRoomDtos.CandidateRoom;
import com.codewalnut.ats.dto.CodingRoomDtos.NotesRequest;
import com.codewalnut.ats.dto.CodingRoomDtos.ProblemOption;
import com.codewalnut.ats.dto.CodingRoomDtos.ProblemRequest;
import com.codewalnut.ats.dto.CodingRoomDtos.RoomPage;
import com.codewalnut.ats.dto.CodingRoomDtos.SaveCodeRequest;
import com.codewalnut.ats.security.CurrentCandidateService;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.CodingRoomService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Live coding rooms (INT-36…): staff endpoints by interview, candidate endpoints by private token. */
@RestController
@RequiredArgsConstructor
public class CodingRoomController {

    private final CodingRoomService roomService;
    private final CurrentUserService currentUserService;
    private final CurrentCandidateService currentCandidateService;

    @GetMapping("/api/v1/coding-problems")
    public List<ProblemOption> problems() {
        return roomService.problems(currentUserService.require());
    }

    @GetMapping("/api/v1/interviews/{id}/coding-room")
    public RoomPage room(@PathVariable UUID id) {
        return roomService.page(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/interviews/{id}/coding-room")
    public RoomPage start(@PathVariable UUID id, @Valid @RequestBody ProblemRequest request) {
        return roomService.start(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/interviews/{id}/coding-room/end")
    public RoomPage end(@PathVariable UUID id) {
        return roomService.end(currentUserService.require(), id);
    }

    @PutMapping("/api/v1/interviews/{id}/coding-room/notes")
    public RoomPage notes(@PathVariable UUID id, @Valid @RequestBody NotesRequest request) {
        return roomService.notes(currentUserService.require(), id, request.notes());
    }

    @PostMapping("/api/v1/interviews/{id}/coding-room/run")
    public RunCodeResult staffRun(@PathVariable UUID id) {
        return roomService.staffRun(currentUserService.require(), id);
    }

    @GetMapping("/api/v1/candidate/coding/{token}")
    public CandidateRoom candidateRoom(@PathVariable String token) {
        return roomService.candidateView(currentCandidateService.require(), token);
    }

    @PutMapping("/api/v1/candidate/coding/{token}")
    public CandidateRoom save(@PathVariable String token, @Valid @RequestBody SaveCodeRequest request) {
        return roomService.save(currentCandidateService.require(), token, request);
    }

    @PostMapping("/api/v1/candidate/coding/{token}/run")
    public CandidateRoom run(@PathVariable String token, @Valid @RequestBody RunCodeRequest request) {
        return roomService.run(currentCandidateService.require(), token, request);
    }
}
