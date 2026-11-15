package com.codewalnut.ats.service;

import java.util.UUID;

/** A spoken answer was uploaded and should get its reading check (AINT-24); handled after the upload commits. */
public record AnswerCheckRequested(UUID checkId) {}
