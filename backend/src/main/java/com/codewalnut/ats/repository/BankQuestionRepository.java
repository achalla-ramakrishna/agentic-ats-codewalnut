package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.BankQuestion;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BankQuestionRepository extends JpaRepository<BankQuestion, java.util.UUID> {

    @Query("select q.builtinKey from BankQuestion q where q.builtinKey is not null")
    Set<String> findBuiltinKeys();

    List<BankQuestion> findByAreaAndStatusOrderBySectionAscTopicAscDifficultyAsc(Assessment.Category area,
            BankQuestion.Status status);

    List<BankQuestion> findByAreaAndSectionAndDifficultyAndStatus(Assessment.Category area, BankQuestion.Section section,
            BankQuestion.Difficulty difficulty, BankQuestion.Status status);

    List<BankQuestion> findByAreaAndSectionAndTopicAndDifficultyAndStatus(Assessment.Category area, BankQuestion.Section section,
            String topic, BankQuestion.Difficulty difficulty, BankQuestion.Status status);

    List<BankQuestion> findBySourceAndStatus(BankQuestion.Source source, BankQuestion.Status status);

    @Query("select q.area, q.section, q.difficulty, count(q) from BankQuestion q where q.status = com.codewalnut.ats.domain.BankQuestion.Status.ACTIVE group by q.area, q.section, q.difficulty")
    List<Object[]> countActive();
}
