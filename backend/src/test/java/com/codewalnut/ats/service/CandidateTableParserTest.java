package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.service.CandidateTableParser.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fake people only — the shapes mirror a real spreadsheet paste. */
class CandidateTableParserTest {

    private static final String PASTE = String.join("\n",
            "slno\tname\temail\tphone",
            "6\tASHA RAO\tasha.rao1999@gmail.com\t9000000001",
            "5\tKAVYA  K S\tKavya KS\t9000000002",
            "15\tT R RAVI\travitr65@gmail.com|\t9000000003",
            "8\tAjay Menon \tajaymenon8@gmail.com \t9000000004",
            "28\tPraveen X\tpraveenx23@gmail.com\t",
            "26\tSam Singh\tsam.singh@outlook.com\t+91 90000 00005",
            "\t\t\t",
            "");

    @Test
    void readsATableWithAHeaderRow() {
        List<Row> rows = CandidateTableParser.parse(PASTE);

        assertThat(rows).hasSize(6);
        assertThat(rows.get(0)).extracting(Row::name, Row::email, Row::phone)
                .containsExactly("ASHA RAO", "asha.rao1999@gmail.com", "9000000001");
        assertThat(rows.get(0).issues()).isEmpty();
        assertThat(rows.get(0).line()).isEqualTo(2);
    }

    @Test
    void aNameInTheEmailColumnIsFlaggedAndLeftBlank() {
        Row row = CandidateTableParser.parse(PASTE).get(1);

        assertThat(row.name()).isEqualTo("KAVYA K S");
        assertThat(row.email()).isNull();
        assertThat(row.issues()).anyMatch(i -> i.contains("Email doesn't look valid"));
    }

    @Test
    void strayCharactersAndSpacesAreCleaned() {
        List<Row> rows = CandidateTableParser.parse(PASTE);

        assertThat(rows.get(2).email()).isEqualTo("ravitr65@gmail.com");
        assertThat(rows.get(3).email()).isEqualTo("ajaymenon8@gmail.com");
        assertThat(rows.get(3).name()).isEqualTo("Ajay Menon");
        assertThat(rows.get(5).phone()).isEqualTo("+919000000005");
    }

    @Test
    void missingPhoneIsFlaggedNotFatal() {
        Row row = CandidateTableParser.parse(PASTE).get(4);

        assertThat(row.hasName()).isTrue();
        assertThat(row.phone()).isNull();
        assertThat(row.issues()).contains("No phone");
    }

    @Test
    void worksWithoutAHeaderRowAndWithCommas() {
        List<Row> rows = CandidateTableParser.parse("1,Neha P,neha.p@gmail.com,9000000006\n2,Om,9000000007,om@x.io");

        assertThat(rows).extracting(Row::name).containsExactly("Neha P", "Om");
        assertThat(rows).extracting(Row::email).containsExactly("neha.p@gmail.com", "om@x.io");
        assertThat(rows).extracting(Row::phone).containsExactly("9000000006", "9000000007");
    }

    @Test
    void emptyPasteGivesNoRows() {
        assertThat(CandidateTableParser.parse("  \n\t\n")).isEmpty();
    }
}
