package works.earendil.pi.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TruncationTest {
    @Test
    void truncatesHeadByLines() {
        Truncation.Result result = Truncation.truncateHead("a\nb\nc", new Truncation.Options(2, 100));
        assertThat(result.content()).isEqualTo("a\nb");
        assertThat(result.truncatedBy()).isEqualTo(Truncation.Limit.LINES);
    }

    @Test
    void truncatesHeadByBytesWithoutPartialLines() {
        Truncation.Result result = Truncation.truncateHead("abcd\nef\nzz", new Truncation.Options(10, 7));

        assertThat(result.content()).isEqualTo("abcd\nef");
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncatedBy()).isEqualTo(Truncation.Limit.BYTES);
        assertThat(result.outputLines()).isEqualTo(2);
    }

    @Test
    void reportsFirstLineExceedsHeadLimit() {
        Truncation.Result result = Truncation.truncateHead("abcdef\nnext", new Truncation.Options(10, 3));

        assertThat(result.content()).isEmpty();
        assertThat(result.firstLineExceedsLimit()).isTrue();
        assertThat(result.truncatedBy()).isEqualTo(Truncation.Limit.BYTES);
    }

    @Test
    void truncatesTailAndAllowsPartialLastLine() {
        Truncation.Result result = Truncation.truncateTail("abcdef", new Truncation.Options(10, 3));

        assertThat(result.content()).isEqualTo("def");
        assertThat(result.lastLinePartial()).isTrue();
        assertThat(result.truncatedBy()).isEqualTo(Truncation.Limit.BYTES);
    }

    @Test
    void truncatesLineWithSuffix() {
        Truncation.LineResult result = Truncation.truncateLine("abcdef", 3);

        assertThat(result.text()).isEqualTo("abc... [truncated]");
        assertThat(result.wasTruncated()).isTrue();
    }

    @Test
    void countsCjkWidth() {
        assertThat(EastAsianWidth.visibleWidth("a界")).isEqualTo(3);
    }
}
