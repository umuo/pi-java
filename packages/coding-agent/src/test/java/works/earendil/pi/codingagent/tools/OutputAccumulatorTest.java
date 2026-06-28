package works.earendil.pi.codingagent.tools;

import org.junit.jupiter.api.Test;
import works.earendil.pi.common.text.Truncation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputAccumulatorTest {
    @Test
    void keepsCompleteUtf8CharactersAcrossChunks() throws Exception {
        OutputAccumulator accumulator = new OutputAccumulator(new OutputAccumulator.Options()
                .maxLines(10)
                .maxBytes(100));
        byte[] bytes = "a界b".getBytes(StandardCharsets.UTF_8);

        accumulator.append(new byte[]{bytes[0], bytes[1]});
        accumulator.append(new byte[]{bytes[2], bytes[3]});
        accumulator.append(new byte[]{bytes[4]});
        accumulator.finish();

        assertThat(accumulator.snapshot().content()).isEqualTo("a界b");
        assertThat(accumulator.snapshot().truncation().truncated()).isFalse();
    }

    @Test
    void snapshotsTailAndPersistsFullOutputWhenTruncated() throws Exception {
        OutputAccumulator accumulator = new OutputAccumulator(new OutputAccumulator.Options()
                .maxLines(2)
                .maxBytes(100)
                .tempFilePrefix("pi-output-test"));

        accumulator.append("one\n".getBytes(StandardCharsets.UTF_8));
        accumulator.append("two\nthree\n".getBytes(StandardCharsets.UTF_8));
        accumulator.finish();

        OutputAccumulator.Snapshot snapshot = accumulator.snapshot(true);

        assertThat(snapshot.content()).isEqualTo("two\nthree");
        assertThat(snapshot.truncation().truncated()).isTrue();
        assertThat(snapshot.truncation().truncatedBy()).isEqualTo(Truncation.Limit.LINES);
        assertThat(snapshot.fullOutputPath()).exists();
        assertThat(Files.readString(snapshot.fullOutputPath())).isEqualTo("one\ntwo\nthree\n");
    }

    @Test
    void tracksLastLineBytesForPartialTailTruncation() throws Exception {
        OutputAccumulator accumulator = new OutputAccumulator(new OutputAccumulator.Options()
                .maxLines(10)
                .maxBytes(4));

        accumulator.append("hello界".getBytes(StandardCharsets.UTF_8));
        accumulator.finish();

        OutputAccumulator.Snapshot snapshot = accumulator.snapshot(true);

        assertThat(snapshot.content()).isEqualTo("o界");
        assertThat(snapshot.truncation().lastLinePartial()).isTrue();
        assertThat(accumulator.getLastLineBytes()).isEqualTo(8);
    }

    @Test
    void rejectsAppendAfterFinish() throws Exception {
        OutputAccumulator accumulator = new OutputAccumulator();

        accumulator.finish();

        assertThatThrownBy(() -> accumulator.append("later".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finished");
    }
}
