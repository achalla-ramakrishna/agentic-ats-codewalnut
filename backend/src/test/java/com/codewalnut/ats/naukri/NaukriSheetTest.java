package com.codewalnut.ats.naukri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-0026: Naukri exports are read by heading, job-related columns only. All data here is made up. */
class NaukriSheetTest {

    @Test
    void readsANaukriExport() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(FakeXlsx.NAUKRI_HEADINGS);
        rows.add(FakeXlsx.row("Asha Test", "Asha.Test@Example.com", "9876543210", "6 Year(s) 5 Month(s)", "Senior Software Engineer",
                "Java, Spring Boot & microservices developer", "Bengaluru", "15 Days or less"));
        rows.add(FakeXlsx.row("Ravi Test", "NA", "NA", "Fresher", "NA", "NA", "Pune", "NA"));
        NaukriSheet.Parsed parsed = NaukriSheet.parse(SpreadsheetReader.read("export.xlsx", FakeXlsx.of(rows)));

        assertThat(parsed.missing()).isEmpty();
        NaukriProfile asha = parsed.profiles().get(0);
        assertThat(asha.name()).isEqualTo("Asha Test");
        assertThat(asha.email()).isEqualTo("asha.test@example.com");
        assertThat(asha.phone()).isEqualTo("9876543210");
        assertThat(asha.experienceYears()).isEqualTo(6.4);
        assertThat(asha.designation()).isEqualTo("Senior Software Engineer");
        assertThat(asha.headline()).contains("Spring Boot & microservices");
        assertThat(asha.noticePeriod()).isEqualTo("15 Days or less");
        assertThat(asha.ugCollege()).isEqualTo("Example Institute of Technology");
        assertThat(asha.text()).doesNotContain("Female", "1995");

        NaukriProfile ravi = parsed.profiles().get(1);
        assertThat(ravi.email()).isNull();
        assertThat(ravi.phone()).isNull();
        assertThat(ravi.experienceYears()).isEqualTo(0.0);
        assertThat(ravi.headline()).isNull();
    }

    @Test
    void csvAndOtherHeadingsWork() {
        String csv = "Candidate Name,Email,Mobile,Experience,Key Skills\n\"Meena, Test\",meena@example.com,+91 98765 43210,4,\"Python, Django\"\n";
        NaukriProfile p = NaukriSheet.parse(SpreadsheetReader.read("list.csv", csv.getBytes())).profiles().get(0);
        assertThat(p.name()).isEqualTo("Meena, Test");
        assertThat(p.phone()).isEqualTo("+919876543210");
        assertThat(p.experienceYears()).isEqualTo(4.0);
        assertThat(p.keySkills()).isEqualTo("Python, Django");
    }

    @Test
    void unreadableFilesSayWhatToDo() {
        assertThatThrownBy(() -> SpreadsheetReader.read("old.xls", new byte[] {1, 2, 3})).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> SpreadsheetReader.read("x.xlsx", "hello".getBytes())).hasMessageContaining("isn't an Excel");
        assertThatThrownBy(() -> NaukriSheet.parse(List.of(List.of("Foo", "Bar"), List.of("1", "2")))).hasMessageContaining("heading row");
        assertThat(SpreadsheetReader.column("AB12")).isEqualTo(27);
    }

    @Test
    void twoEmailsOrPhonesInOneCellUseTheFirstValidOne() {
        assertThat(NaukriSheet.email("First.Test@Example.com, second@example.com")).isEqualTo("first.test@example.com");
        assertThat(NaukriSheet.email("not-an-email; other@example.com")).isEqualTo("other@example.com");
        assertThat(NaukriSheet.email("NA")).isNull();
        assertThat(NaukriSheet.phone("98765 43210 / 91234 56789")).isEqualTo("9876543210");
    }
}
