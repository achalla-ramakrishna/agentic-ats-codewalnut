-- AI interviews remember the opening they were created for, so sending them can start there.
ALTER TABLE assessment ADD COLUMN job_id BINARY(16) NULL;

-- Existing AI interviews are titled "AI interview — <opening title>[ — Set X]": link each one whose
-- title matches exactly one opening.
UPDATE assessment a
JOIN (
    SELECT a2.id AS assessment_id, MIN(j.id) AS job_id
    FROM assessment a2
    JOIN job_opening j
      ON a2.title = CONCAT('AI interview — ', j.title)
      OR a2.title LIKE CONCAT('AI interview — ', j.title, ' — Set _')
    WHERE a2.category = 'INTERVIEW' AND a2.job_id IS NULL
    GROUP BY a2.id
    HAVING COUNT(DISTINCT j.id) = 1
) m ON m.assessment_id = a.id
SET a.job_id = m.job_id;
