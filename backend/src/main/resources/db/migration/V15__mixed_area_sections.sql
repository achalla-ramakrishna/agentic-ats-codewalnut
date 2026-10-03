-- Role tests mix areas (e.g. Java + SQL), so a test question's section can be "JAVA:PRACTICAL" (ADR-0015).
ALTER TABLE assessment_question MODIFY section VARCHAR(40);
